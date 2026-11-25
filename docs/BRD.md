# **BUSINESS REQUIREMENTS DOCUMENT (BRD)**

## **Project Name: Drafty.**

### **1\. Project Overview & Executive Summary**

**Drafty.** is an innovative, voice-driven mobile AI assistant application designed for digital content creators, student leaders, and professionals. It eliminates "blank page syndrome" and the friction of mobile typing by allowing users to speak unstructured, stream-of-consciousness voice notes while on-the-go. The app records, compresses, and transmits audio payloads over cellular or Wi-Fi networks to a cloud backend where AI transcribes, parses, and structures the input into a multi-tab content container complete with titles, platform-specific hooks, captions, visual direction, schedules, and metadata—ready for direct synchronization with the **Drafted** workspace.

### **2\. Project Scope & Objectives**

#### **2.1 General Objective**

To develop a functional mobile application prototype in **Kotlin** that captures unscripted voice memos, transmits them over wireless networks, and translates them into structured content containers in under 5 seconds.

#### **2.2 Specific Objectives**

1. **Low-Latency Voice Capture:** Implement native Android audio recording modules optimized for stability and minimal file size.

2. **Network Resilience & Mobility:** Support seamless transitions and data synchronization across Wi-Fi and 4G/5G cellular networks using Mobile IP principles.

3. **Automated AI Parsing:** Leverage cloud-based Speech-to-Text (Whisper) and LLM parsing engines to automatically generate structured multi-platform content kits.

4. **Direct Ecosystem Sync:** Provide a one-tap synchronization mechanism to push finalized content containers directly into the **Drafted** workspace database.

#### **2.3 Scope Boundaries**

* **In Scope:** Android mobile app UI (Jetpack Compose), audio recorder, local SQLite/Room caching queue, HTTPS REST API client, cloud backend (FastAPI \+ Supabase), and Drafted synchronization.

* **Out of Scope:** Direct automated auto-posting to external social media platforms (TikTok, Instagram, etc.); the application lifecycle concludes at the Drafted scheduling queue.

### **3\. Target Users & Stakeholders**

* **Primary Users:** Content creators, student organization officers (e.g., Junior Philippine Computer Society members), and mobile professionals who need to bank ideas quickly while walking, commuting, or working away from a desktop.

* **Evaluators / Course Instructors:** Faculty members of the College of Informatics and Computing Sciences assessing mobile computing competency, network efficiency, and systems design.

### **4\. Functional Requirements**

| Feature ID | Feature Name | Description | Priority |
| :---- | :---- | :---- | :---- |
| **FR-01** | One-Tap Voice Recording | The user can tap a prominent microphone button on the home screen to instantly record an unscripted audio brain-dump. | High |
| **FR-02** | Local Audio Caching | Recorded audio is temporarily saved in local storage (Room database) with a retry queue to prevent data loss during poor connectivity. | High |
| **FR-03** | Network Adaptive Upload | The app packages and transmits audio payloads securely via HTTPS over the active network interface (Wi-Fi or Cellular). | High |
| **FR-04** | AI Content Parsing | The backend transcribes audio and parses intent into a structured container format (Title, Hook, Script, Creatives, Schedule). | High |
| **FR-05** | Interactive Container UI | Users can review generated assets across clean, tabbed interfaces (Script, Visuals, Schedule) and edit parameters inline. | Medium |
| **FR-06** | Drafted Synchronization | A single tap on "Schedule Draft" pushes the structured package directly into the Drafted workspace database with designated dates and times. | High |

### **5\. Non-Functional & Mobile Computing Requirements**

* **Performance & Latency:** End-to-end processing time from finishing a voice recording to rendering the structured container on the device must be under **5.0 seconds** in \$\\ge 90\\%\$ of test trials.

* **Network Reliability:** Audio packet transmission success rate must be \$\\ge 98\\%\$ under standard 4G LTE and Wi-Fi conditions. Session state must be preserved during network handovers.

* **Security:** All data payloads in transit must be encrypted using TLS 1.3 over HTTPS. Authentication tokens must be securely stored in Android EncryptedSharedPreferences.

* **Power Efficiency:** Radio usage optimized via batch uploading and idle states to minimize battery drain on mobile devices.

### **6\. Proposed System Architecture & Data Flow**

#### **6.1 System Topology Diagram (Landscape Mermaid)**

Code snippet

```
graph LR
    %% Client Layer
    subgraph Client [Mobile Client - Kotlin]
        UI[UI & Navigation] -->|Records| AudioRec[Audio Recorder]
        AudioRec -->|Saves| LocalCache[Room Cache]
    end

    LocalCache -->|HTTPS Payload| WiFi[Network / Wi-Fi / 5G]

    subgraph Cloud [Backend & AI]
        WiFi -->|Handoff| API[FastAPI Gateway]
        API -->|Transcribes| Transcriber[Whisper ASR]
        Transcriber -->|Parses| Parser[AI Parser Engine]
        Parser -->|Stores JSON| DB[(Supabase DB)]
    end

    DB -->|Syncs Final Container| Drafted[Drafted Workspace]

    %% Styling
    classDef client fill:#f9f,stroke:#333,stroke-width:2px;
    classDef net fill:#bbf,stroke:#333,stroke-width:2px;
    classDef cloud fill:#bfb,stroke:#333,stroke-width:2px;
    classDef ext fill:#ff9,stroke:#333,stroke-width:2px;
    
    class UI,AudioRec,LocalCache client;
    class WiFi net;
    class API,Transcriber,Parser,DB cloud;
    class Drafted ext;
```

#### **6.2 Technology Stack Breakdown**

| Layer | Component | Classification | Justification |
| :---- | :---- | :---- | :---- |
| **Mobile Frontend** | Kotlin (Jetpack Compose / Android SDK) | Custom-Built | Provides modern declarative UI and native access to MediaRecorder APIs. |
| **Local Storage** | Android Room Database | Existing / Reused | Ensures robust local caching and offline-first queue management. |
| **Network Link** | Wi-Fi (IEEE 802.11) & 4G/5G Cellular | Existing / Reused | Handles outdoor mobility and high-speed indoor batch syncing. |
| **Backend API** | Python / FastAPI | Custom-Built | High-performance asynchronous endpoint handling. |
| **AI / ASR Engine** | OpenAI Whisper & GPT-4o mini | Existing / Reused | Industry-standard transcription and schema-constrained JSON parsing. |
| **Database & Sync** | Supabase (PostgreSQL) | Existing / Reused | Centralized cloud repository connecting Drafty to Drafted. |

### **7\. Evaluation & Success Metrics**

1. **Processing Latency:** Tracked via client performance logs; target is $<5.0$ seconds for \$90\\%\$ of test runs.

2. **Parsing Accuracy:** Manual evaluation of generated content containers against original voice inputs; target is \$\\ge 90\\%\$ semantic relevance without hallucination.

3. **Network Resilience:** Simulated network switching (Wi-Fi to Cellular mid-upload) across 10 trials; target is \$100\\%\$ recovery without app failure.

