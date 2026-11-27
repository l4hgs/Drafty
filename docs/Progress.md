# **Project Progress Report: Drafty**

**Last Updated:** November 27, 2026  
**Release:** `v2.0` (`https://github.com/l4hgs/Drafty`) · **Companion App:** [Drafted](https://github.com/l4hgs/Drafted)  
**References:** [Implementation.md](Implementation.md) · [BRD.md](BRD.md) · [Evaluation_Report.md](Evaluation_Report.md) · [UI_Design_System.md](UI_Design_System.md)

---

## 1. Sprint & Release Status

| Phase | Focus Area | Status | Completion |
| :--- | :--- | :--- | :---: |
| **Week 1** | Core Client-Server Scaffolding, Audio Capture & Supabase Storage | **Completed** | **100%** |
| **Week 2** | GPT-4o mini Structured Parsing, Idea Container UI & Room Offline Queue | **Completed** | **100%** |
| **Week 3** | Drafted Sync, Mobile Network Stress Tests & Evaluation Report | **Completed** | **100%** |
| **v1.0 Polish** | Apple Liquid Glass + 1-Bit Pixel UI, Onboarding, Smart Scheduling, Hook Regeneration & Render Deploy Setup | **Completed** | **100%** |
| **v2.0 Release** | Unified Supabase Auth, Live Drafted `public.posts` Sync, Hands-Free Lock-to-Record, Responsive Two-Pane UX & Security Hardening | **Completed** | **100%** |

---

## 2. Completed Deliverables

### Backend & AI Pipeline (`backend/`)
- [x] FastAPI gateway with strict Pydantic v2 (`extra="forbid"`), flat JSON error handlers, HTTP security headers middleware (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Cache-Control: no-store` on `/auth/*`), and `X-Process-Time-Seconds` telemetry ([main.py](../backend/main.py), [models.py](../backend/models.py)).
- [x] Supabase Bearer JWT authentication (`get_authenticated_user_id`), optional `REQUIRE_AUTH`, per-user IDOR ownership enforcement on all container endpoints, sliding-window AI rate limiting (`15 req / 5 min` → `429 RATE_LIMITED`), and HTTPS email confirmation landing page at `GET /auth/verified` ([auth.py](../backend/auth.py), [main.py](../backend/main.py)).
- [x] OpenAI Whisper (`whisper-1`) ASR + parallel `omni-moderation-latest` safety check & `gpt-4o-mini` Structured Outputs with brand-voice injection, non-blocking audio archival, background `voice_drafts` finalization, and deterministic conflict-free date/weekday normalization with 24h clock-drift guard ([ai_service.py](../backend/services/ai_service.py)).
- [x] One-tap AI hook regeneration (`POST /api/v1/containers/{id}/regenerate-hooks`) and live Drafted calendar upsert (`POST /api/v1/containers/{id}/schedule-to-drafted`) writing directly into `public.posts` with deterministic 12-digit numeric-string IDs.
- [x] Unified Supabase PostgreSQL migrations (`001_initial_schema.sql`, `002_unified_drafted_and_auth.sql`, `003_harden_rls_auth_only.sql`) unifying Drafty and Drafted tables under strict `auth.uid()::text = user_id` RLS policies and `handle_new_auth_user()` profile sync ([db_service.py](../backend/services/db_service.py)).
- [x] Automated unit/integration tests ([test_voice_api.py](../backend/tests/test_voice_api.py)) and 20-trial latency / 10-trial network handover benchmark suite ([benchmark_week3.py](../backend/tests/benchmark_week3.py)).
- [x] Live cloud deployment on Render (`https://drafty-vh1d.onrender.com/`) via [render.yaml](../render.yaml) and [backend/Dockerfile](../backend/Dockerfile).

### Android Mobile Client (`android/`)
- [x] Native `MediaRecorder` AAC `.m4a` capture + `OfflineQueueManager` auto-retry on `ConnectivityManager` network recovery, plus silent `/health` warm-up ping on launch ([AudioRecorder.kt](../android/app/src/main/java/com/cs312/drafty/core/audio/AudioRecorder.kt), [OfflineQueueManager.kt](../android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt)).
- [x] Room v2 local persistence (`idea_containers`, `pending_voice_notes`), Retrofit/Moshi client with debug-only HTTP logging, HTTPS-only `network_security_config.xml`, `allowBackup="false"`, AndroidKeyStore AES-256-GCM encrypted token storage (`SupabaseAuthManager.kt`), and full local Room + `.m4a` wipe on Sign Out ([NetworkClient.kt](../android/app/src/main/java/com/cs312/drafty/data/remote/NetworkClient.kt)).
- [x] Mandatory 5-step onboarding with Email/Password sign-up/sign-in (password visibility toggle, `/auth/verified` redirect, `EmailVerificationWaitingModal` with 60s resend cooldown) and Google OAuth (`drafty://auth-callback`) ([OnboardingScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/OnboardingScreen.kt)).
- [x] Voice-first Home screen with `{ Drafty }` pixel wordmark, ambient tap-to-seed chat bubbles, notched liquid-glass bottom dock, hands-free **Lock-to-Record**, `IDEAS · SYNCED · DRAFTS` counter bar, `SwipeToDismissBox` with frosted `GlassToast` Undo, and responsive two-pane landscape layout ([HomeScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/HomeScreen.kt), [Common.kt](../android/app/src/main/java/com/cs312/drafty/ui/components/Common.kt)).
- [x] Edge-lit RGB liquid-glass recording overlay with live amplitude ripples, slide-to-lock / slide-up-to-cancel gestures, and `6000ms` Render cold-start reassurance ([LiquidGlassRecordingOverlay.kt](../android/app/src/main/java/com/cs312/drafty/ui/components/LiquidGlassRecordingOverlay.kt)).
- [x] Swipeable `HorizontalPager` Idea Container (`Script`, `Creatives`, `Schedule`) with tap-to-select Primary Hook, AI Hook Regeneration, unified inline Edit/Cancel (including shot-list editing), `DraftedHandoffCard`, and 1-tap launch into companion **Drafted** (`com.example.drafted`) ([IdeaContainerScreen.kt](../android/app/src/main/java/com/cs312/drafty/ui/screens/IdeaContainerScreen.kt)).

### Companion Drafted Integration ([`l4hgs/Drafted`](https://github.com/l4hgs/Drafted))
- [x] Shared Supabase Auth (`drafted://auth-callback`), `DraftedAuthGate` with `AppLifecycleState.resumed` auto-sync, `AccountSheet` (Sync Now + Sign Out with local cache reset), and verified Drafty → Drafted handoff tests (`17/17` passing, `0` analyzer issues).
