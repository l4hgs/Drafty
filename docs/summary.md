# **Drafty — Functional & Architectural Summary**

**Repository:** `https://github.com/l4hgs/Drafty`  
**Last Updated:** November 25, 2026

---

## 1. High-Level Overview

- **Drafty** (`{ Drafty }`) is an offline-first, voice-driven mobile AI content assistant ([BRD.md](BRD.md)).
- **Problem Solved:** Eliminates blank-page syndrome and mobile typing friction by letting creators hold Drafty, speak a rough idea, and receive a structured multi-platform content container (title, summary, mainstream platform badges, 3 scroll-stopping hooks, primary caption/script, CTA, hashtags, shot-by-shot visual direction, and a conflict-free peak posting slot) ready for one-tap sync to **Drafted**.
- **Identity & Access Control:**
  - Device-bound creator identity (`user_id` UUID in Android `SharedPreferences`, synced to Supabase `user_profiles` without email hijacking).
  - Row-Level Security (RLS) enabled on `user_profiles`, `voice_drafts`, and `content_containers`; all reads/writes route through FastAPI using the Supabase service key.

### Tech Stack

| Layer | Technologies & Libraries | Key Files |
| :--- | :--- | :--- |
| **Android Client** | Kotlin 2.2.10, Jetpack Compose (Material 3), Navigation Compose, ViewModel + StateFlow, Room (KSP), Retrofit 2 + OkHttp 3, Moshi (KSP), `MediaRecorder` (AAC `.m4a`), `ConnectivityManager` | [libs.versions.toml](../android/gradle/libs.versions.toml), [app/build.gradle.kts](../android/app/build.gradle.kts) |
| **Backend API** | Python 3.12, FastAPI, Uvicorn, Pydantic v2 (`extra="forbid"`), `python-multipart`, `python-dotenv` | [main.py](../backend/main.py), [models.py](../backend/models.py), [config.py](../backend/config.py) |
| **AI Pipeline** | OpenAI `whisper-1` (ASR) + `gpt-4o-mini` Structured Outputs (content structuring, hook regeneration, conflict-free scheduling) | [ai_service.py](../backend/services/ai_service.py) |
| **Cloud & Local DB** | Supabase PostgreSQL + `voice-drafts` Storage bucket; Android Room v2 (`idea_containers`, `pending_voice_notes`) | [001_initial_schema.sql](../backend/migrations/001_initial_schema.sql), [db_service.py](../backend/services/db_service.py) |
| **Deployment** | Render Web Service Blueprint (`render.yaml`), `backend/Dockerfile`, Gradle `-PDRAFTY_API_URL` build injection | [render.yaml](../render.yaml), [backend/Dockerfile](../backend/Dockerfile) |

---

## 2. Core Features & Flows

1. **First-Launch Onboarding ([OnboardingScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/OnboardingScreen.kt)):**
   - 4-step minimal `HorizontalPager` showcasing `{ Drafty }` (`IDLE`), Hold-to-Talk (`LISTENING`), Structured Drafts (`THINKING`), and Creator Profile setup (`email`, `brand_name`, `brand_voice_prompt`) with offline local fallback.
2. **Voice-First Home Screen ([HomeScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt)):**
   - `{ Drafty }` pixel wordmark, `"hey, <name>"` greeting (defaults to `"hey, Drafter"`), contextual quick-resume pill, and ambient tap-to-seed rounded chat bubbles ([PixelSpeechBubble.kt](../android/app/src/main/java/com/cs312/drafty/ui/components/PixelSpeechBubble.kt)).
   - Notched liquid-glass bottom dock cradling the protruding white glassmorphic Drafty hold button (`pointerInput` hold to record, slide up to cancel, release to draft) over a focused pink-to-orange radial ombré.
   - Modal bottom sheets for **Offline Queue**, **Creator Profile**, and **Recent Ideas** (with live search, `All` / `Synced` / `Drafts` + mainstream platform filters, and deduplicated social media logo badges).
3. **Edge-Lit Liquid-Glass Recording Overlay ([LiquidGlassRecordingOverlay.kt](../android/app/src/main/java/com/cs312/drafty/ui/components/LiquidGlassRecordingOverlay.kt)):**
   - Full-viewport frosted glass with an Apple Intelligence–style screen-edge RGB iridescent wave reacting to live microphone amplitude, background-free `WaveformVisualizer`, spatial slide-to-cancel chevron, and seamless transition into Whisper → GPT-4o mini processing.
4. **Structured Idea Container ([IdeaContainerScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt)):**
   - Tab-reactive ambient color patches and solid muted editorial tiles: **Script** (Slate Blue), **Creatives** (Dusty Rose), and **Schedule** (Forest Sage).
   - **One-Tap AI Hook Regeneration** (`POST /api/v1/containers/{id}/regenerate-hooks`), **Copy Script / Copy Shot List**, **Native Android Share Sheet**, inline editing (`PATCH`), deletion (`DELETE`), single-line aligned Date/Day/Time pickers, and **Schedule to Drafted** (`POST /schedule-to-drafted`).
5. **Smart Conflict-Free Scheduling ([ai_service.py](../backend/services/ai_service.py), [db_service.py](../backend/services/db_service.py)):**
   - Concurrently fetches existing scheduled dates from Supabase and guides GPT-4o mini to pick an open day 1–10 days ahead with a topic- and platform-tailored time and reasoning.

---

## 3. Backend REST Endpoints ([backend/main.py](../backend/main.py))

| Method & Route | Purpose |
| :--- | :--- |
| `GET /health` | Liveness check (`{"status": "ok"}`) for Render health probes |
| `POST /api/v1/users/profile` | Device-bound upsert of creator `email`, `brand_name`, and `brand_voice_prompt` |
| `POST /api/v1/voice/upload` | Validates and uploads `.m4a`/`.mp4`/`.wav` audio to Supabase Storage |
| `POST /api/v1/voice/transcribe-and-parse` | Concurrent storage + brand voice + booked dates + Whisper ASR → concurrent draft save + GPT-4o mini parse → persist container |
| `GET /api/v1/containers` | Lists a user's saved content containers |
| `PATCH /api/v1/containers/{container_id}` | Updates title, summary, assets, creative direction, or schedule |
| `DELETE /api/v1/containers/{container_id}` | Deletes a container from Supabase |
| `POST /api/v1/containers/{container_id}/regenerate-hooks` | Generates 3 fresh, non-repeating hooks tailored to brand voice |
| `POST /api/v1/containers/{container_id}/schedule-to-drafted` | Persists final edits and sets `is_pushed_to_drafted = True` for Drafted workspace sync |
