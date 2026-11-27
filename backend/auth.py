"""Supabase Auth JWT verification dependency for Drafty FastAPI endpoints."""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Optional

from fastapi import Header, HTTPException

from config import settings
from services import db_service

logger = logging.getLogger("drafty.auth")

# Tiny in-memory TTL cache: token -> (user_id, expires_at_monotonic)
_TOKEN_CACHE: dict[str, tuple[str, float]] = {}
_CACHE_TTL_SECONDS = 300.0


def _verify_token_sync(token: str) -> str:
    """Verify a Supabase Auth access token and return its auth.users UUID string."""
    now = time.monotonic()
    cached = _TOKEN_CACHE.get(token)
    if cached and cached[1] > now:
        return cached[0]

    client = db_service.get_client()
    if client is None:
        raise RuntimeError("Supabase client not configured.")

    res = client.auth.get_user(token)
    user = getattr(res, "user", None)
    user_id = getattr(user, "id", None) if user else None
    if not user_id:
        raise ValueError("Invalid or expired Supabase access token.")

    uid = str(user_id)
    if len(_TOKEN_CACHE) > 256:
        _TOKEN_CACHE.clear()
    _TOKEN_CACHE[token] = (uid, now + _CACHE_TTL_SECONDS)
    return uid


async def get_authenticated_user_id(
    authorization: Optional[str] = Header(default=None),
) -> Optional[str]:
    """
    Extracts and verifies `Authorization: Bearer <supabase_jwt>`.
    - If a Bearer header is provided, it MUST be valid (401 otherwise).
    - If no header is provided and `REQUIRE_AUTH=true`, raises 401.
    - Otherwise returns None so legacy/unauthenticated dev clients remain compatible.
    """
    if not authorization or not authorization.strip():
        if settings.REQUIRE_AUTH:
            raise HTTPException(
                status_code=401,
                detail={"detail": "Sign in required.", "code": "AUTH_REQUIRED"},
            )
        return None

    parts = authorization.strip().split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
        raise HTTPException(
            status_code=401,
            detail={"detail": "Invalid Authorization header.", "code": "INVALID_AUTH_HEADER"},
        )

    token = parts[1].strip()
    try:
        return await asyncio.to_thread(_verify_token_sync, token)
    except Exception as exc:
        logger.warning("Supabase JWT verification failed: %s", exc)
        raise HTTPException(
            status_code=401,
            detail={"detail": "Your session expired. Please sign in again.", "code": "INVALID_TOKEN"},
        )
