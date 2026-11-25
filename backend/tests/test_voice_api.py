"""Automated tests for audio file payload reception, storage upload, and AI pipeline."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

from fastapi.testclient import TestClient

# Ensure backend root is on sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import main  # noqa: E402
from models import (  # noqa: E402
    ContentAssets,
    ContentMetadata,
    CreativeDirection,
    IdeaContainer,
    ParsedContent,
    Schedule,
    Shot,
)
from services.ai_service import EmptyTranscriptError, ParsingError  # noqa: E402

SAMPLE_USER_ID = "11111111-2222-3333-4444-555555555555"
SAMPLE_DRAFT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
VALID_AUDIO_BYTES = b"RIFF" + (b"\x00" * 4096)  # 4 KB (> MIN_AUDIO_BYTES = 2048)


def _sample_parsed() -> ParsedContent:
    return ParsedContent(
        title="5-Second Voice Brain-Dump Workflow",
        summary="How creators turn raw voice memos into structured social posts in seconds.",
        metadata=ContentMetadata(
            target_platforms=["TikTok", "Instagram Reels", "LinkedIn"],
            content_pillar="Productivity & AI",
            tone="Approachable and authoritative",
        ),
        assets=ContentAssets(
            hook_options=[
                "Stop typing your content ideas into Notes.",
                "Here is how I turn a 10-second voice memo into 3 posts.",
                "Blank page syndrome ends today.",
            ],
            primary_caption="Speak your raw idea.\nLet Drafty. structure the hooks, script, and schedule.",
            cta="Try Drafty. on your next commute!",
            hashtags=["#CreatorEconomy", "#AITools", "#Productivity"],
        ),
        creative_direction=CreativeDirection(
            visual_type="B-Roll with Text Overlay & ASMR",
            shot_list=[
                Shot(timestamp="0-3s", description="Walking outdoors speaking into phone mic"),
                Shot(timestamp="3-12s", description="Screen recording of Drafty. generating Bento cards"),
                Shot(timestamp="12-18s", description="One-tap schedule to Drafted celebration"),
            ],
            text_overlays=["Talk it out.", "Drafted in <5s"],
        ),
        schedule=Schedule(
            recommended_day="Friday",
            recommended_date="2026-11-13",
            recommended_time="18:00",
            peak_window_reason="High evening engagement for creator workflow content.",
        ),
    )


class StubAIService:
    def __init__(self, transcript: str = "Here is my raw voice memo about creator workflows.") -> None:
        self.transcript = transcript
        self.last_brand_voice: str | None = None

    async def transcribe(self, audio: bytes, filename: str, content_type: str) -> str:
        if len(self.transcript.strip()) < 8:
            raise EmptyTranscriptError("No intelligible speech detected in the recording.")
        return self.transcript

    async def parse(
        self,
        transcript: str,
        client_time,
        brand_voice: str | None = None,
        booked_dates: list[str] | None = None,
    ) -> ParsedContent:
        self.last_brand_voice = brand_voice
        self.last_booked_dates = booked_dates
        return _sample_parsed()


class VoiceApiTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.saved_drafts: list[dict] = []
        self.saved_containers: dict[str, dict] = {}

        async def fake_upload(user_id: str, audio: bytes, ext: str, content_type: str, *, strict: bool = False):
            return f"voice-drafts/{user_id}/test-memo.{ext}"

        async def fake_save_draft(user_id: str, raw_transcript: str, audio_path: str | None = None, duration_seconds=None):
            self.saved_drafts.append({
                "id": SAMPLE_DRAFT_ID,
                "user_id": user_id,
                "audio_path": audio_path,
                "duration_seconds": duration_seconds,
            })
            return SAMPLE_DRAFT_ID

        async def fake_get_brand_voice(user_id: str) -> str | None:
            return self.brand_voice_override

        self.brand_voice_override: str | None = None

        async def fake_save_container(draft_id: str, user_id: str, idea_container):
            cid = str(idea_container.id)
            row = idea_container.model_dump(mode="json")
            row["user_id"] = user_id
            self.saved_containers[cid] = row
            return cid

        async def fake_list_containers(user_id: str):
            rows = [r for r in self.saved_containers.values() if r.get("user_id") == user_id]
            return [IdeaContainer(**{k: v for k, v in r.items() if k != "user_id"}) for r in reversed(rows)]

        async def fake_update_container(container_id: str, updates):
            if container_id not in self.saved_containers:
                raise main.db_service.ContainerNotFoundError(container_id)
            row = dict(self.saved_containers[container_id])
            patch_dict = updates.model_dump(mode="json", exclude_none=True)
            row.update(patch_dict)
            self.saved_containers[container_id] = row
            return IdeaContainer(**{k: v for k, v in row.items() if k != "user_id"})

        async def fake_delete_container(container_id: str):
            if container_id not in self.saved_containers:
                raise main.db_service.ContainerNotFoundError(container_id)
            del self.saved_containers[container_id]
            return {"container_id": container_id, "deleted": True}

        async def fake_sync(container_id: str, updates=None):
            if container_id not in self.saved_containers:
                raise main.db_service.ContainerNotFoundError(container_id)
            sched_for = "2026-11-13T18:00"
            if updates and updates.schedule:
                sched_for = f"{updates.schedule.recommended_date}T{updates.schedule.recommended_time}"
            return {
                "container_id": container_id,
                "scheduled_for": sched_for,
                "sync_status": "synced",
            }

        async def fake_upsert_profile(req):
            uid = str(req.user_id or SAMPLE_USER_ID)
            profile = {
                "id": uid,
                "email": req.email.strip().lower(),
                "brand_name": req.brand_name.strip(),
                "brand_voice_prompt": req.brand_voice_prompt.strip(),
            }
            self.saved_profiles[uid] = profile
            return profile

        self.saved_profiles: dict[str, dict] = {}

        self.patches = [
            patch.object(main.db_service, "get_client", return_value=object()),
            patch.object(main.db_service, "upload_audio_to_storage", fake_upload),
            patch.object(main.db_service, "upsert_user_profile", fake_upsert_profile),
            patch.object(main.db_service, "get_brand_voice", fake_get_brand_voice),
            patch.object(main.db_service, "save_voice_draft", fake_save_draft),
            patch.object(main.db_service, "save_content_container", fake_save_container),
            patch.object(main.db_service, "list_user_containers", fake_list_containers),
            patch.object(main.db_service, "update_content_container", fake_update_container),
            patch.object(main.db_service, "delete_content_container", fake_delete_container),
            patch.object(main.db_service, "sync_to_drafted_workspace", fake_sync),
            patch.object(main, "AIService", StubAIService),
        ]
        for p in self.patches:
            p.start()

        self.client_ctx = TestClient(main.app)
        self.client = self.client_ctx.__enter__()

    def tearDown(self) -> None:
        self.client_ctx.__exit__(None, None, None)
        for p in reversed(self.patches):
            p.stop()

    def test_health(self):
        res = self.client.get("/health")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json(), {"status": "ok"})

    def test_upload_voice_audio_accepts_valid_formats(self):
        for mime, ext in (("audio/m4a", "m4a"), ("audio/wav", "wav"), ("audio/mp4", "mp4")):
            with self.subTest(mime=mime):
                res = self.client.post(
                    "/api/v1/voice/upload",
                    data={"user_id": SAMPLE_USER_ID},
                    files={"file": (f"clip.{ext}", VALID_AUDIO_BYTES, mime)},
                )
                self.assertEqual(res.status_code, 200)
                body = res.json()
                self.assertTrue(body["storage_path"].endswith(f".{ext}"))
                self.assertEqual(body["bytes_received"], len(VALID_AUDIO_BYTES))
                self.assertEqual(body["content_type"], mime)

    def test_transcribe_and_parse_end_to_end(self):
        res = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-08T09:45:00+08:00"},
            files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
        )
        self.assertEqual(res.status_code, 200)
        self.assertIn("X-Process-Time-Seconds", res.headers)
        body = res.json()
        self.assertEqual(body["title"], "5-Second Voice Brain-Dump Workflow")
        self.assertEqual(body["draft_id"], SAMPLE_DRAFT_ID)
        self.assertEqual(len(body["assets"]["hook_options"]), 3)
        self.assertEqual(self.saved_drafts[0]["audio_path"], f"voice-drafts/{SAMPLE_USER_ID}/test-memo.m4a")

        container_id = body["id"]
        sync_res = self.client.post(
            f"/api/v1/containers/{container_id}/schedule-to-drafted",
            json={
                "title": "Edited Title",
                "schedule": {
                    "recommended_day": "Saturday",
                    "recommended_date": "2026-11-14",
                    "recommended_time": "19:30",
                    "peak_window_reason": "Updated slot",
                },
            },
        )
        self.assertEqual(sync_res.status_code, 200)
        self.assertEqual(sync_res.json()["sync_status"], "synced")
        self.assertEqual(sync_res.json()["scheduled_for"], "2026-11-14T19:30")

    def test_rejects_invalid_user_id(self):
        res = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": "not-a-uuid", "client_timestamp": "2026-11-08T09:45:00Z"},
            files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
        )
        self.assertEqual(res.status_code, 422)
        self.assertEqual(res.json()["code"], "INVALID_USER_ID")

    def test_rejects_unsupported_media_type(self):
        res = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-08T09:45:00Z"},
            files={"file": ("memo.ogg", VALID_AUDIO_BYTES, "audio/ogg")},
        )
        self.assertEqual(res.status_code, 415)
        self.assertEqual(res.json()["code"], "UNSUPPORTED_MEDIA")

    def test_rejects_too_short_audio(self):
        res = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-08T09:45:00Z"},
            files={"file": ("memo.m4a", b"tiny", "audio/m4a")},
        )
        self.assertEqual(res.status_code, 422)
        self.assertEqual(res.json()["code"], "TOO_SHORT")

    def test_rejects_oversized_audio(self):
        with patch.object(main, "MAX_AUDIO_BYTES", 4096):
            res = self.client.post(
                "/api/v1/voice/transcribe-and-parse",
                data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-08T09:45:00Z"},
                files={"file": ("memo.m4a", b"\x00" * 5000, "audio/m4a")},
            )
        self.assertEqual(res.status_code, 413)
        self.assertEqual(res.json()["code"], "TOO_LARGE")

    def test_rejects_empty_speech(self):
        main.app.state.ai = StubAIService(transcript="   ")
        res = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-08T09:45:00Z"},
            files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
        )
        self.assertEqual(res.status_code, 422)
        self.assertEqual(res.json()["code"], "NO_SPEECH")

    def test_schedule_not_found_and_invalid_id(self):
        bad = self.client.post("/api/v1/containers/not-a-uuid/schedule-to-drafted")
        self.assertEqual(bad.status_code, 422)
        self.assertEqual(bad.json()["code"], "INVALID_CONTAINER_ID")

        missing = self.client.post(f"/api/v1/containers/{uuid4()}/schedule-to-drafted")
        self.assertEqual(missing.status_code, 404)
        self.assertEqual(missing.json()["code"], "CONTAINER_NOT_FOUND")

    def test_brand_voice_and_duration_seconds_tracking(self):
        self.brand_voice_override = "Alex — Energetic student tech lead"
        res = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={
                "user_id": SAMPLE_USER_ID,
                "client_timestamp": "2026-11-22T23:45:00+08:00",
                "duration_seconds": "14",
            },
            files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
        )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(self.saved_drafts[-1]["duration_seconds"], 14)
        self.assertEqual(main.app.state.ai.last_brand_voice, "Alex — Energetic student tech lead")

    def test_container_list_patch_and_delete(self):
        # Empty list when user has no containers
        empty_res = self.client.get("/api/v1/containers", params={"user_id": SAMPLE_USER_ID})
        self.assertEqual(empty_res.status_code, 200)
        self.assertEqual(empty_res.json(), [])

        # Create a container via transcribe-and-parse
        created = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-22T23:50:00Z"},
            files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
        ).json()
        cid = created["id"]

        # List returns the created container
        list_res = self.client.get("/api/v1/containers", params={"user_id": SAMPLE_USER_ID})
        self.assertEqual(list_res.status_code, 200)
        self.assertEqual(len(list_res.json()), 1)
        self.assertEqual(list_res.json()[0]["id"], cid)

        # Partial update (PATCH)
        patch_res = self.client.patch(
            f"/api/v1/containers/{cid}",
            json={"title": "Updated Working Title", "summary": "Refined summary."},
        )
        self.assertEqual(patch_res.status_code, 200)
        self.assertEqual(patch_res.json()["title"], "Updated Working Title")
        self.assertEqual(patch_res.json()["summary"], "Refined summary.")

        # Extra fields are forbidden (422 VALIDATION_ERROR)
        bad_patch = self.client.patch(f"/api/v1/containers/{cid}", json={"unknown_field": "nope"})
        self.assertEqual(bad_patch.status_code, 422)
        self.assertEqual(bad_patch.json()["code"], "VALIDATION_ERROR")

        # Delete container (200), and subsequent PATCH/DELETE return 404
        del_res = self.client.delete(f"/api/v1/containers/{cid}")
        self.assertEqual(del_res.status_code, 200)
        self.assertEqual(del_res.json(), {"container_id": cid, "deleted": True})

        not_found_del = self.client.delete(f"/api/v1/containers/{cid}")
        self.assertEqual(not_found_del.status_code, 404)
        self.assertEqual(not_found_del.json()["code"], "CONTAINER_NOT_FOUND")

    def test_downstream_failure_marks_draft_failed_without_masking_error(self):
        failed_drafts: list[str] = []

        async def fake_mark_failed(draft_id: str) -> None:
            failed_drafts.append(draft_id)
            raise main.db_service.DatabaseError("secondary db error")

        async def failing_parse(
            transcript: str,
            client_time,
            brand_voice: str | None = None,
            booked_dates: list[str] | None = None,
        ):
            raise ParsingError("LLM parse failed")

        with (
            patch.object(main.db_service, "mark_draft_failed", fake_mark_failed),
            patch.object(main.app.state.ai, "parse", failing_parse),
        ):
            res = self.client.post(
                "/api/v1/voice/transcribe-and-parse",
                data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-22T23:55:00Z"},
                files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
            )
        self.assertEqual(res.status_code, 502)
        self.assertEqual(res.json()["code"], "PARSE_FAILED")
        self.assertEqual(failed_drafts, [SAMPLE_DRAFT_ID])

    def test_upsert_user_profile_success_validation_and_db_error(self):
        # 1) Valid upsert with brand_name and brand_voice_prompt
        res = self.client.post(
            "/api/v1/users/profile",
            json={
                "user_id": SAMPLE_USER_ID,
                "email": "Creator@Drafty.app",
                "brand_name": "Alex (JPCS Lead)",
                "brand_voice_prompt": "Punchy, approachable student tech leader",
            },
        )
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertEqual(body["id"], SAMPLE_USER_ID)
        self.assertEqual(body["email"], "creator@drafty.app")
        self.assertEqual(body["brand_name"], "Alex (JPCS Lead)")
        self.assertEqual(body["brand_voice_prompt"], "Punchy, approachable student tech leader")

        # 2) Validation errors: short email, blank brand_name, or extra forbidden field
        bad_email = self.client.post(
            "/api/v1/users/profile",
            json={"email": "a", "brand_name": "Alex"},
        )
        self.assertEqual(bad_email.status_code, 422)
        self.assertEqual(bad_email.json()["code"], "VALIDATION_ERROR")

        extra_field = self.client.post(
            "/api/v1/users/profile",
            json={"email": "alex@drafty.app", "brand_name": "Alex", "role": "admin"},
        )
        self.assertEqual(extra_field.status_code, 422)
        self.assertEqual(extra_field.json()["code"], "VALIDATION_ERROR")

        # 3) Database error handling (502 DB_FAILED)
        async def failing_upsert(_req):
            raise main.db_service.DatabaseError("supabase unavailable")

        with patch.object(main.db_service, "upsert_user_profile", failing_upsert):
            db_err = self.client.post(
                "/api/v1/users/profile",
                json={"email": "alex@drafty.app", "brand_name": "Alex"},
            )
        self.assertEqual(db_err.status_code, 502)
        self.assertEqual(db_err.json()["code"], "DB_FAILED")

    def test_schedule_to_drafted_with_and_without_edits_and_db_error(self):
        created = self.client.post(
            "/api/v1/voice/transcribe-and-parse",
            data={"user_id": SAMPLE_USER_ID, "client_timestamp": "2026-11-22T23:55:00Z"},
            files={"file": ("memo.m4a", VALID_AUDIO_BYTES, "audio/m4a")},
        ).json()
        cid = created["id"]

        # 1) Schedule without body (no edits)
        no_edit_res = self.client.post(f"/api/v1/containers/{cid}/schedule-to-drafted")
        self.assertEqual(no_edit_res.status_code, 200)
        self.assertEqual(no_edit_res.json()["scheduled_for"], "2026-11-13T18:00")
        self.assertEqual(no_edit_res.json()["sync_status"], "synced")

        # 2) Schedule with DraftedSyncRequest edits
        edit_res = self.client.post(
            f"/api/v1/containers/{cid}/schedule-to-drafted",
            json={
                "title": "Final Scheduled Title",
                "schedule": {
                    "recommended_day": "Sunday",
                    "recommended_date": "2026-11-15",
                    "recommended_time": "20:00",
                    "peak_window_reason": "Sunday evening peak",
                },
            },
        )
        self.assertEqual(edit_res.status_code, 200)
        self.assertEqual(edit_res.json()["scheduled_for"], "2026-11-15T20:00")

        # 3) Database error on schedule-to-drafted returns 502 DB_FAILED
        async def failing_sync(_cid: str, _updates=None):
            raise main.db_service.DatabaseError("sync failed")

        with patch.object(main.db_service, "sync_to_drafted_workspace", failing_sync):
            err_res = self.client.post(f"/api/v1/containers/{cid}/schedule-to-drafted")
        self.assertEqual(err_res.status_code, 502)
        self.assertEqual(err_res.json()["code"], "DB_FAILED")

    def test_container_endpoints_error_not_found_and_invalid_id_paths(self):
        missing_id = str(uuid4())

        # GET /api/v1/containers: invalid user_id (422) & DB failure (502)
        bad_list = self.client.get("/api/v1/containers", params={"user_id": "invalid-uuid"})
        self.assertEqual(bad_list.status_code, 422)
        self.assertEqual(bad_list.json()["code"], "INVALID_USER_ID")

        async def failing_list(_uid: str):
            raise main.db_service.DatabaseError("list failed")

        with patch.object(main.db_service, "list_user_containers", failing_list):
            db_list = self.client.get("/api/v1/containers", params={"user_id": SAMPLE_USER_ID})
        self.assertEqual(db_list.status_code, 502)
        self.assertEqual(db_list.json()["code"], "DB_FAILED")

        # PATCH /api/v1/containers/{id}: invalid UUID (422), not found (404), & DB failure (502)
        bad_patch_id = self.client.patch("/api/v1/containers/not-a-uuid", json={"title": "X"})
        self.assertEqual(bad_patch_id.status_code, 422)
        self.assertEqual(bad_patch_id.json()["code"], "INVALID_CONTAINER_ID")

        missing_patch = self.client.patch(f"/api/v1/containers/{missing_id}", json={"title": "X"})
        self.assertEqual(missing_patch.status_code, 404)
        self.assertEqual(missing_patch.json()["code"], "CONTAINER_NOT_FOUND")

        async def failing_update(_cid: str, _updates):
            raise main.db_service.DatabaseError("update failed")

        with patch.object(main.db_service, "update_content_container", failing_update):
            db_patch = self.client.patch(f"/api/v1/containers/{missing_id}", json={"title": "X"})
        self.assertEqual(db_patch.status_code, 502)
        self.assertEqual(db_patch.json()["code"], "DB_FAILED")

        # DELETE /api/v1/containers/{id}: invalid UUID (422), not found (404), & DB failure (502)
        bad_del_id = self.client.delete("/api/v1/containers/not-a-uuid")
        self.assertEqual(bad_del_id.status_code, 422)
        self.assertEqual(bad_del_id.json()["code"], "INVALID_CONTAINER_ID")

        missing_del = self.client.delete(f"/api/v1/containers/{missing_id}")
        self.assertEqual(missing_del.status_code, 404)
        self.assertEqual(missing_del.json()["code"], "CONTAINER_NOT_FOUND")

        async def failing_delete(_cid: str):
            raise main.db_service.DatabaseError("delete failed")

        with patch.object(main.db_service, "delete_content_container", failing_delete):
            db_del = self.client.delete(f"/api/v1/containers/{missing_id}")
        self.assertEqual(db_del.status_code, 502)
        self.assertEqual(db_del.json()["code"], "DB_FAILED")






if __name__ == "__main__":
    unittest.main()
