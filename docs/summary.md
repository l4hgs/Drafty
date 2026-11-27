# **Drafty — Functional & Architectural Summary**

**Repository:** `https://github.com/l4hgs/Drafty` (`v2.0`)  
**Companion App:** `https://github.com/l4hgs/Drafted`  
**Live Backend:** `https://drafty-vh1d.onrender.com/`  
**Last Updated:** November 27, 2026

---

## 1. High-Level Overview

- **Drafty** (`{ Drafty }`) is an offline-first, voice-driven mobile AI content assistant ([BRD.md](BRD.md)).
- **Problem Solved:** Eliminates blank-page syndrome and mobile typing friction by letting creators hold (or slide-to-lock) Drafty, speak a rough idea, and receive a structured multi-platform content container (title, summary, mainstream platform badges, 3 scroll-stopping hooks with tap-to-select Primary Hook, primary caption/script, CTA, hashtags, shot-by-shot visual direction, and a conflict-free peak posting slot) ready for one-tap live sync to the **Drafted** calendar app.
- **Identity, Auth & Security:**
  - Unified **Supabase Auth** (Email + Password with HTTPS `/auth/verified` confirmation landing page, plus Google OAuth via `drafty://auth-callback`) shared across Drafty and Drafted.
  - Access/refresh tokens are encrypted at rest via **AndroidKeyStore AES-256-GCM** (`SupabaseAuthManager.kt`), with `android:allowBackup="false"`, HTTPS-only `network_security_config.xml`, debug-only HTTP logging, and full local Room + `.m4a` wipe on Sign Out.
  - FastAPI verifies Supabase Bearer JWTs (`auth.py`), enforces per-user `user_id` ownership checks (IDOR protection), applies a sliding-window AI rate limiter (`15 req / 5 min`), and sets HTTP security headers.
  - Strict Row-Level Security (RLS) across all Drafty and Drafted tables (`002_unified_drafted_and_auth.sql`, `003_harden_rls_auth_only.sql`) requires `auth.uid()::text = user_id`.

### Tech Stack

| Layer | Technologies & Libraries | Key Files |
| :--- | :--- | :--- |
| **Android Client** | Kotlin 2.2.10, Jetpack Compose (Material 3 + Foundation `HorizontalPager`), Navigation Compose, ViewModel + StateFlow, Room (KSP), Retrofit 2 + OkHttp 3, Moshi (KSP), `MediaRecorder` (AAC `.m4a`), `ConnectivityManager`, AndroidKeyStore AES-256-GCM | [libs.versions.toml](../android/gradle/libs.versions.toml), [app/build.gradle.kts](../android/app/build.gradle.kts), [SupabaseAuthManager.kt](../android/app/src/main/java/com/cs312/drafty/core/auth/SupabaseAuthManager.kt) |
| **Backend API** | Python 3.12, FastAPI, Uvicorn, Pydantic v2 (`extra="forbid"`), Supabase JWT verification, rate limiting & security headers middleware | [main.py](../backend/main.py), [auth.py](../backend/auth.py), [models.py](../backend/models.py), [config.py](../backend/config.py) |
| **AI Pipeline** | OpenAI `whisper-1` (ASR) + parallel `omni-moderation-latest` & `gpt-4o-mini` Structured Outputs (content structuring, hook regeneration, deterministic conflict-free scheduling) | [ai_service.py](../backend/services/ai_service.py) |
| **Unified Cloud & Local DB** | Unified Supabase PostgreSQL (`user_profiles`, `voice_drafts`, `content_containers`, plus Drafted's `posts`, `subtasks`, `radar_events`, `checklist_presets`, `milestone_stickers`) + `voice-drafts` Storage bucket; Android Room v2 (`idea_containers`, `pending_voice_notes`) | [001_initial_schema.sql](../backend/migrations/001_initial_schema.sql), [002_unified_drafted_and_auth.sql](../backend/migrations/002_unified_drafted_and_auth.sql), [003_harden_rls_auth_only.sql](../backend/migrations/003_harden_rls_auth_only.sql), [db_service.py](../backend/services/db_service.py) |
| **Companion App** | **Drafted** (Flutter / Dart, `com.example.drafted`) sharing the same Supabase Auth & `public.posts` schema with `AppLifecycleState.resumed` auto-sync | [`l4hgs/Drafted`](https://github.com/l4hgs/Drafted) |
| **Deployment** | Render Web Service (`https://drafty-vh1d.onrender.com/`) via `render.yaml`, `backend/Dockerfile`, Gradle `-PDRAFTY_API_URL` build injection | [render.yaml](../render.yaml), [backend/Dockerfile](../backend/Dockerfile) |

---

## 2. Core Features & Flows

1. **Mandatory 5-Step Onboarding & Unified Auth ([OnboardingScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/OnboardingScreen.kt)):**
   - 5-step `HorizontalPager` showcasing `{ Drafty }` (`IDLE`), Hold-to-Talk & Lock-to-Record (`LISTENING`), Structured Drafts (`THINKING`), Creator Profile setup (`brand_name`, `brand_voice_prompt`), and Unified Account Sign-In / Sign-Up (Email + Password with visibility toggle, `EmailVerificationWaitingModal` with 60s resend cooldown, and Google OAuth).
2. **Voice-First Home Screen ([HomeScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt)):**
   - `{ Drafty }` pixel wordmark, `"hey, <name>"` greeting, contextual quick-resume pill, and ambient tap-to-seed rounded chat bubbles ([PixelSpeechBubble.kt](../android/app/src/main/java/com/cs312/drafty/ui/components/PixelSpeechBubble.kt)).
   - Notched liquid-glass bottom dock cradling the protruding white glassmorphic Drafty hold button (`pointerInput` hold to record, slide left/right to **Lock-to-Record** hands-free, slide up to cancel, release to draft) over a focused pink-to-orange radial ombré, plus responsive two-pane landscape layout.
   - Modal bottom sheets for **Offline Queue**, **Creator Profile** (with Sign Out & local wipe), and **Recent Ideas** (with `IDEAS · SYNCED · DRAFTS` counter bar, live search, status + platform filters, `"Hook: ..."` preview cards, and `SwipeToDismissBox` delete with frosted `GlassToast` **Undo**).
3. **Edge-Lit Liquid-Glass Recording Overlay ([LiquidGlassRecordingOverlay.kt](../android/app/src/main/java/com/cs312/drafty/ui/components/LiquidGlassRecordingOverlay.kt)):**
   - Full-viewport frosted glass with an Apple Intelligence–style screen-edge RGB iridescent wave reacting to live microphone amplitude, background-free `WaveformVisualizer`, directional Lock / Cancel gestures, `6000ms` Render cold-start reassurance, and seamless transition into Whisper → Moderation + GPT-4o mini processing.
4. **Structured Idea Container ([IdeaContainerScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt)):**
   - Swipeable Foundation `HorizontalPager` across **Script** (Slate Blue), **Creatives** (Dusty Rose), and **Schedule** (Forest Sage), with responsive two-pane landscape layout.
   - **Tap-to-Select Primary Hook** (sent as `hook_preview` to Drafted), **One-Tap AI Hook Regeneration** (`POST /api/v1/containers/{id}/regenerate-hooks`), **Copy Script / Copy Shot List**, **Native Android Share Sheet**, unified inline **Edit / Cancel** (including shot-list editing), `DraftedHandoffCard`, and **Schedule to Drafted** (`POST /schedule-to-drafted`) + 1-tap `com.example.drafted` launch.
5. **Smart Conflict-Free Scheduling & Live Drafted Sync ([ai_service.py](../backend/services/ai_service.py), [db_service.py](../backend/services/db_service.py)):**
   - Concurrently fetches existing scheduled dates from Supabase, normalizes dates deterministically (1–14 days ahead, clamping >24h clock drift), and upserts finalized containers directly into Drafted's `public.posts` table with deterministic 12-digit numeric-string IDs.

---

## 3. Backend REST Endpoints ([backend/main.py](../backend/main.py))

| Method & Route | Auth / Limits | Purpose |
| :--- | :--- | :--- |
| `GET /health` | Public | Liveness & cold-start warm-up probe (`{"status": "ok"}`) |
| `GET /auth/verified` | Public (`no-store`) | HTTPS email confirmation landing page redirecting back to Drafty/Drafted |
| `POST /api/v1/users/profile` | Bearer JWT | Authenticated upsert of creator `email`, `brand_name`, and `brand_voice_prompt` |
| `POST /api/v1/voice/upload` | Bearer JWT | Validates and uploads `.m4a`/`.mp4`/`.wav` audio to Supabase Storage |
| `POST /api/v1/voice/transcribe-and-parse` | Bearer JWT · Rate-limited (`15/5m`) | Whisper ASR + parallel Moderation & GPT-4o mini parse + non-blocking archival & container persistence |
| `GET /api/v1/containers` | Bearer JWT | Lists the authenticated user's saved content containers |
| `PATCH /api/v1/containers/{container_id}` | Bearer JWT (Owner) | Updates title, summary, assets, creative direction, or schedule |
| `DELETE /api/v1/containers/{container_id}` | Bearer JWT (Owner) | Deletes a container owned by the authenticated user |
| `POST /api/v1/containers/{container_id}/regenerate-hooks` | Bearer JWT (Owner) · Rate-limited | Generates 3 fresh, non-repeating hooks tailored to brand voice |
| `POST /api/v1/containers/{container_id}/schedule-to-drafted` | Bearer JWT (Owner) | Persists final edits, marks `is_pushed_to_drafted = True`, and upserts directly into Drafted's `public.posts` |
