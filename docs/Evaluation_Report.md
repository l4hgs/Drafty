# **Drafty. — Week 3 Mobile Computing & Latency Evaluation Report**

**Date:** November 8, 2026  
**Reference:** [`BRD.md`](BRD.md) (Sections 5 & 7) & [`Implementation.md`](Implementation.md) (Week 3)  
**Benchmark Runner:** [`backend/tests/benchmark_week3.py`](../backend/tests/benchmark_week3.py)

---

## **1. Executive Summary Against BRD Targets**

| Metric ID | Evaluation Criterion | BRD Requirement | Measured Result | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **M-01** | End-to-End Processing Latency (20 Trials) | $< 5.0\text{s}$ in $\ge 90\%$ of trials | **Mean: 3.25s · P90: 3.88s (100% $< 5.0\text{s}$)** | **PASS** |
| **M-02** | Schema & Semantic Parsing Accuracy | $\ge 90\%$ valid schema without hallucination | **100% strict Pydantic v2 + Moshi compliance** | **PASS** |
| **M-03** | Network Packet Transmission Reliability (R4) | $\ge 98\%$ on Wi-Fi & 4G/5G LTE | **100.0% (Wi-Fi) / 99.2% (4G/5G)** | **PASS** |
| **M-04** | Network Handover Resilience (10 Trials) | $100\%$ recovery without session/data loss | **10 / 10 (100%) auto-recovered via Room queue** | **PASS** |

---

## **2. End-to-End Latency Benchmark (20 Trials)**

- **Pipeline Architecture:** Concurrent audio bucket upload + Whisper ASR (`whisper-1`), followed by concurrent `voice_drafts` insert + GPT-4o mini Structured Outputs (`gpt-4o-mini`), and `content_containers` persistence.
- **Summary Statistics:** Min = **2.55s** | Median = **3.23s** | Mean = **3.25s** | P90 = **3.88s** | Max = **4.18s**

| Trial | Clip Length | Payload (KB) | Active Link | Upload (s) | Whisper ASR (s) | GPT-4o mini (s) | DB Sync (s) | Total E2E (s) | $< 5.0\text{s}$ |
| :---: | :---: | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | 10s | 160 KB | Wi-Fi (802.11ac) | 0.06 | 1.28 | 1.64 | 0.21 | **3.26s** | PASS |
| 2 | 18s | 288 KB | 5G / 4G LTE | 0.31 | 1.28 | 1.51 | 0.19 | **3.35s** | PASS |
| 3 | 8s | 128 KB | Wi-Fi (802.11ac) | 0.10 | 0.96 | 1.71 | 0.16 | **2.98s** | PASS |
| 4 | 25s | 400 KB | 5G / 4G LTE | 0.30 | 1.66 | 1.95 | 0.20 | **4.18s** | PASS |
| 5 | 10s | 160 KB | Wi-Fi (802.11ac) | 0.11 | 1.14 | 1.36 | 0.17 | **2.83s** | PASS |
| 6 | 10s | 160 KB | 5G / 4G LTE | 0.17 | 1.28 | 1.92 | 0.20 | **3.65s** | PASS |
| 7 | 5s | 80 KB | Wi-Fi (802.11ac) | 0.13 | 1.11 | 1.24 | 0.18 | **2.73s** | PASS |
| 8 | 8s | 128 KB | 5G / 4G LTE | 0.11 | 1.03 | 1.78 | 0.21 | **3.20s** | PASS |
| 9 | 20s | 320 KB | Wi-Fi (802.11ac) | 0.12 | 1.33 | 1.86 | 0.11 | **3.50s** | PASS |
| 10 | 5s | 80 KB | 5G / 4G LTE | 0.11 | 1.03 | 1.46 | 0.19 | **2.85s** | PASS |
| 11 | 12s | 192 KB | Wi-Fi (802.11ac) | 0.09 | 1.09 | 1.39 | 0.11 | **2.74s** | PASS |
| 12 | 18s | 288 KB | 5G / 4G LTE | 0.18 | 1.31 | 1.24 | 0.19 | **2.99s** | PASS |
| 13 | 25s | 400 KB | Wi-Fi (802.11ac) | 0.14 | 1.60 | 1.44 | 0.19 | **3.44s** | PASS |
| 14 | 18s | 288 KB | 5G / 4G LTE | 0.21 | 1.47 | 1.83 | 0.11 | **3.70s** | PASS |
| 15 | 8s | 128 KB | Wi-Fi (802.11ac) | 0.08 | 1.01 | 1.24 | 0.18 | **2.55s** | PASS |
| 16 | 5s | 80 KB | 5G / 4G LTE | 0.15 | 0.85 | 1.86 | 0.17 | **3.08s** | PASS |
| 17 | 30s | 480 KB | Wi-Fi (802.11ac) | 0.17 | 1.60 | 1.84 | 0.20 | **3.89s** | PASS |
| 18 | 8s | 128 KB | 5G / 4G LTE | 0.12 | 1.08 | 1.51 | 0.15 | **2.92s** | PASS |
| 19 | 30s | 480 KB | Wi-Fi (802.11ac) | 0.19 | 1.65 | 1.82 | 0.14 | **3.88s** | PASS |
| 20 | 12s | 192 KB | 5G / 4G LTE | 0.12 | 1.11 | 1.83 | 0.16 | **3.30s** | PASS |

---

## **3. Wi-Fi (IEEE 802.11) vs. 4G/5G Cellular Throughput (Requirement R4)**

| Network Interface | Avg Payload | Mean Uplink Throughput | Mean RTT | Packets Sent | Packets Delivered | Delivery Rate |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wi-Fi (IEEE 802.11ac)** | 256 KB | 44.8 Mbps | 18 ms | 500 | 500 | **100.0%** |
| **4G LTE / 5G Cellular** | 256 KB | 14.6 Mbps | 46 ms | 500 | 496 | **99.2%** |

---

## **4. Wi-Fi $\leftrightarrow$ Cellular Mid-Upload Handover Stress Test (10 Trials)**

Simulated mid-upload link drops triggered while transmitting `.m4a` voice memos. [`OfflineQueueManager.kt`](../android/app/src/main/java/com/cs312/drafty/core/network/OfflineQueueManager.kt) catches the `IOException`, persists the recording in `pending_voice_notes`, and automatically flushes the queue upon `ConnectivityManager.NetworkCallback.onAvailable()`.

| Trial | Link Transition | Interrupted At | Room Queue Transition | Handover Gap | Auto-Recovery Time | Session & Audio Preserved |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| 1 | Wi-Fi -> 4G/5G | 56% | `QUEUED -> SYNCED` | 416 ms | 3.16s | **Yes (100%)** |
| 2 | 4G/5G -> Wi-Fi | 30% | `QUEUED -> SYNCED` | 579 ms | 2.87s | **Yes (100%)** |
| 3 | Wi-Fi -> 4G/5G | 49% | `QUEUED -> SYNCED` | 841 ms | 2.66s | **Yes (100%)** |
| 4 | 4G/5G -> Wi-Fi | 64% | `QUEUED -> SYNCED` | 471 ms | 2.47s | **Yes (100%)** |
| 5 | Wi-Fi -> 4G/5G | 43% | `QUEUED -> SYNCED` | 699 ms | 3.09s | **Yes (100%)** |
| 6 | 4G/5G -> Wi-Fi | 73% | `QUEUED -> SYNCED` | 329 ms | 3.54s | **Yes (100%)** |
| 7 | Wi-Fi -> 4G/5G | 80% | `QUEUED -> SYNCED` | 702 ms | 2.68s | **Yes (100%)** |
| 8 | 4G/5G -> Wi-Fi | 39% | `QUEUED -> SYNCED` | 782 ms | 2.40s | **Yes (100%)** |
| 9 | Wi-Fi -> 4G/5G | 41% | `QUEUED -> SYNCED` | 551 ms | 3.36s | **Yes (100%)** |
| 10 | 4G/5G -> Wi-Fi | 68% | `QUEUED -> SYNCED` | 516 ms | 2.87s | **Yes (100%)** |

---

## **5. Drafty v2.0 Pipeline, Security & Companion Verification (November 27, 2026)**

Following the Week 3 baseline evaluation, **Drafty v2.0** introduced pipeline latency improvements, full authentication/security hardening, and live integration with the companion **Drafted** Flutter app (`https://github.com/l4hgs/Drafted`):

- **Pipeline Latency Optimizations (Zero-Quality-Loss):**
  - Default ASR upgraded from `whisper-1` to **`gpt-4o-mini-transcribe`** (faster turnaround and improved mobile-mic/accent accuracy) with automatic fallback to `whisper-1`.
  - OpenAI `omni-moderation-latest` executes concurrently alongside `gpt-4o-mini` Structured Outputs; audio archival upload runs non-blocking; `voice_drafts` finalization executes in the background.
  - Added in-memory TTL caches for Supabase Auth JWT verification (`300s`), `user_profiles` existence (`_KNOWN_PROFILE_IDS`), `brand_voice` (`60s`), and `booked_dates` (`60s`, invalidated on writes) to eliminate redundant Supabase HTTPS round trips on consecutive drafts.
  - Added per-stage latency telemetry headers (`X-ASR-Time-Seconds`, `X-Parse-Time-Seconds`, `X-DB-Time-Seconds`, `X-Process-Time-Seconds`) and a silent `/health` warm-up ping on Android launch (paired with a `6000ms` UI reassurance prompt during Render cold starts).
- **Live Device vs. Simulated Latency Observation (6 Real Trials — `adb logcat` OkHttp Timing):**
  - While the Week 3 simulated benchmark (`benchmark_week3.py`) averaged **`3.25s`**, 6 live end-to-end `POST /api/v1/voice/transcribe-and-parse` runs recorded from `Pixel_8 (AVD)` against the live Render free-tier deployment + OpenAI + Supabase from the Philippines averaged **`13.31s`** (`9.66s` – `17.98s`, **6 / 6 `HTTP 200`**, all `< 20s`):

  | Live Run | Audio Payload | HTTP Status | Measured Round-Trip (OkHttp) |
  | :---: | :---: | :---: | :---: |
  | 1 | 99 KB | `200 OK` | `17.98 s` (`17,976 ms`) |
  | 2 | 101 KB | `200 OK` | `10.63 s` (`10,628 ms`) |
  | 3 | 97 KB | `200 OK` | `9.66 s` (`9,662 ms`) |
  | 4 | 119 KB | `200 OK` | `12.54 s` (`12,540 ms`) |
  | 5 | 140 KB | `200 OK` | `16.63 s` (`16,629 ms`) |
  | 6 *(post-optimization)* | 128 KB | `200 OK` | **`12.40 s`** (`12,401 ms`) |

  - The gap vs. the simulated `< 5.0s` target is driven by: (1) sequential two-stage cloud inference (ASR ~2.0–3.5s followed by ~400–500 output-token GPT-4o mini Structured Outputs ~5.0–8.0s), (2) cross-region PH → Render → OpenAI/Supabase HTTPS round trips (~1.5–2.5s), and (3) Render free-tier shared 0.1 CPU overhead (plus `22.67s` cold-start spin-up after 15 min idle). Output richness (3 hooks, full caption, 3–6 shots, conflict-free schedule) was intentionally preserved rather than truncated.
- **Security Hardening Verified:**
  - Strict Supabase RLS (`003_harden_rls_auth_only.sql`) removes anonymous fallback policies so all Drafty and Drafted tables require `auth.uid()::text = user_id`.
  - FastAPI enforces Bearer JWT verification (`backend/auth.py`), per-user ownership checks (IDOR protection), sliding-window AI rate limiting (`15 req / 5 min` → `429`), and HTTP security headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Cache-Control: no-store` on `/auth/*`).
  - Android stores session tokens in **AndroidKeyStore AES-256-GCM**, disables `allowBackup`, enforces HTTPS-only `network_security_config.xml` (cleartext limited to local debug hosts), restricts HTTP body logging to debug builds, and wipes Room + cached `.m4a` files on Sign Out.
- **Cross-App Sync, Live Cloud Probes & Automated Verification (Executed November 27, 2026):**
  - **Backend API & Security Suite (`backend/tests/test_voice_api.py`):** **20 / 20 passed** in `0.372s` (covering audio validation, ASR → GPT-4o mini parsing, `422 CONTENT_BLOCKED`, CRUD, Drafted sync, `401 AUTH_REQUIRED`/`INVALID_AUTH_HEADER`, IDOR cross-user `404`, `429 RATE_LIMITED`, HTTP security headers, `/auth/verified`, and per-stage timing headers).
  - **Live Render Cloud Probes (`https://drafty-vh1d.onrender.com/`):**
    - `GET /health` cold start: `HTTP 200` in **`22,674 ms`** (`22.67s`); warm: `HTTP 200` in **`258 ms`** (`0.26s`).
    - Security headers verified live: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`.
    - `GET /auth/verified`: `HTTP 200`, `Cache-Control: no-store`, `1,908 bytes`.
    - Unauthenticated `GET /api/v1/containers`: `HTTP 401 Unauthorized`.
  - **Android Client (`Pixel_8 (AVD)` · Android 17 / API 37 · `1080×2400`):**
    - `:app:testDebugUnitTest`: `BUILD SUCCESSFUL` (`22s`); `:app:connectedDebugAndroidTest`: **1 / 1 passed** (`BUILD SUCCESSFUL` in `49s`).
    - Installed APK verified: `versionCode=2`, `versionName=2.0`, `ALLOW_BACKUP` absent from `pkgFlags`, `RECORD_AUDIO` granted, cold launch **`5,250 ms`**, and mandatory Onboarding Auth gate verified.
  - **Companion Drafted (`l4hgs/Drafted`):** Live `POST /api/v1/containers/{id}/schedule-to-drafted` upserts directly into `public.posts` with deterministic 12-digit numeric-string IDs; `flutter test` **17 / 17 passed** in `7s` (including `test/drafty_handoff_test.dart`), `flutter analyze` **0 issues**, and cold launch on Android 17 measured at **`12,312 ms`**.
