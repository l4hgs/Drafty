# **Drafty. — Comprehensive Functional & Architectural Summary**

**Repository:** `c:\Users\alodi\OneDrive\Desktop\Drafty`  
**Last Updated:** November 22, 2026

---

## 1. High-Level Overview

### Purpose & Problem Solved
- **Drafty.** is an offline-first, voice-driven mobile AI content assistant ([BRD.md](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/BRD.md#L5-L24)).
- **Problem Solved:** Eliminates "blank page syndrome" and the friction of typing long-form content ideas on a mobile device while commuting or walking. Users record unscripted, stream-of-consciousness voice memos on an Android device, and the cloud backend transcribes and parses them in under **5.0 seconds** into structured, multi-platform content kits (working title, core summary, platform tags, 3 scroll-stopping hooks, primary caption/script, CTA, hashtags, shot-by-shot visual direction, and recommended posting schedule) ready for one-tap synchronization with the **Drafted** workspace database.

### Target Users / Roles
- **Primary Users ([BRD.md](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/BRD.md#L31-L36)):** Digital content creators, student organization leaders (e.g., Junior Philippine Computer Society officers), and mobile professionals.
- **Evaluators / Stakeholders:** College of Informatics and Computing Sciences faculty evaluating mobile computing, network handover resilience, and systems architecture.
- **Permissions & Roles:**
  - **Application Role Model:** Single creator role identified by a UUID (`user_id`), persisted locally in Android `SharedPreferences` ([DraftyViewModel.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L72-L81)) and synced to the `user_profiles` table in Supabase ([db_service.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L50-L92)).
  - **Android OS Permissions ([AndroidManifest.xml](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/AndroidManifest.xml#L5-L11)):** `RECORD_AUDIO` (requested at runtime in [HomeScreen.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt#L68-L78)), `INTERNET`, and `ACCESS_NETWORK_STATE`.
  - **Database Access Control ([001_initial_schema.sql](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/migrations/001_initial_schema.sql#L58-L62)):** Row-Level Security (RLS) is enabled on all three PostgreSQL tables (`user_profiles`, `voice_drafts`, `content_containers`) with no public/anon policies; all database reads and writes go through the FastAPI backend using the Supabase service key.

### Tech Stack

| Layer | Technologies, Frameworks & Libraries | Configuration Files |
| :--- | :--- | :--- |
| **Android Mobile Client** | **Kotlin 2.2.10**, **Jetpack Compose** (Compose BOM `2026.02.01`, Material 3), **Navigation Compose** (`2.9.0`), **Lifecycle ViewModel Compose** (`2.9.0`), **Coroutines** (`1.9.0`), **Room Database** (`2.7.2` with KSP `2.3.6`), **Retrofit 2** (`2.11.0`), **Moshi** (`1.15.1` with KSP codegen & reflection fallback), **OkHttp 3** (`4.12.0` + Logging Interceptor), **Coil Compose** (`2.6.0`), Android **`MediaRecorder`**, **`ConnectivityManager`** | [libs.versions.toml](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/gradle/libs.versions.toml), [app/build.gradle.kts](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/build.gradle.kts) (AGP `9.3.3`, `minSdk = 24`, `targetSdk = 37`, Java 11) |
| **Backend API** | **Python 3.11+**, **FastAPI** (`>=0.115,<1.0`), **Uvicorn** (`>=0.30`), **Pydantic v2** (`>=2.8,<3.0` with `ConfigDict(extra="forbid")`), `python-multipart` (`>=0.0.9`), `python-dotenv` (`>=1.0`) | [requirements.txt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/requirements.txt), [main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py), [models.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/models.py) |
| **AI / Cloud Services** | **OpenAI Python SDK** (`>=1.50`) using `AsyncOpenAI`: **Whisper ASR** (`whisper-1`) for speech-to-text and **GPT-4o mini** (`gpt-4o-mini`) with **Structured Outputs** (`client.beta.chat.completions.parse`) | [ai_service.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py) |
| **Database & Storage** | **Supabase Python SDK** (`>=2.7`) backed by **PostgreSQL** (`pgcrypto`, JSONB columns, triggers) and **Supabase Storage** (`voice-drafts` private bucket); **SQLite / Room** on Android | [001_initial_schema.sql](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/migrations/001_initial_schema.sql), [db_service.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py), [DraftyDatabase.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/DraftyDatabase.kt) |
| **Testing & Benchmarks** | Python `unittest`, FastAPI/Starlette `TestClient`, `httpx` (`>=0.27`), `pytest` (`>=8.0`), JUnit 4 & Espresso dependencies on Android | [test_voice_api.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/test_voice_api.py), [benchmark_week3.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/benchmark_week3.py) |

---

## 2. Core Features & User Flows

### 2.1 Creator Profile Setup & Sign-In
- **What it does:** Collects or updates a creator's `email`, `brand_name`, and `brand_voice_prompt`, persists them locally in `SharedPreferences`, and upserts the record into Supabase `user_profiles` (matching by `email` or `id`).
- **How it is triggered:**
  - **User trigger:** Tapping the top-left avatar, the `"Hey there 👋 (Tap to Sign In)"` header, or the sky-blue `SignInBannerCard` on [HomeScreen.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt#L87-L91) opens `CreatorProfileSheet`.
  - **System action:** Tapping **`[ ✓ Save & Sign In ]`** calls [`DraftyViewModel.saveUserProfile()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L248-L289) $\rightarrow$ `POST /api/v1/users/profile` $\rightarrow$ [`db_service.upsert_user_profile()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L66-L105), and immediately triggers [`offlineQueue.flushQueue()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L276).

### 2.2 One-Tap Voice Capture & Live Waveform Visualization (`FR-01`)
- **What it does:** Records mono AAC audio in an MPEG-4 container (`.m4a`, 44.1 kHz sampling rate, 128 kbps bit rate) to `filesDir/voice_queue/memo_<timestamp>.m4a` while rendering an animated listening mascot (`MascotState.LISTENING` with retro headphones), an elapsed-seconds timer, and a 28-bar scrolling sound-level visualizer.
- **How it is triggered:**
  - **User trigger:** Tapping **`[ 🎙️ Tap to Brain-Dump ]`** on [HomeScreen.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt#L72-L78) verifies `RECORD_AUDIO` permission and calls [`DraftyViewModel.startRecording()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L123-L157).
  - **System action:** [`AudioRecorder.start()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/audio/AudioRecorder.kt#L16-L42) initializes `MediaRecorder`. Two coroutines poll `recorder.getMaxAmplitude()` every 80 ms and elapsed seconds every 250 ms. [RecordingSheet.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/RecordingSheet.kt#L45-L64) and [WaveformVisualizer.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/components/WaveformVisualizer.kt#L20-L50) collect `StateFlow` instances internally to avoid full-screen recompositions.
  - **Validation:** Recordings shorter than 1 second or $\le 1024$ bytes on Android ([AudioRecorder.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/audio/AudioRecorder.kt#L65-L71), [DraftyViewModel.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L180-L184)) or $< 2048$ bytes / $> 25\text{ MB}$ on the backend ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L120-L137)) are rejected.

### 2.3 Cloud AI Transcription, Structured Parsing & Persistence (`FR-03`, `FR-04`)
- **What it does:** Uploads the `.m4a` file via multipart HTTP POST, stores the raw audio in Supabase Storage, transcribes speech via OpenAI Whisper, saves a `voice_drafts` record, parses the transcript into a schema-valid `IdeaContainer` via GPT-4o mini, persists the container in Supabase `content_containers`, and caches it locally in Room.
- **How it is triggered:**
  - **User trigger:** Tapping **`[ ■ Stop & Draft ]`** in [RecordingSheet.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/RecordingSheet.kt#L117-L121) invokes [`DraftyViewModel.stopRecordingAndProcess()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L174-L240).
  - **Backend pipeline ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L156-L221)):**
    1. Concurrently launches `db_service.upload_audio_to_storage()` (to private bucket `voice-drafts/{user_id}/{uuid}.{ext}`) and `ai.transcribe()` (OpenAI `whisper-1`).
    2. Rejects transcripts under 8 characters with `422 NO_SPEECH` ([ai_service.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py#L61-L67)).
    3. Concurrently launches `db_service.save_voice_draft()` (status `pending`) and `ai.parse()` (`gpt-4o-mini` with `response_format=ParsedContent`).
    4. Persists the container via `db_service.save_content_container()`, updates the draft status to `parsed` (or `failed` on error), sets the `X-Process-Time-Seconds` telemetry header, and returns the `IdeaContainer` JSON.
  - **Client completion:** The ViewModel upserts the returned `IdeaContainer` into Room (`dao.upsertContainer(result.toEntity())`) and emits `RecordingState.Success(result.id)`, which navigates to `IdeaContainerScreen`.

### 2.4 Offline Audio Queue & Network Handover Auto-Sync (`FR-02`)
- **What it does:** Prevents data loss when the device is offline or switches between Wi-Fi and 4G/5G mid-upload by queuing the `.m4a` file path and metadata in Room (`pending_voice_notes`) and automatically flushing the queue in FIFO order when connectivity returns.
- **How it is triggered:**
  - **System trigger (Failure capture):** If `api.transcribeAndParse()` throws an `IOException` in [`DraftyViewModel.stopRecordingAndProcess()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L211-L223), the local `.m4a` file is retained and saved via [`OfflineQueueManager.enqueue()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt#L72-L88).
  - **System trigger (Auto-recovery):** [`OfflineQueueManager`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt#L45-L61) registers a `ConnectivityManager.NetworkCallback`. Whenever `onAvailable` or `onCapabilitiesChanged` (with `NET_CAPABILITY_INTERNET`) fires, or on app startup, it calls `flushQueue()` (guarded by a `Mutex`).
  - **User trigger (Manual sync/discard):** On [HomeScreen.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt#L323-L379), `OfflineQueueCard` displays queued items with **`[ ↻ Sync Now ]`** (`vm::retryOfflineQueue`) and **`[ ✕ ]`** (`vm::discardPendingNote`). Permanent `4xx` responses (e.g., a silent `422` clip) are automatically deleted so they do not loop indefinitely ([OfflineQueueManager.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt#L136-L141)).

### 2.5 Interactive Multi-Tab Idea Container, Inline Editing & Drafted Sync (`FR-05`, `FR-06`)
- **What it does:** Renders the parsed content container across three pastel Bento tabs (`Script`, `Creatives`, `Schedule`), supports inline editing of Title, Caption, CTA, Date, and Peak Time, and synchronizes the finalized container to the **Drafted** workspace in Supabase.
- **How it is triggered:**
  - **Navigation trigger:** Automatically after a recording is parsed, or by tapping any card in the `"Recent ideas"` feed on `HomeScreen`.
  - **Tab switching ([IdeaContainerScreen.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt#L140-L162)):**
    - **Tab 1 (`Script` on `DraftyLavender`):** Displays 3 numbered hooks, primary caption/script, CTA, and hashtags.
    - **Tab 2 (`Creatives` on `DraftySage`):** Displays visual style chip, numbered shot list with timestamps, and text overlays.
    - **Tab 3 (`Schedule` on `DraftyCoral`):** Displays Date, Day, Peak Time, and peak window reasoning. Tapping **`DATE ✏️`** or **`PEAK TIME ✏️`** opens `DatePickerDialog` or `TimePickerDialog`, recalculates `recommendedDay`, and saves to Room via `vm.updateContainer()`.
  - **Inline Edit trigger:** Tapping **`[ ✏️ Edit ]`** in the bottom floating dock switches Title, Caption, and CTA into `OutlinedTextField` inputs; tapping **`[ ✓ Save ]`** persists changes to Room ([IdeaContainerScreen.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt#L151-L185)).
  - **Drafted Sync trigger:** Tapping **`[ 🚀 Schedule to Drafted ]`** calls [`DraftyViewModel.scheduleToDrafted(id)`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L298-L322), sending `POST /api/v1/containers/{container_id}/schedule-to-drafted` with a `DraftedSyncRequest` body (`title`, `assets`, `schedule`). The backend updates the Supabase `content_containers` row with any user edits and sets `is_pushed_to_drafted = True` ([db_service.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L140-L164)). On success, Android marks the local Room row as pushed and renders `CelebrationOverlay` (`MascotState.CELEBRATING` + 56-particle canvas confetti).

### 2.6 Partial, Placeholder, or Unused Elements Spotted in Codebase
1. **`brand_voice_prompt` is stored but not yet injected into the GPT-4o mini prompt:**
   - [`db_service.get_brand_voice(user_id)`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L108-L117) and [`db_service._get_brand_voice(user_id)`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L108-L117) are implemented in `db_service.py`, and users can save `brand_voice_prompt` via `POST /api/v1/users/profile`. However, [`main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L194) and [`AIService.parse()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py#L70-L89) do not currently call `get_brand_voice(user_id)` or append the user's custom brand voice to `SYSTEM_PROMPT`.
2. **Local `IdeaContainerEntity.rawTranscript` & `voice_drafts.duration_seconds`:**
   - In Room, [`IdeaContainerEntity.rawTranscript`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/IdeaContainerEntity.kt#L11) defaults to `""` ([Converters.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/Converters.kt#L32)) because `IdeaContainer` returned by `/api/v1/voice/transcribe-and-parse` does not include `raw_transcript` (the transcript is stored in Supabase `voice_drafts.raw_transcript`). Similarly, `voice_drafts.duration_seconds` is always `None` because the client does not send clip duration in the multipart form.
3. **Simulated Processing Step Transition on Android:**
   - Because `/api/v1/voice/transcribe-and-parse` performs Whisper ASR and GPT-4o mini parsing in a single HTTP request, [`DraftyViewModel.stopRecordingAndProcess()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L189-L195) switches the UI indicator from `ProcessingStep.TRANSCRIBING` to `ProcessingStep.STRUCTURING` using a 1.5-second timer (`delay(1_500)`) rather than server-sent events.
4. **Standalone `/api/v1/voice/upload` Endpoint & Unused Dependency:**
   - [`POST /api/v1/voice/upload`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L140-L153) is implemented and covered by backend tests, but the Android app uploads directly to `/api/v1/voice/transcribe-and-parse` (which performs the storage upload concurrently).
   - `io.coil-kt:coil-compose:2.6.0` is declared in [app/build.gradle.kts](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/build.gradle.kts#L67) but is not used in any screen because the mascot and UI graphics are drawn procedurally on Compose `Canvas`.

---

## 3. Entry Points & Structure

### Entry Points
- **Android Application Entry Point:**
  - [`MainActivity.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/MainActivity.kt#L24-L59) (registered as `MAIN` / `LAUNCHER` in [AndroidManifest.xml](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/AndroidManifest.xml#L18-L29)).
  - Hosts `DraftyNavHost` with two Compose navigation routes:
    - `"home"` $\rightarrow$ [`HomeScreen`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt#L63) (plus modal [`RecordingSheet`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/RecordingSheet.kt#L45) and `CreatorProfileSheet`)
    - `"ContainerDetail/{id}"` $\rightarrow$ [`IdeaContainerScreen`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt#L98)
- **FastAPI Backend Entry Point & Routes ([`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py)):**
  - CLI command: `uvicorn main:app --reload --host 0.0.0.0 --port 8000`
  - `GET /health` ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L101-L103)) — Returns `{"status": "ok"}`.
  - `POST /api/v1/users/profile` ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L106-L117)) — Upserts a creator profile into `user_profiles`.
  - `POST /api/v1/voice/upload` ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L140-L153)) — Validates and uploads `.m4a`/`.mp4`/`.wav` audio to the `voice-drafts` Supabase Storage bucket.
  - `POST /api/v1/voice/transcribe-and-parse` ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L156-L221)) — Multipart endpoint (`file`, `user_id`, `client_timestamp`) executing storage upload + Whisper ASR + draft save + GPT-4o mini structured parse + container save.
  - `POST /api/v1/containers/{container_id}/schedule-to-drafted` ([main.py](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L224-L243)) — Persists optional `DraftedSyncRequest` edits and sets `is_pushed_to_drafted = True`.
- **Background Workers / Listeners:**
  - [`OfflineQueueManager.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt#L33-L173) — Registers a `ConnectivityManager.NetworkCallback` on ViewModel initialization to monitor active transport (`Wi-Fi`, `Cellular`, `Offline`) and flush queued voice recordings on a background coroutine (`Dispatchers.IO`).

### Folder / Module Map

| Path | Responsibility |
| :--- | :--- |
| [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py) | FastAPI app initialization, lifespan hooks, CORS, flat JSON exception handlers (`StarletteHTTPException`, `RequestValidationError`, `Exception`), and REST endpoints. |
| [`backend/models.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/models.py) | Strict Pydantic v2 schemas (`ContentMetadata`, `ContentAssets`, `Shot`, `CreativeDirection`, `Schedule`, `ParsedContent`, `IdeaContainer`, `DraftedSyncRequest`, `DraftedSyncResponse`, `AudioUploadResponse`, `UserProfileRequest`, `UserProfileResponse`, `ErrorResponse`). |
| [`backend/services/ai_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py) | `AIService` wrapping `AsyncOpenAI` for `whisper-1` audio transcription and `gpt-4o-mini` Structured Outputs parsing. |
| [`backend/services/db_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py) | Supabase client singleton (`get_client`), `asyncio.to_thread` wrappers (`_run`), storage bucket uploader, and CRUD functions for `user_profiles`, `voice_drafts`, and `content_containers`. |
| [`backend/migrations/001_initial_schema.sql`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/migrations/001_initial_schema.sql) | PostgreSQL schema migration creating tables, indexes, `set_updated_at()` trigger, RLS locks, and the `voice-drafts` storage bucket. |
| [`backend/tests/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/) | [`test_voice_api.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/test_voice_api.py) (9 functional API & validation tests) and [`benchmark_week3.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/benchmark_week3.py) (20-trial E2E latency & 10-trial network handover evaluation suite). |
| [`android/.../core/audio/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/audio/AudioRecorder.kt) | `AudioRecorder.kt` wrapping Android `MediaRecorder` for `.m4a` AAC capture and live peak amplitude polling. |
| [`android/.../core/network/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt) | `OfflineQueueManager.kt` managing `ConnectivityManager` callbacks and FIFO retry of queued voice notes. |
| [`android/.../data/local/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/) | Room database (`DraftyDatabase.kt`), DAO (`DraftyDao.kt`), entities (`IdeaContainerEntity.kt`, `PendingVoiceNoteEntity.kt`), and Moshi JSON converters (`Converters.kt`). |
| [`android/.../data/remote/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/remote/) | Retrofit interface (`DraftyApiService.kt`), OkHttp client with `127.0.0.1` $\rightarrow$ `10.0.2.2` fallback interceptor (`NetworkClient.kt`), and `@JsonClass(generateAdapter = true)` DTOs (`ApiModels.kt`). |
| [`android/.../ui/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/) | `DraftyViewModel.kt` (`AndroidViewModel`), reusable components (`DraftyMascot.kt`, `WaveformVisualizer.kt`, `Common.kt`), screens (`HomeScreen.kt`, `RecordingSheet.kt`, `IdeaContainerScreen.kt`), and theme tokens (`Color.kt`, `Shape.kt`, `Type.kt`, `Theme.kt`). |
| [`docs/`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/) | Project specifications and reports: `BRD.md`, `Implementation.md`, `Progress.md`, `Evaluation_Report.md`, `summary.md`, and `DesignSystem.pdf`. |

---

## 4. Data, State & Integrations

### Data Models & Database Schemas

#### A. Cloud PostgreSQL Schema (Supabase — [`001_initial_schema.sql`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/migrations/001_initial_schema.sql))
1. **`user_profiles`:**
   - `id` (`UUID PK DEFAULT gen_random_uuid()`), `email` (`TEXT UNIQUE`), `brand_name` (`TEXT`), `brand_voice_prompt` (`TEXT`), `created_at` (`TIMESTAMPTZ`).
2. **`voice_drafts`:**
   - `id` (`UUID PK`), `user_id` (`UUID FK -> user_profiles.id ON DELETE CASCADE`), `audio_storage_path` (`TEXT`), `raw_transcript` (`TEXT`), `duration_seconds` (`INT`), `status` (`TEXT CHECK IN ('pending', 'parsed', 'failed')`), `created_at` (`TIMESTAMPTZ`).
3. **`content_containers`:**
   - `id` (`UUID PK`), `draft_id` (`UUID FK -> voice_drafts.id ON DELETE CASCADE`), `user_id` (`UUID FK -> user_profiles.id ON DELETE CASCADE`), `title` (`TEXT`), `summary` (`TEXT`), `metadata` (`JSONB`), `assets` (`JSONB`), `creative_direction` (`JSONB`), `schedule` (`JSONB`), `is_pushed_to_drafted` (`BOOLEAN DEFAULT FALSE`), `created_at` (`TIMESTAMPTZ`), `updated_at` (`TIMESTAMPTZ`, auto-updated via `trg_containers_updated_at`).

#### B. Mobile Local SQLite Schema (Android Room — [`DraftyDatabase.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/DraftyDatabase.kt), `version = 2`)
1. **`idea_containers` ([`IdeaContainerEntity.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/IdeaContainerEntity.kt)):**
   - `id` (`String PK`), `title`, `summary`, `rawTranscript`, serialized JSON blobs (`metadataJson`, `assetsJson`, `creativeDirectionJson`, `scheduleJson`), `isPushedToDrafted` (`Boolean`), `createdAt` (`Long`).
2. **`pending_voice_notes` ([`PendingVoiceNoteEntity.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/PendingVoiceNoteEntity.kt)):**
   - `id` (`String PK`), `userId`, `audioFilePath`, `clientTimestamp`, `status` (`QUEUED` | `SYNCING` | `FAILED`), `retryCount` (`Int`), `lastError` (`String?`), `createdAt` (`Long`).

#### C. UI State Management ([`DraftyViewModel.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt))
- `recordingState: StateFlow<RecordingState>` (`Idle`, `Recording(elapsedSeconds)`, `Processing(step)`, `Success(containerId)`, `Error(message)`).
- `sheetVisible: StateFlow<Boolean>` (derived distinct boolean so per-second timer ticks never recompose `HomeScreen`).
- `amplitude: StateFlow<Int>` (collected exclusively inside `WaveformVisualizer`).
- `syncState: StateFlow<SyncState>` (`Idle`, `Syncing`, `Synced(scheduledFor)`, `Error(message)`).
- `recentIdeas: StateFlow<List<IdeaCard>>` & `pendingNotes: StateFlow<List<PendingVoiceNoteEntity>>` (backed by Room `Flow`s).
- `userProfile: StateFlow<UserProfileState>` & `networkLabel: StateFlow<String>`.

### External Services & Environment Variables
Configured in [`backend/.env.example`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/.env.example) and loaded by `python-dotenv` in [`main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L8) and [`db_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L12):
- **OpenAI API (`AsyncOpenAI`):**
  - `OPENAI_API_KEY` *(required at startup in [`AIService.__init__`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py#L51))*
  - `WHISPER_MODEL` *(optional, defaults to `"whisper-1"`)*
  - `PARSER_MODEL` *(optional, defaults to `"gpt-4o-mini"`)*
- **Supabase (PostgreSQL + Storage):**
  - `SUPABASE_URL` *(required for DB/Storage persistence)*
  - `SUPABASE_KEY` *(required for DB/Storage persistence)*
  - `SUPABASE_AUDIO_BUCKET` *(optional, defaults to `"voice-drafts"` in [`db_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L20))*
- **Server & Payload Limits:**
  - `MAX_AUDIO_BYTES` *(optional, defaults to `26214400` / 25 MB)*
  - `MIN_AUDIO_BYTES` *(optional, defaults to `2048` / 2 KB)*
  - `CORS_ORIGINS` *(optional, defaults to `"*"`)*
  - `LOG_LEVEL` *(optional, defaults to `"INFO"`)*

---

## 5. Observations & Gaps

1. **Brand Voice Prompt Not Wired into LLM Prompt (`ai_service.py` / `main.py`):**
   - Although [`db_service.get_brand_voice(user_id)`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py#L186-L190) is implemented to fetch a user's `brand_name` and `brand_voice_prompt` from `user_profiles`, it is never called in [`main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py#L194) or passed into [`AIService.parse()`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py#L70). Passing that string into the user/system prompt in `ai.parse()` would complete the personalization loop.
2. **BRD Security Requirement vs. Current Token Storage (`SharedPreferences` vs. `EncryptedSharedPreferences` & Cleartext Traffic):**
   - [BRD.md](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/BRD.md#L54) specifies TLS 1.3 over HTTPS and storing tokens in Android `EncryptedSharedPreferences`. Currently, for local emulator prototyping, [AndroidManifest.xml](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/AndroidManifest.xml#L14) enables `android:usesCleartextTraffic="true"` (to reach `http://127.0.0.1:8000/` and `http://10.0.2.2:8000/`) and [DraftyViewModel.kt](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt#L72) uses standard `Context.MODE_PRIVATE` `SharedPreferences`.
3. **Emulator vs. Physical Device Networking (`NetworkClient.kt`):**
   - [`NetworkClient.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/remote/NetworkClient.kt#L25-L45) targets `http://127.0.0.1:8000/` (which requires `adb reverse tcp:8000 tcp:8000` on the emulator or a USB-connected phone) and falls back to `http://10.0.2.2:8000/` (emulator host alias). If the app is run on a physical phone over Wi-Fi/cellular without USB `adb reverse`, `BASE_URL` must be pointed to the host machine's LAN IP or a deployed HTTPS URL.
4. **Local-Only Edits Before "Schedule to Drafted":**
   - When a user edits the title, caption, CTA, or schedule date/time in [`IdeaContainerScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt#L172-L179), `vm.updateContainer()` saves those edits to the local Android Room database immediately, while remote Supabase persistence of those edits occurs when the user taps **`[ 🚀 Schedule to Drafted ]`** (`POST /api/v1/containers/{id}/schedule-to-drafted`).
5. **Android Unit/Instrumented Tests Are Still Template Stubs:**
   - The backend has comprehensive automated tests ([`test_voice_api.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/test_voice_api.py) and [`benchmark_week3.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/benchmark_week3.py)), whereas [`ExampleUnitTest.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/test/java/com/cs312/drafty/ExampleUnitTest.kt) and [`ExampleInstrumentedTest.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/androidTest/java/com/cs312/drafty/ExampleInstrumentedTest.kt) on the Android side are still the default Android Studio template files.
