"""Supabase persistence + Drafted workspace sync."""
from __future__ import annotations

import asyncio
import logging
from functools import lru_cache
from uuid import uuid4

from supabase import Client, create_client

from config import settings
from models import (
    ContainerUpdateRequest,
    DraftedSyncRequest,
    IdeaContainer,
    UserProfileRequest,
)

logger = logging.getLogger("drafty.db")

AUDIO_BUCKET = settings.SUPABASE_AUDIO_BUCKET


class ContainerNotFoundError(Exception):
    pass


class DatabaseError(Exception):
    pass


@lru_cache(maxsize=1)
def get_client() -> Client | None:
    """Return the Supabase client, or None (with a warning) if not configured."""
    url, key = settings.SUPABASE_URL, settings.SUPABASE_KEY
    missing = [n for n, v in (("SUPABASE_URL", url), ("SUPABASE_KEY", key)) if not v]
    if missing:
        logger.warning("Missing env var(s): %s. Set them in backend/.env.", ", ".join(missing))
        return None
    return create_client(url, key)


def _db() -> Client:
    client = get_client()
    if client is None:
        raise DatabaseError("Supabase is not configured (SUPABASE_URL / SUPABASE_KEY missing).")
    return client


def _upload_audio_to_storage(user_id: str, audio: bytes, ext: str, content_type: str) -> str:
    db = _db()
    path = f"{user_id}/{uuid4()}.{ext}"
    file_opts = {"content-type": content_type, "upsert": "true"}
    try:
        db.storage.from_(AUDIO_BUCKET).upload(path, audio, file_opts)
    except Exception:
        # Bucket may not exist yet; create it once and retry.
        try:
            db.storage.create_bucket(AUDIO_BUCKET, options={"public": False})
        except Exception:
            pass
        db.storage.from_(AUDIO_BUCKET).upload(path, audio, file_opts)
    return f"{AUDIO_BUCKET}/{path}"


def _upsert_user_profile(req: UserProfileRequest) -> dict:
    """Upsert creator metadata strictly scoped to the device's user_id UUID."""
    db = _db()
    uid = str(req.user_id or uuid4())
    email = req.email.strip().lower()
    brand_name = req.brand_name.strip()
    brand_voice = req.brand_voice_prompt.strip()
    payload = {
        "id": uid,
        "email": email,
        "brand_name": brand_name,
        "brand_voice_prompt": brand_voice,
    }
    try:
        res = db.table("user_profiles").upsert(payload, on_conflict="id").execute()
        row = res.data[0] if res.data else payload
    except Exception:
        # Handle duplicate email constraint gracefully without adopting/leaking another user's row.
        fallback_payload = {
            "id": uid,
            "brand_name": brand_name,
            "brand_voice_prompt": brand_voice,
        }
        res = db.table("user_profiles").upsert(fallback_payload, on_conflict="id").execute()
        row = res.data[0] if res.data else fallback_payload
    return {
        "id": uid,
        "email": row.get("email") or email,
        "brand_name": row.get("brand_name") or brand_name,
        "brand_voice_prompt": row.get("brand_voice_prompt") if row.get("brand_voice_prompt") is not None else brand_voice,
    }


def _get_brand_voice(user_id: str) -> str | None:
    try:
        res = _db().table("user_profiles").select("brand_name, brand_voice_prompt").eq("id", user_id).limit(1).execute()
        if not res.data:
            return None
        row = res.data[0]
        parts = [str(p).strip() for p in (row.get("brand_name"), row.get("brand_voice_prompt")) if p and str(p).strip()]
        return " — ".join(parts) if parts else None
    except Exception:
        return None


def _save_voice_draft(user_id: str, raw_transcript: str, audio_path: str | None,
                      duration_seconds: int | None) -> str:
    db = _db()
    # Ensure the profile exists so the FK holds for first-time users.
    db.table("user_profiles").upsert({"id": user_id}, on_conflict="id", ignore_duplicates=True).execute()
    res = db.table("voice_drafts").insert({
        "user_id": user_id,
        "raw_transcript": raw_transcript,
        "audio_storage_path": audio_path,
        "duration_seconds": duration_seconds,
        "status": "pending",
    }).execute()
    return res.data[0]["id"]


def _update_draft_status(draft_id: str, status: str) -> None:
    _db().table("voice_drafts").update({"status": status}).eq("id", draft_id).execute()


def _save_content_container(draft_id: str, user_id: str, idea: IdeaContainer) -> str:
    data = idea.model_dump(mode="json")
    res = _db().table("content_containers").insert({
        "id": data["id"],
        "draft_id": draft_id,
        "user_id": user_id,
        "title": data["title"],
        "summary": data["summary"],
        "metadata": data["metadata"],
        "assets": data["assets"],
        "creative_direction": data["creative_direction"],
        "schedule": data["schedule"],
    }).execute()
    _update_draft_status(draft_id, "parsed")
    return res.data[0]["id"]


def _row_to_idea_container(row: dict) -> IdeaContainer:
    return IdeaContainer(
        id=row["id"],
        draft_id=row.get("draft_id"),
        title=row["title"],
        summary=row.get("summary") or "",
        metadata=row["metadata"],
        assets=row["assets"],
        creative_direction=row["creative_direction"],
        schedule=row["schedule"],
    )


def _get_recent_scheduled_dates(user_id: str) -> list[str]:
    res = (
        _db()
        .table("content_containers")
        .select("schedule")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .limit(15)
        .execute()
    )
    dates: set[str] = set()
    for row in res.data or []:
        sched = row.get("schedule") or {}
        d = sched.get("recommended_date")
        if isinstance(d, str) and d.strip():
            dates.add(d.strip())
    return sorted(dates)


def _list_user_containers(user_id: str) -> list[IdeaContainer]:
    res = (
        _db()
        .table("content_containers")
        .select("*")
        .eq("user_id", user_id)
        .order("created_at", desc=True)
        .execute()
    )
    return [_row_to_idea_container(row) for row in (res.data or [])]


def _update_content_container(container_id: str, updates: ContainerUpdateRequest) -> IdeaContainer:
    table = _db().table("content_containers")
    found = table.select("*").eq("id", container_id).limit(1).execute()
    if not found.data:
        raise ContainerNotFoundError(container_id)
    row = dict(found.data[0])
    patch_data: dict = {}
    if updates.title is not None:
        patch_data["title"] = updates.title
    if updates.summary is not None:
        patch_data["summary"] = updates.summary
    if updates.assets is not None:
        patch_data["assets"] = updates.assets.model_dump(mode="json")
    if updates.creative_direction is not None:
        patch_data["creative_direction"] = updates.creative_direction.model_dump(mode="json")
    if updates.schedule is not None:
        patch_data["schedule"] = updates.schedule.model_dump(mode="json")
    if patch_data:
        res = table.update(patch_data).eq("id", container_id).execute()
        if res.data:
            row = dict(res.data[0])
        else:
            row.update(patch_data)
    return _row_to_idea_container(row)


def _delete_content_container(container_id: str) -> dict:
    table = _db().table("content_containers")
    found = table.select("id").eq("id", container_id).limit(1).execute()
    if not found.data:
        raise ContainerNotFoundError(container_id)
    table.delete().eq("id", container_id).execute()
    return {"container_id": container_id, "deleted": True}



def _sync_to_drafted_workspace(container_id: str, updates: DraftedSyncRequest | None = None) -> dict:
    table = _db().table("content_containers")
    found = table.select("id, schedule").eq("id", container_id).limit(1).execute()
    if not found.data:
        raise ContainerNotFoundError(container_id)
    # NOTE: Drafted shares this Supabase project, so flipping the flag is the push.
    patch_data: dict = {"is_pushed_to_drafted": True}
    sched = found.data[0].get("schedule") or {}
    if updates is not None:
        if updates.title is not None:
            patch_data["title"] = updates.title
        if updates.assets is not None:
            patch_data["assets"] = updates.assets.model_dump(mode="json")
        if updates.schedule is not None:
            sched = updates.schedule.model_dump(mode="json")
            patch_data["schedule"] = sched
    table.update(patch_data).eq("id", container_id).execute()
    date, time_ = sched.get("recommended_date"), sched.get("recommended_time")
    return {
        "container_id": container_id,
        "scheduled_for": f"{date}T{time_}" if date and time_ else None,
        "sync_status": "synced",
    }


async def _run(fn, *args):
    """Supabase client is synchronous; keep the event loop free."""
    try:
        return await asyncio.to_thread(fn, *args)
    except (ContainerNotFoundError, KeyError):
        raise
    except Exception as exc:  # postgrest / httpx errors
        raise DatabaseError(str(exc)) from exc


async def upload_audio_to_storage(
    user_id: str, audio: bytes, ext: str, content_type: str, *, strict: bool = False
) -> str | None:
    """Upload raw audio to the Supabase Storage bucket and return its storage path."""
    try:
        return await _run(_upload_audio_to_storage, user_id, audio, ext, content_type)
    except DatabaseError:
        if strict:
            raise
        logger.warning("Could not upload audio to storage bucket %r; continuing without audio_storage_path", AUDIO_BUCKET)
        return None


async def save_voice_draft(user_id: str, raw_transcript: str, audio_path: str | None = None,
                           duration_seconds: int | None = None) -> str:
    return await _run(_save_voice_draft, user_id, raw_transcript, audio_path, duration_seconds)


async def mark_draft_failed(draft_id: str) -> None:
    """Best-effort status update to 'failed'; never raises so primary errors are not masked."""
    try:
        await _run(_update_draft_status, draft_id, "failed")
    except Exception:
        logger.warning("Could not mark voice_draft %s as failed", draft_id, exc_info=True)


async def save_content_container(draft_id: str, user_id: str, idea_container: IdeaContainer) -> str:
    return await _run(_save_content_container, draft_id, user_id, idea_container)


async def list_user_containers(user_id: str) -> list[IdeaContainer]:
    return await _run(_list_user_containers, user_id)


async def update_content_container(container_id: str, updates: ContainerUpdateRequest) -> IdeaContainer:
    return await _run(_update_content_container, container_id, updates)


async def delete_content_container(container_id: str) -> dict:
    return await _run(_delete_content_container, container_id)


async def upsert_user_profile(req: UserProfileRequest) -> dict:
    return await _run(_upsert_user_profile, req)


async def get_brand_voice(user_id: str) -> str | None:
    try:
        return await _run(_get_brand_voice, user_id)
    except DatabaseError:
        return None


async def get_recent_scheduled_dates(user_id: str) -> list[str]:
    try:
        return await _run(_get_recent_scheduled_dates, user_id)
    except DatabaseError:
        return []


async def sync_to_drafted_workspace(container_id: str, updates: DraftedSyncRequest | None = None) -> dict:
    return await _run(_sync_to_drafted_workspace, container_id, updates)

