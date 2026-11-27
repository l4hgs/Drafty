"""Supabase persistence + Drafted workspace sync."""
from __future__ import annotations

import asyncio
import logging
import time
from datetime import datetime
from functools import lru_cache
from uuid import UUID, uuid4

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

# Lightweight in-memory caches to avoid redundant Supabase round trips on consecutive drafts.
_KNOWN_PROFILE_IDS: set[str] = set()
_BRAND_VOICE_CACHE: dict[str, tuple[str | None, float]] = {}
_BOOKED_DATES_CACHE: dict[str, tuple[list[str], float]] = {}
_READ_CACHE_TTL_SECONDS = 60.0


def _invalidate_user_read_caches(user_id: str | None) -> None:
    if user_id:
        _BRAND_VOICE_CACHE.pop(user_id, None)
        _BOOKED_DATES_CACHE.pop(user_id, None)


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
    _KNOWN_PROFILE_IDS.add(uid)
    _BRAND_VOICE_CACHE.pop(uid, None)
    return {
        "id": uid,
        "email": row.get("email") or email,
        "brand_name": row.get("brand_name") or brand_name,
        "brand_voice_prompt": row.get("brand_voice_prompt") if row.get("brand_voice_prompt") is not None else brand_voice,
    }


def _get_brand_voice(user_id: str) -> str | None:
    now = time.monotonic()
    cached = _BRAND_VOICE_CACHE.get(user_id)
    if cached and cached[1] > now:
        return cached[0]
    try:
        res = _db().table("user_profiles").select("brand_name, brand_voice_prompt").eq("id", user_id).limit(1).execute()
        if not res.data:
            _BRAND_VOICE_CACHE[user_id] = (None, now + _READ_CACHE_TTL_SECONDS)
            return None
        _KNOWN_PROFILE_IDS.add(user_id)
        row = res.data[0]
        parts = [str(p).strip() for p in (row.get("brand_name"), row.get("brand_voice_prompt")) if p and str(p).strip()]
        voice = " — ".join(parts) if parts else None
        if len(_BRAND_VOICE_CACHE) > 256:
            _BRAND_VOICE_CACHE.clear()
        _BRAND_VOICE_CACHE[user_id] = (voice, now + _READ_CACHE_TTL_SECONDS)
        return voice
    except Exception:
        return None


def _save_voice_draft(user_id: str, raw_transcript: str, audio_path: str | None,
                      duration_seconds: int | None) -> str:
    db = _db()
    # Skip redundant user_profiles upsert if we already verified this profile exists in-process.
    if user_id not in _KNOWN_PROFILE_IDS:
        db.table("user_profiles").upsert({"id": user_id}, on_conflict="id", ignore_duplicates=True).execute()
        _KNOWN_PROFILE_IDS.add(user_id)
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


def _finalize_voice_draft(draft_id: str, audio_path: str | None) -> None:
    patch: dict = {"status": "parsed"}
    if audio_path:
        patch["audio_storage_path"] = audio_path
    _db().table("voice_drafts").update(patch).eq("id", draft_id).execute()


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
    _BOOKED_DATES_CACHE.pop(user_id, None)
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
    now = time.monotonic()
    cached = _BOOKED_DATES_CACHE.get(user_id)
    if cached and cached[1] > now:
        return list(cached[0])
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
    result = sorted(dates)
    if len(_BOOKED_DATES_CACHE) > 256:
        _BOOKED_DATES_CACHE.clear()
    _BOOKED_DATES_CACHE[user_id] = (result, now + _READ_CACHE_TTL_SECONDS)
    return list(result)


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


def _update_content_container(
    container_id: str,
    updates: ContainerUpdateRequest,
    owner_user_id: str | None = None,
) -> IdeaContainer:
    table = _db().table("content_containers")
    q = table.select("*").eq("id", container_id)
    if owner_user_id:
        q = q.eq("user_id", owner_user_id)
    found = q.limit(1).execute()
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
        uq = table.update(patch_data).eq("id", container_id)
        if owner_user_id:
            uq = uq.eq("user_id", owner_user_id)
        res = uq.execute()
        if res.data:
            row = dict(res.data[0])
        else:
            row.update(patch_data)
    return _row_to_idea_container(row)


def _delete_content_container(container_id: str, owner_user_id: str | None = None) -> dict:
    table = _db().table("content_containers")
    q = table.select("id").eq("id", container_id)
    if owner_user_id:
        q = q.eq("user_id", owner_user_id)
    found = q.limit(1).execute()
    if not found.data:
        raise ContainerNotFoundError(container_id)
    dq = table.delete().eq("id", container_id)
    if owner_user_id:
        dq = dq.eq("user_id", owner_user_id)
    dq.execute()
    return {"container_id": container_id, "deleted": True}



def _map_drafted_platforms(raw_platforms: list[str] | None) -> list[str]:
    """Map Drafty's freeform platform strings to Drafted's PlatformType enum names."""
    mapped: list[str] = []
    for raw in raw_platforms or []:
        key = str(raw).strip().lower()
        canonical = (
            "tiktok" if ("tiktok" in key or "tik tok" in key)
            else "youtube" if ("youtube" in key or "shorts" in key or key == "yt")
            else "instagram" if ("instagram" in key or "reels" in key or key == "ig")
            else "linkedin" if "linkedin" in key
            else None
        )
        if canonical and canonical not in mapped:
            mapped.append(canonical)
    return mapped or ["tiktok"]


def _map_drafted_format(visual_type: str | None) -> str:
    """Map Drafty's visual_type string to Drafted's FormatType enum name."""
    key = str(visual_type or "").strip().lower()
    if "carousel" in key or "slide" in key:
        return "carousel"
    if "long" in key or "vlog" in key or "youtube" in key or "tutorial" in key:
        return "long_video"
    if "brand" in key or "sponsor" in key or "ad" in key:
        return "brand_deal"
    return "short_form"


def _format_display_time(hhmm: str | None) -> str | None:
    if not hhmm:
        return None
    try:
        return datetime.strptime(hhmm.strip(), "%H:%M").strftime("%I:%M %p").lstrip("0")
    except ValueError:
        return hhmm.strip()


def _deterministic_drafted_post_id(container_id: str) -> str:
    """Drafted's Dart Post.id is an int stored as text; derive a stable positive 12-digit numeric string."""
    return str((UUID(container_id).int % 900_000_000_000) + 100_000_000_000)


def _sync_to_drafted_workspace(
    container_id: str,
    updates: DraftedSyncRequest | None = None,
    target_user_id: str | None = None,
) -> dict:
    db = _db()
    table = db.table("content_containers")
    found = table.select("*").eq("id", container_id).limit(1).execute()
    if not found.data:
        raise ContainerNotFoundError(container_id)

    row = dict(found.data[0])
    effective_user_id = str(target_user_id or row.get("user_id") or "local_device")
    patch_data: dict = {"is_pushed_to_drafted": True}
    if target_user_id and str(row.get("user_id")) != target_user_id:
        # Prevent cross-account hijacking: only adopt containers belonging to an unlinked guest profile
        old_uid = str(row.get("user_id") or "")
        if old_uid:
            old_prof = db.table("user_profiles").select("email").eq("id", old_uid).limit(1).execute()
            if old_prof.data and old_prof.data[0].get("email"):
                raise ContainerNotFoundError(container_id)
        db.table("user_profiles").upsert(
            {"id": target_user_id}, on_conflict="id", ignore_duplicates=True
        ).execute()
        patch_data["user_id"] = target_user_id
    title = row.get("title") or "Untitled Draft"
    assets = dict(row.get("assets") or {})
    metadata = dict(row.get("metadata") or {})
    creative = dict(row.get("creative_direction") or {})
    sched = dict(row.get("schedule") or {})
    selected_hook_index = 0

    if updates is not None:
        selected_hook_index = max(0, updates.selected_hook_index)
        if updates.title is not None:
            title = updates.title
            patch_data["title"] = title
        if updates.assets is not None:
            assets = updates.assets.model_dump(mode="json")
            patch_data["assets"] = assets
        if updates.schedule is not None:
            sched = updates.schedule.model_dump(mode="json")
            patch_data["schedule"] = sched

    date_str, time_str = sched.get("recommended_date"), sched.get("recommended_time")
    scheduled_for = f"{date_str}T{time_str}" if date_str and time_str else None

    # Upsert directly into Drafted's unified `public.posts` table
    drafted_post_id = row.get("drafted_post_id") or _deterministic_drafted_post_id(container_id)
    platforms = _map_drafted_platforms(metadata.get("target_platforms"))
    hooks = assets.get("hook_options") or []
    primary_hook = (
        hooks[min(selected_hook_index, len(hooks) - 1)]
        if hooks
        else (row.get("summary") or "")
    )
    caption_parts = [p for p in (assets.get("primary_caption"), assets.get("cta")) if p and str(p).strip()]
    caption_text = "\n\n".join(str(p).strip() for p in caption_parts)
    hashtags_str = " ".join(str(h).strip() for h in (assets.get("hashtags") or []) if str(h).strip())
    quick_chips = [
        str(c).strip()
        for c in (metadata.get("content_pillar"), metadata.get("tone"))
        if c and str(c).strip()
    ]

    drafted_post_payload = {
        "id": str(drafted_post_id),
        "user_id": effective_user_id,
        "title": title,
        "platform": platforms[0],
        "platforms": platforms,
        "format_type": _map_drafted_format(creative.get("visual_type")),
        "scheduled_at": f"{date_str}T{time_str}:00+00:00" if date_str and time_str else None,
        "is_backlog": False,
        "status": "to_shoot",
        "display_time_label": _format_display_time(time_str),
        "hook_preview": f'Hook: "{primary_hook}"' if primary_hook else None,
        "progress": 0.25,
        "quick_chips": quick_chips,
        "caption_text": caption_text or None,
        "hashtags": hashtags_str or None,
    }
    try:
        db.table("posts").upsert(drafted_post_payload, on_conflict="id").execute()
        patch_data["drafted_post_id"] = str(drafted_post_id)
    except Exception:
        # Graceful fallback if 002_unified_drafted_and_auth.sql hasn't been executed on Supabase yet.
        logger.warning(
            "Could not upsert into Drafted public.posts for container %s; run migration 002_unified_drafted_and_auth.sql",
            container_id,
            exc_info=True,
        )
        drafted_post_id = None

    try:
        table.update(patch_data).eq("id", container_id).execute()
    except Exception:
        # Retry without drafted_post_id if migration 002 hasn't added that column yet.
        patch_data.pop("drafted_post_id", None)
        table.update(patch_data).eq("id", container_id).execute()

    return {
        "container_id": container_id,
        "scheduled_for": scheduled_for,
        "sync_status": "synced",
        "drafted_post_id": drafted_post_id,
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


async def finalize_voice_draft(draft_id: str, audio_path: str | None = None) -> None:
    """Best-effort background update of voice_drafts status='parsed' + audio_storage_path."""
    try:
        await _run(_finalize_voice_draft, draft_id, audio_path)
    except Exception:
        logger.warning("Could not finalize voice_draft %s", draft_id, exc_info=True)


async def save_content_container(draft_id: str, user_id: str, idea_container: IdeaContainer) -> str:
    return await _run(_save_content_container, draft_id, user_id, idea_container)


async def list_user_containers(user_id: str) -> list[IdeaContainer]:
    return await _run(_list_user_containers, user_id)


async def update_content_container(
    container_id: str,
    updates: ContainerUpdateRequest,
    owner_user_id: str | None = None,
) -> IdeaContainer:
    return await _run(_update_content_container, container_id, updates, owner_user_id)


async def delete_content_container(
    container_id: str,
    owner_user_id: str | None = None,
) -> dict:
    return await _run(_delete_content_container, container_id, owner_user_id)


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


async def sync_to_drafted_workspace(
    container_id: str,
    updates: DraftedSyncRequest | None = None,
    target_user_id: str | None = None,
) -> dict:
    return await _run(_sync_to_drafted_workspace, container_id, updates, target_user_id)

