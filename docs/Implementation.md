### **Sprint Schedule Overview**

> **Live Progress Tracker:** See [`Progress.md`](file:///C:/Users/alodi/OneDrive/Desktop/Drafty/docs/Progress.md) for detailed deliverable status and file references.

| Week | Focus Area | Primary Deliverables | Target Milestone | Status |
| :---- | :---- | :---- | :---- | :---- |
| **Week 1** | **Core Foundations & Audio Capture** | Kotlin Jetpack Compose UI skeleton, native audio recording pipeline (MediaRecorder), FastAPI server scaffolding, and Whisper ASR integration. | Raw voice recorded on Android and successfully transcribed to text on the backend. | ✅ **Completed (100%)** |
| **Week 2** | **AI Parsing Engine & Container UI** | Schema-constrained prompt engineering (GPT-4o mini), multi-tab Idea Container UI (Script, Creatives, Schedule), and Room local database caching. | Voice input reliably outputs a formatted multi-tab container on the phone screen. | ✅ **Completed (100%)** |
| **Week 3** | **Drafted Sync, Mobile Network Testing & Polish** | Supabase database sync, Drafted queue push, Wi-Fi/Cellular handover testing, latency optimization ($<5s$), and presentation deck/demo prep. | Full end-to-end working demonstration under varying network conditions. | ✅ **Completed (100%)** |
| **v2.0 Upgrade** | **Unified Auth, Live Drafted Sync, UX & Security Hardening** | Shared Supabase Auth (Email + Google OAuth), live `public.posts` upsert into Drafted, hands-free Lock-to-Record, swipeable tabs, responsive landscape UI, AndroidKeyStore AES-256-GCM, strict `auth.uid()` RLS, IDOR & rate-limit hardening. | Production-hardened v2.0 APK and live Drafty ↔ Drafted calendar sync on Render + Supabase. | ✅ **Completed (100%)** |

### **Detailed Week-by-Week Breakdown**

#### **Week 1: Core Client-Server Scaffolding & Audio Pipeline**

*Objective: Build the basic client recorder and ensure the server can ingest audio and return text.*

* **Member 1 (Lead Developer / AI & Architecture):**

  * Set up the Python FastAPI backend project structure.

  * Integrate OpenAI Whisper API endpoint for speech-to-text transcription.

  * Define API contracts and endpoints (POST /api/v1/voice/transcribe-and-parse).

* **Member 2 (Mobile Frontend & Voice Capture Lead):**

  * Scaffold the native Android project in Android Studio using **Kotlin** and **Jetpack Compose**.

  * Implement the native audio capture module using Android's MediaRecorder or AudioRecord APIs (handling runtime microphone and storage permissions).

  * Build the minimal home screen layout with the central mic button and recording timer.

* **Member 3 (Backend Integration & Database Lead):**

  * Set up the Supabase project, initialize tables for voice\_drafts, content\_containers, and user\_profiles.

  * Implement audio file handling and storage buckets (temporary .m4a / .wav upload endpoints).

  * Write automated tests for audio file payload reception.

#### **Week 2: AI Parsing Engine, Container UI & Offline Caching**

*Objective: Transform transcribed text into structured containers and display them in the app.*

* **Member 1 (Lead Developer / AI & Architecture):**

  * Engineer the structured JSON prompt pipeline using GPT-4o mini with strict schema enforcement (Title, Hooks, Script/Caption, Visual Direction, and Recommended Schedule).

  * Connect transcription output directly into the parser pipeline to achieve sub-second backend execution.

* **Member 2 (Mobile Frontend & Voice Capture Lead):**

  * Develop the **Idea Container UI** featuring the tabbed interface:

    * **Tab 1:** Script & Hook options.

    * **Tab 2:** Creative Direction & Shot List.

    * **Tab 3:** Schedule (Date, Day, Peak Time selector).

  * Build the real-time processing indicator screen (transcribing $\rightarrow$ parsing status bar).

* **Member 3 (Backend Integration & Database Lead):**

  * Implement Android **Room Database** on the mobile client for local caching of voice notes and drafts.

  * Build an offline queue manager that holds payloads if the device loses connection and sends them once reconnected.

#### **Week 3: Drafted Synchronization, Network Stress Testing & Evaluation**

*Objective: Complete ecosystem integration, measure performance metrics, and prepare the final demo.*

* **Member 1 (Lead Developer / AI & Architecture):**

  * Benchmark end-to-end latency across 20 trials, optimizing prompt tokens and audio payload sizes to ensure response times remain strictly under **5.0 seconds**.

  * Finalize error handling and edge cases (e.g., inaudible speech, extremely short audio clips).

* **Member 2 (Mobile Frontend & Voice Capture Lead):**

  * Wire the **\[🚀 Schedule Draft\]** button to trigger the Supabase API sync call.

  * Implement network state listeners to dynamically switch between Wi-Fi and 4G/5G without dropping user session state.

  * Polish animations, typography, and visual feedback following the clean "Productive Minimalism" design standard.

* **Member 3 (Backend Integration & Database Lead):**

  * Complete direct integration pushing parsed containers into the **Drafted** workspace database schema.

  * Conduct the required mobile computing evaluations:

    * Measure upload throughput on Wi-Fi vs. 4G/5G (verifying requirement **R4**).

    * Test connection handovers (switching from Wi-Fi to cellular mid-upload).

  * Compile metric logs and screenshots for the final documentation and demonstration slides.

#### **v2.0 Upgrade: Unified Auth, Live Drafted Sync, UX & Security Hardening**

*Objective: Unify Drafty and Drafted under one authenticated Supabase backend, harden client/server security, and upgrade mobile ergonomics.*

* **Unified Supabase Schema & Live Sync:**
  * Migrated Drafted's 5 tables (`posts`, `subtasks`, `radar_events`, `checklist_presets`, `milestone_stickers`) into Drafty's canonical Supabase project (`002_unified_drafted_and_auth.sql`, `003_harden_rls_auth_only.sql`) with strict `auth.uid()::text = user_id` RLS and safe `handle_new_auth_user()` profile provisioning.
  * Wired `POST /api/v1/containers/{id}/schedule-to-drafted` to upsert directly into `public.posts` using deterministic 12-digit numeric-string IDs and added 1-tap `com.example.drafted` launch from `DraftedHandoffCard`.
* **Authentication & Security Hardening:**
  * Added mandatory 5-step onboarding with Email/Password (`GET /auth/verified` HTTPS confirmation landing page + `EmailVerificationWaitingModal`) and Google OAuth (`drafty://auth-callback`, `drafted://auth-callback`).
  * Secured Android with AndroidKeyStore AES-256-GCM token encryption, `allowBackup="false"`, HTTPS-only `network_security_config.xml`, debug-only HTTP logging, and full local Room + `.m4a` wipe on Sign Out.
  * Hardened FastAPI with Supabase Bearer JWT verification, per-user IDOR checks, sliding-window AI rate limiting (`15 req / 5 min`), and HTTP security headers.
* **UX & Pipeline Upgrades:**
  * Added hands-free Lock-to-Record, `6000ms` Render cold-start reassurance + silent `/health` warm-up, swipeable `HorizontalPager` container tabs, tap-to-select Primary Hook, unified Edit/Cancel, `SwipeToDismissBox` with frosted `GlassToast` Undo, Drafted-aligned typography/counters, and responsive two-pane landscape layouts.
  * Parallelized OpenAI Moderation + GPT-4o mini, moved audio archival and `voice_drafts` finalization off the critical path, and added deterministic date/weekday normalization with a 24h clock-drift guard.