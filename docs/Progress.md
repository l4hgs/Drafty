# **Project Progress Report: Drafty.**

**Last Updated:** November 8, 2026  
**Reference:** [`Implementation.md`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/Implementation.md) & [`BRD.md`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/BRD.md)

---

## **1. Executive Summary & Sprint Status**

| Week | Focus Area | Target Milestone | Status | Completion |
| :--- | :--- | :--- | :--- | :--- |
| **Week 1** | **Core Foundations & Audio Capture** | Raw voice recorded on Android and transcribed to text on the backend. | **Completed** | **100%** |
| **Week 2** | **AI Parsing Engine & Container UI** | Voice input reliably outputs a formatted multi-tab container on the phone screen. | **Completed** | **100%** |
| **Week 3** | **Drafted Sync, Mobile Network Testing & Polish** | Full end-to-end working demonstration under varying network conditions. | **Completed** | **100%** |

---

## **2. Detailed Week-by-Week Deliverable Tracking**

### **Week 1: Core Client-Server Scaffolding & Audio Pipeline**
*Objective: Build the basic client recorder and ensure the server can ingest audio and return text.*

#### **Member 1 (Lead Developer / AI & Architecture)**
- [x] **Set up the Python FastAPI backend project structure**
  - Implemented in [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py), [`backend/models.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/models.py), [`backend/requirements.txt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/requirements.txt), and [`backend/.env.example`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/.env.example).
  - Configured CORS middleware, lifespan initialization, `.env` loading, and a `/health` check endpoint.
- [x] **Integrate OpenAI Whisper API endpoint for speech-to-text transcription**
  - Implemented `AIService.transcribe()` in [`backend/services/ai_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py) using `AsyncOpenAI` (`whisper-1`).
- [x] **Define API contracts and endpoints (`POST /api/v1/voice/transcribe-and-parse`)**
  - Implemented multipart upload endpoint in [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py) with strict Pydantic v2 models (`ConfigDict(extra="forbid")`) and flat JSON error responses (`{"detail": "...", "code": "..."}`).

#### **Member 2 (Mobile Frontend & Voice Capture Lead)**
- [x] **Scaffold the native Android project in Android Studio using Kotlin and Jetpack Compose**
  - Configured under `com.cs312.drafty` in [`android/app/build.gradle.kts`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/build.gradle.kts) and [`android/gradle/libs.versions.toml`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/gradle/libs.versions.toml) with Compose BOM, Material 3, Navigation Compose, Retrofit2, Moshi (KSP codegen), OkHttp3, and Room.
- [x] **Implement the native audio capture module using Android's `MediaRecorder` API**
  - Implemented in [`AudioRecorder.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/audio/AudioRecorder.kt) recording AAC `.m4a` at 44.1 kHz / 128 kbps mono.
  - Added runtime `RECORD_AUDIO` permission launcher in [`HomeScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt) and safe stop/minimum file size (>1 KB) validation.
- [x] **Build the minimal home screen layout with the central mic button and recording timer**
  - Implemented in [`HomeScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt) and [`RecordingSheet.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/RecordingSheet.kt) with isolated state collection to prevent sheet recomposition flicker.

#### **Member 3 (Backend Integration & Database Lead)**
- [x] **Set up the Supabase project, initialize tables for `voice_drafts`, `content_containers`, and `user_profiles`**
  - SQL migration created in [`backend/migrations/001_initial_schema.sql`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/migrations/001_initial_schema.sql) with UUID primary keys, foreign key cascades, indexes on `user_id` and `created_at`, `updated_at` trigger, and Row-Level Security (RLS).
  - Database service implemented in [`backend/services/db_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py).
- [x] **Implement audio file handling and storage buckets (temporary `.m4a` / `.wav` upload endpoints)**
  - Validated MIME types (`audio/m4a`, `audio/mp4`, `audio/wav`) and payload size bounds (2 KB – 25 MB) in [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py).
  - Added private `voice-drafts` bucket in [`backend/migrations/001_initial_schema.sql`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/migrations/001_initial_schema.sql), `upload_audio_to_storage()` in [`backend/services/db_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py), standalone `POST /api/v1/voice/upload` endpoint, and concurrent bucket upload in `POST /api/v1/voice/transcribe-and-parse`.
- [x] **Write automated tests for audio file payload reception**
  - Implemented 9 automated tests in [`backend/tests/test_voice_api.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/test_voice_api.py) covering valid `.m4a`/`.wav`/`.mp4` uploads, end-to-end transcription & parsing, Drafted scheduling, and all error codes (`INVALID_USER_ID`, `UNSUPPORTED_MEDIA`, `TOO_SHORT`, `TOO_LARGE`, `NO_SPEECH`, `CONTAINER_NOT_FOUND`).

---

### **Week 2: AI Parsing Engine, Container UI & Offline Caching**
*Objective: Transform transcribed text into structured containers and display them in the app.*

#### **Member 1 (Lead Developer / AI & Architecture)**
- [x] **Engineer the structured JSON prompt pipeline using GPT-4o mini with strict schema enforcement**
  - Implemented `AIService.parse()` in [`backend/services/ai_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py) using OpenAI Structured Outputs (`client.beta.chat.completions.parse` with `response_format=ParsedContent`).
- [x] **Connect transcription output directly into the parser pipeline**
  - Integrated end-to-end in [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py): Whisper ASR $\rightarrow$ Supabase `voice_drafts` (`pending`) $\rightarrow$ GPT-4o mini structured parse $\rightarrow$ Supabase `content_containers` (`parsed`).

#### **Member 2 (Mobile Frontend & Voice Capture Lead)**
- [x] **Develop the Idea Container UI featuring the tabbed interface**
  - Implemented in [`IdeaContainerScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt):
    - **Tab 1 (`Script`):** Hook options, formatted caption/script, CTA, and hashtags on `DraftyLavender`, with inline editing for Title, Caption, and CTA.
    - **Tab 2 (`Creatives`):** Visual style badge, numbered shot list with timestamps, and text overlays on `DraftySage`.
    - **Tab 3 (`Schedule`):** Interactive Date & Peak Time pickers (`DatePickerDialog` and `TimePickerDialog` in [`IdeaContainerScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt)) that automatically recalculate the weekday (`recommendedDay`) and persist schedule changes into Room.
- [x] **Build the real-time processing indicator screen (transcribing $\rightarrow$ parsing status bar)**
  - Implemented in [`RecordingSheet.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/RecordingSheet.kt) (`ProcessingContent` with `MascotState.THINKING` and step indicators for *Whisper transcribing* $\rightarrow$ *GPT-4o mini structuring*).

#### **Member 3 (Backend Integration & Database Lead)**
- [x] **Implement Android Room Database on the mobile client for local caching of drafts and containers**
  - Implemented in [`IdeaContainerEntity.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/IdeaContainerEntity.kt), [`PendingVoiceNoteEntity.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/PendingVoiceNoteEntity.kt), [`Converters.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/Converters.kt), [`DraftyDao.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/DraftyDao.kt), and [`DraftyDatabase.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/data/local/DraftyDatabase.kt).
- [x] **Build an offline queue manager that holds audio payloads if the device loses connection and sends them once reconnected (FR-02)**
  - Implemented in [`OfflineQueueManager.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt), [`DraftyViewModel.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/ui/DraftyViewModel.kt), and [`HomeScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt) (`OfflineQueueCard` with manual `↻ Sync Now` and automatic `ConnectivityManager.NetworkCallback` flush).

---

### **Week 3: Drafted Synchronization, Network Stress Testing & Evaluation**
*Objective: Complete ecosystem integration, measure performance metrics, and prepare the final demo.*

#### **Member 1 (Lead Developer / AI & Architecture)**
- [x] **Finalize error handling and edge cases (inaudible speech, short audio clips)**
  - Implemented minimum byte & duration checks on Android ([`AudioRecorder.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/audio/AudioRecorder.kt), [`DraftyViewModel.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt)) and `MIN_AUDIO_BYTES` / `EmptyTranscriptError` checks on FastAPI ([`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py), [`backend/services/ai_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/ai_service.py)).
- [x] **Benchmark end-to-end latency across 20 trials ($<5.0\text{s}$ target)**
  - Parallelized `save_voice_draft()` with `ai.parse()` and added `X-Process-Time-Seconds` response header in [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py).
  - Executed 20-trial latency benchmark in [`backend/tests/benchmark_week3.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/benchmark_week3.py) (100% of trials $< 5.0\text{s}$; full breakdown in [`Evaluation_Report.md`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/Evaluation_Report.md)).

#### **Member 2 (Mobile Frontend & Voice Capture Lead)**
- [x] **Wire the `[🚀 Schedule to Drafted]` button to trigger the Supabase API sync call**
  - Wired in [`IdeaContainerScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt) and [`DraftyViewModel.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/DraftyViewModel.kt) with celebration overlay (`MascotState.CELEBRATING` + confetti animation), persisting any inline title/caption/CTA/schedule edits via `DraftedSyncRequest`.
- [x] **Polish animations, typography, and visual feedback following the "Productive Minimalism" design system**
  - Implemented custom vector [`DraftyMascot.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/components/DraftyMascot.kt) (4 animated states: `IDLE`, `LISTENING`, `THINKING`, `CELEBRATING`), [`WaveformVisualizer.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/components/WaveformVisualizer.kt), pastel Bento tokens in [`Color.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/theme/Color.kt), [`Shape.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/theme/Shape.kt), [`Theme.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/theme/Theme.kt), and complete Material 3 typography hierarchy in [`Type.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/theme/Type.kt).
- [x] **Implement network state listeners to dynamically switch between Wi-Fi and 4G/5G without dropping session state**
  - Implemented `ConnectivityManager.NetworkCallback` observer in [`OfflineQueueManager.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt) with live network badge and automatic queue flush on [`HomeScreen.kt`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt).

#### **Member 3 (Backend Integration & Database Lead)**
- [x] **Complete direct integration pushing parsed containers into the Drafted workspace database schema**
  - Implemented `POST /api/v1/containers/{container_id}/schedule-to-drafted` in [`backend/main.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/main.py) and `sync_to_drafted_workspace()` in [`backend/services/db_service.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/services/db_service.py) with `DraftedSyncRequest` support.
- [x] **Conduct mobile computing evaluations & compile metric logs**
  - Automated in [`backend/tests/benchmark_week3.py`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/backend/tests/benchmark_week3.py) and compiled in [`docs/Evaluation_Report.md`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/Evaluation_Report.md) (Wi-Fi vs. 4G/5G throughput, 20-trial E2E latency, and 10-trial Wi-Fi $\leftrightarrow$ Cellular mid-upload handover recovery).

---

## **3. Week 3 Action Plan & To-Do Checklist**

### **Task 1 — Prompt & Payload Latency Optimization + 20-Trial Benchmark (Member 1)**
- [x] **1.1 Optimize AI Parser Prompt & Concurrency (`backend/services/ai_service.py` & `backend/main.py`)**
- [x] **1.2 Run 20-Trial Latency Benchmark Suite (`backend/tests/benchmark_week3.py`)**

### **Task 2 — Typography Polish, Session Preservation & Sync Resilience (Member 2)**
- [x] **2.1 Complete "Productive Minimalism" Typography Scale (`ui/theme/Type.kt`)**
- [x] **2.2 Resilient Drafted Sync Across Network Handovers (`DraftyViewModel.kt` & `OfflineQueueManager.kt`)**

### **Task 3 — Drafted Workspace Sync Update & Mobile Computing Evaluations (Member 3)**
- [x] **3.1 Support Schedule/Edit Payload on Drafted Sync (`backend/main.py`, `backend/models.py`, `backend/services/db_service.py`)**
- [x] **3.2 Wi-Fi vs. 4G/5G Throughput & 10-Trial Network Handover Evaluation (`backend/tests/benchmark_week3.py`)**
- [x] **3.3 Generate Final Evaluation & Metrics Report (`docs/Evaluation_Report.md`)**
- [x] **3.4 Final Verification**

