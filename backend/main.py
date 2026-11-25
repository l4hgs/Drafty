"""Drafty. FastAPI backend. Run: uvicorn main:app --reload --host 0.0.0.0 --port 8000"""
from __future__ import annotations

import asyncio
import logging
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from uuid import UUID

from fastapi import Body, FastAPI, File, Form, HTTPException, Query, Request, Response, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from config import settings
from models import (
    AudioUploadResponse,
    ContainerDeleteResponse,
    ContainerUpdateRequest,
    DraftedSyncRequest,
    DraftedSyncResponse,
    ErrorResponse,
    IdeaContainer,
    RegenerateHooksRequest,
    RegenerateHooksResponse,
    UserProfileRequest,
    UserProfileResponse,
)
from services import db_service
from services.ai_service import (
    AIService,
    ContentSafetyError,
    EmptyTranscriptError,
    ParsingError,
    TranscriptionError,
)

logging.basicConfig(level=settings.LOG_LEVEL)
logger = logging.getLogger("drafty.api")

MAX_AUDIO_BYTES = settings.MAX_AUDIO_BYTES
MIN_AUDIO_BYTES = settings.MIN_AUDIO_BYTES
ALLOWED_TYPES = {
    "audio/m4a": "m4a",
    "audio/x-m4a": "m4a",
    "audio/mp4": "mp4",
    "audio/wav": "wav",
    "audio/x-wav": "wav",
    "audio/wave": "wav",
}


def api_error(status: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status, detail={"detail": message, "code": code})


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.ai = AIService()
    if db_service.get_client() is None:
        logger.warning("Supabase not configured; persistence endpoints will return DB_FAILED.")
    yield


app = FastAPI(title="Drafty. API", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---- Flat error format: {"detail": "...", "code": "..."} -------------------
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(_: Request, exc: StarletteHTTPException) -> JSONResponse:
    if isinstance(exc.detail, dict) and "detail" in exc.detail and "code" in exc.detail:
        body = {"detail": str(exc.detail["detail"]), "code": str(exc.detail["code"])}
    else:
        body = {"detail": str(exc.detail), "code": f"HTTP_{exc.status_code}"}
    return JSONResponse(status_code=exc.status_code, content=body, headers=exc.headers)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
    first = exc.errors()[0] if exc.errors() else {}
    loc = ".".join(str(p) for p in first.get("loc", []) if p != "body")
    message = f"{loc}: {first.get('msg', 'Invalid request')}" if loc else "Invalid request"
    return JSONResponse(status_code=422, content={"detail": message, "code": "VALIDATION_ERROR"})


@app.exception_handler(Exception)
async def unhandled_exception_handler(_: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled error")
    return JSONResponse(status_code=500, content={"detail": "Internal server error.", "code": "INTERNAL_ERROR"})


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post(
    "/api/v1/users/profile",
    response_model=UserProfileResponse,
    responses={code: {"model": ErrorResponse} for code in (422, 502)},
)
async def upsert_profile(payload: UserProfileRequest) -> UserProfileResponse:
    try:
        saved = await db_service.upsert_user_profile(payload)
    except db_service.DatabaseError:
        logger.exception("upsert_user_profile failed")
        raise api_error(502, "DB_FAILED", "Could not save user profile.")
    return UserProfileResponse(**saved)



async def _validate_audio_upload(file: UploadFile, user_id: str) -> tuple[bytes, str, str]:
    try:
        UUID(user_id)
    except ValueError:
        raise api_error(422, "INVALID_USER_ID", "user_id must be a UUID.")

    content_type = (file.content_type or "").split(";")[0].strip().lower()
    if content_type not in ALLOWED_TYPES:
        raise api_error(415, "UNSUPPORTED_MEDIA", "Use audio/m4a, audio/mp4 or audio/wav.")

    audio = await file.read(MAX_AUDIO_BYTES + 1)
    if len(audio) > MAX_AUDIO_BYTES:
        raise api_error(413, "TOO_LARGE", "Audio file too large.")
    if len(audio) < MIN_AUDIO_BYTES:
        raise api_error(422, "TOO_SHORT", "Recording too short.")

    return audio, ALLOWED_TYPES[content_type], content_type


@app.post(
    "/api/v1/voice/upload",
    response_model=AudioUploadResponse,
    responses={code: {"model": ErrorResponse} for code in (413, 415, 422, 502)},
)
async def upload_voice_audio(
    file: UploadFile = File(...),
    user_id: str = Form(...),
) -> AudioUploadResponse:
    audio, ext, content_type = await _validate_audio_upload(file, user_id)
    try:
        storage_path = await db_service.upload_audio_to_storage(user_id, audio, ext, content_type, strict=True)
    except db_service.DatabaseError:
        logger.exception("upload_audio_to_storage failed")
        raise api_error(502, "STORAGE_FAILED", "Could not upload audio file to storage.")
    return AudioUploadResponse(storage_path=storage_path or "", bytes_received=len(audio), content_type=content_type)


@app.post(
    "/api/v1/voice/transcribe-and-parse",
    response_model=IdeaContainer,
    responses={code: {"model": ErrorResponse} for code in (413, 415, 422, 502)},
)
async def transcribe_and_parse(
    response: Response,
    file: UploadFile = File(...),
    user_id: str = Form(...),
    client_timestamp: str = Form(...),
    duration_seconds: int | None = Form(default=None),
) -> IdeaContainer:
    started = time.perf_counter()

    audio, ext, content_type = await _validate_audio_upload(file, user_id)
    valid_duration = duration_seconds if (duration_seconds is not None and duration_seconds >= 0) else None
    try:
        client_time = datetime.fromisoformat(client_timestamp.replace("Z", "+00:00"))
    except ValueError:
        client_time = datetime.now(timezone.utc)
        logger.warning("Bad client_timestamp %r; using server time", client_timestamp)

    filename = f"memo.{ext}"
    ai: AIService = app.state.ai

    # 1) Transcribe + upload raw audio + fetch brand voice & existing scheduled dates concurrently
    storage_task = asyncio.create_task(
        db_service.upload_audio_to_storage(user_id, audio, ext, content_type, strict=False)
    )
    brand_voice_task = asyncio.create_task(db_service.get_brand_voice(user_id))
    booked_dates_task = asyncio.create_task(db_service.get_recent_scheduled_dates(user_id))
    try:
        transcript = await ai.transcribe(audio, filename, content_type)
    except EmptyTranscriptError as exc:
        storage_task.cancel()
        brand_voice_task.cancel()
        booked_dates_task.cancel()
        raise api_error(422, "NO_SPEECH", str(exc))
    except TranscriptionError:
        storage_task.cancel()
        brand_voice_task.cancel()
        booked_dates_task.cancel()
        raise api_error(502, "ASR_FAILED", "Transcription service failed.")
    storage_path, brand_voice, booked_dates = await asyncio.gather(
        storage_task, brand_voice_task, booked_dates_task
    )

    # 2) Persist the draft (pending) AND run GPT-4o mini structured parse concurrently
    draft_task = asyncio.create_task(
        db_service.save_voice_draft(
            user_id,
            transcript,
            audio_path=storage_path,
            duration_seconds=valid_duration,
        )
    )
    parse_task = asyncio.create_task(
        ai.parse(transcript, client_time, brand_voice=brand_voice, booked_dates=booked_dates)
    )

    try:
        draft_id = await draft_task
    except db_service.DatabaseError:
        parse_task.cancel()
        logger.exception("save_voice_draft failed")
        raise api_error(502, "DB_FAILED", "Could not save the draft.")

    try:
        parsed = await parse_task
        container = IdeaContainer(**parsed.model_dump(), draft_id=UUID(draft_id))
        await db_service.save_content_container(draft_id, user_id, container)
    except ContentSafetyError as exc:
        try:
            await db_service.mark_draft_failed(draft_id)
        except Exception:
            pass
        raise api_error(422, "CONTENT_BLOCKED", str(exc))
    except ParsingError:
        try:
            await db_service.mark_draft_failed(draft_id)
        except Exception:
            pass
        raise api_error(502, "PARSE_FAILED", "Content parsing failed.")
    except db_service.DatabaseError:
        logger.exception("save_content_container failed")
        try:
            await db_service.mark_draft_failed(draft_id)
        except Exception:
            pass
        raise api_error(502, "DB_FAILED", "Could not save the content container.")
    except Exception:
        try:
            await db_service.mark_draft_failed(draft_id)
        except Exception:
            pass
        raise

    elapsed = time.perf_counter() - started
    response.headers["X-Process-Time-Seconds"] = f"{elapsed:.3f}"
    logger.info("user=%s processed in %.2fs", user_id, elapsed)
    return container


@app.get(
    "/api/v1/containers",
    response_model=list[IdeaContainer],
    responses={code: {"model": ErrorResponse} for code in (422, 502)},
)
async def list_containers(user_id: str = Query(...)) -> list[IdeaContainer]:
    try:
        uid = str(UUID(user_id))
    except ValueError:
        raise api_error(422, "INVALID_USER_ID", "user_id must be a UUID.")
    try:
        return await db_service.list_user_containers(uid)
    except db_service.DatabaseError:
        logger.exception("list_user_containers failed")
        raise api_error(502, "DB_FAILED", "Could not fetch content containers.")


@app.post(
    "/api/v1/containers/{container_id}/regenerate-hooks",
    response_model=RegenerateHooksResponse,
    responses={code: {"model": ErrorResponse} for code in (422, 502)},
)
async def regenerate_hooks(
    container_id: str,
    payload: RegenerateHooksRequest = Body(...),
) -> RegenerateHooksResponse:
    try:
        UUID(container_id)
    except ValueError:
        raise api_error(422, "INVALID_CONTAINER_ID", "container_id must be a UUID.")
    ai: AIService = app.state.ai
    try:
        hooks = await ai.regenerate_hooks(payload)
        return RegenerateHooksResponse(hook_options=hooks)
    except ContentSafetyError as exc:
        raise api_error(422, "CONTENT_BLOCKED", str(exc))
    except ParsingError:
        raise api_error(502, "PARSE_FAILED", "Could not regenerate hooks right now.")


@app.patch(
    "/api/v1/containers/{container_id}",
    response_model=IdeaContainer,
    responses={code: {"model": ErrorResponse} for code in (404, 422, 502)},
)
async def update_container(
    container_id: str,
    payload: ContainerUpdateRequest = Body(...),
) -> IdeaContainer:
    try:
        cid = str(UUID(container_id))
    except ValueError:
        raise api_error(422, "INVALID_CONTAINER_ID", "container_id must be a UUID.")
    try:
        return await db_service.update_content_container(cid, payload)
    except db_service.ContainerNotFoundError:
        raise api_error(404, "CONTAINER_NOT_FOUND", "Content container not found.")
    except db_service.DatabaseError:
        logger.exception("update_content_container failed")
        raise api_error(502, "DB_FAILED", "Could not update content container.")


@app.delete(
    "/api/v1/containers/{container_id}",
    response_model=ContainerDeleteResponse,
    responses={code: {"model": ErrorResponse} for code in (404, 422, 502)},
)
async def delete_container(container_id: str) -> ContainerDeleteResponse:
    try:
        cid = str(UUID(container_id))
    except ValueError:
        raise api_error(422, "INVALID_CONTAINER_ID", "container_id must be a UUID.")
    try:
        result = await db_service.delete_content_container(cid)
    except db_service.ContainerNotFoundError:
        raise api_error(404, "CONTAINER_NOT_FOUND", "Content container not found.")
    except db_service.DatabaseError:
        logger.exception("delete_content_container failed")
        raise api_error(502, "DB_FAILED", "Could not delete content container.")
    return ContainerDeleteResponse(**result)


@app.post(
    "/api/v1/containers/{container_id}/schedule-to-drafted",
    response_model=DraftedSyncResponse,
    responses={code: {"model": ErrorResponse} for code in (404, 422, 502)},
)
async def schedule_to_drafted(
    container_id: str,
    payload: DraftedSyncRequest | None = Body(default=None),
) -> DraftedSyncResponse:
    try:
        container_id = str(UUID(container_id))
    except ValueError:
        raise api_error(422, "INVALID_CONTAINER_ID", "container_id must be a UUID.")
    try:
        result = await db_service.sync_to_drafted_workspace(container_id, payload)
    except db_service.ContainerNotFoundError:
        raise api_error(404, "CONTAINER_NOT_FOUND", "Content container not found.")
    except db_service.DatabaseError:
        logger.exception("sync_to_drafted_workspace failed")
        raise api_error(502, "DB_FAILED", "Could not sync to Drafted.")
    return DraftedSyncResponse(**result)
