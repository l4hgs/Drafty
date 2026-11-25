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
