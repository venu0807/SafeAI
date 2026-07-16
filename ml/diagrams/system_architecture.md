# SafeGuard AI - Complete Mermaid Diagrams for All 21 Figures
# Each diagram has a title line showing its Figure number and name.
# Export each individually from https://mermaid.live or VS Code.

---

## FIGURE 3.1: System Architecture Diagram

```mermaid
graph TB
    subgraph P["Presentation Layer"]
        A1["Android App<br/>(Jetpack Compose)"]
        A2["Web Dashboard<br/>(Next.js + React)"]
    end
    
    subgraph S["Service Layer"]
        B1["ThreatDetectionService<br/>(Foreground Service)"]
        B2["EmergencyResponseService<br/>(Multi-Channel Dispatch)"]
        B3["SafeTimerService<br/>(Countdown Checks)"]
    end
    
    subgraph I["Intelligence Layer"]
        C1["AudioClassifier<br/>(TFLite CNN)"]
        C2["MFCCExtractor<br/>(Signal Processing)"]
        C3["YOLOv8 Detector<br/>(TFLite)"]
        C4["SafeWordHelper<br/>(Offline Speech)"]
    end
    
    subgraph D["Data Layer"]
        E1["Room DB<br/>(Local SQLite)"]
        E2["SharedPrefs<br/>(Encrypted)"]
        E3["Firebase RTDB<br/>(Cloud Sync)"]
    end
    
    A1 --> B1
    A2 --> E3
    B1 --> C1 --> C2
    B1 --> C3
    B1 --> C4
    B1 --> B2
    B1 --> B3
    B2 --> E3
    B1 --> E1
    B1 --> E2
```

---

## FIGURE 3.2: Context-Level DFD (Level 0)

```mermaid
graph LR
    U["User"] --> S["SafeGuard AI<br/>System"]
    E["Environment<br/>(Audio/Visual)"] --> S
    S --> C["Emergency<br/>Contacts"]
    S --> F["Firebase<br/>Cloud"]
    S --> W["Web<br/>Dashboard"]
    S --> D["Flash<br/>Deterrent"]
```

---

## FIGURE 3.3: Level 1 DFD - Threat Detection Subsystem

```mermaid
flowchart LR
    MIC["Microphone"] --> P1["1.0<br/>VAD"]
    P1 --> P2["2.0<br/>MFCC Extraction"]
    P2 --> P3["3.0<br/>CNN Classification"]
    P3 --> P4["4.0<br/>Proximity Check"]
    P4 --> P5["5.0<br/>YOLOv8 Verification"]
    P5 --> P6["6.0<br/>Emergency Dispatch"]
    P4 --> P7["7.0<br/>SOS Buffer"]
    P7 --> P6
    DS["Data Store:<br/>Settings"] --> P3
```

---

## FIGURE 3.4: Level 2 DFD - Emergency Response Subsystem

```mermaid
flowchart TD
    TRIG["Emergency Trigger"] --> P1["1.0<br/>GPS Location<br/>Acquisition"]
    TRIG --> P2["2.0<br/>Flash Activation"]
    P1 --> P3["3.0<br/>SMS Dispatch"]
    P1 --> P4["4.0<br/>Firebase Push"]
    P1 --> P5["5.0<br/>Auto Phone Call"]
    TRIG --> P6["6.0<br/>Encrypted Audio<br/>Recording"]
    P3 --> EC["Emergency<br/>Contacts"]
    P4 --> FB["Firebase<br/>Cloud"]
    P6 --> DS["Data Store:<br/>Incident Files"]
```

---

## FIGURE 3.5: UML Class Diagram

```mermaid
classDiagram
    class ThreatDetectionService {
        -AudioClassifier classifier
        -MFCCExtractor extractor
        -YoloV8Detector detector
        -SafeWordHelper safeWord
        -EmergencyResponseService emergency
        +startMonitoring()
        +stopMonitoring()
        -onAudioChunk(byte[] data)
        -processThreat(ClassificationResult result)
    }
    
    class AudioClassifier {
        -Interpreter tflite
        +classify(float[][] mfcc) ClassificationResult
        -preprocessInput(float[][] mfcc)
        -postprocessOutput(float[] output)
    }
    
    class MFCCExtractor {
        +extract(short[] audio, int sampleRate) float[][]
        -preEmphasis(short[] audio)
        -framing(short[] audio)
        -applyMelFilterBank(float[][] spectrum)
        -applyDCT(float[][] melEnergy)
    }
    
    class ClassificationResult {
        +float normalProbability
        +float distressProbability
        +int predictedClass
        +float confidence
    }
    
    class EmergencyResponseService {
        -FusedLocationProviderClient locationClient
        -SmsManager smsManager
        +dispatchSOS(String message)
        -sendSMS(String phone, String message)
        -pushToFirebase(SOSAlert alert)
        -initiatePhoneCall(String phone)
        -activateFlash()
    }
    
    class YoloV8Detector {
        -Interpreter tflite
        +detect(Bitmap image) List~Detection~
        -preprocessImage(Bitmap image)
        -applyNMS(List~Detection~ detections)
    }
    
    ThreatDetectionService --> AudioClassifier
    ThreatDetectionService --> MFCCExtractor
    ThreatDetectionService --> YoloV8Detector
    ThreatDetectionService --> SafeWordHelper
    ThreatDetectionService --> EmergencyResponseService
    AudioClassifier ..> ClassificationResult
```

---

## FIGURE 3.6: UML Sequence Diagram - Threat Detection Flow

```mermaid
sequenceDiagram
    participant MIC as Microphone
    participant TDS as ThreatDetection<br/>Service
    participant VAD as Voice Activity<br/>Detector
    participant MFCC as MFCC<br/>Extractor
    participant CNN as CNN<br/>Classifier
    participant PROX as Proximity<br/>Sensor
    participant CAM as Camera<br/>Helper
    participant YOLO as YOLOv8<br/>Detector
    participant SOS as SOS Buffer
    participant EMS as Emergency<br/>Response
    
    MIC->>TDS: 1-sec audio chunk
    TDS->>VAD: check RMS energy
    VAD-->>TDS: RMS > 500
    TDS->>MFCC: extract features
    MFCC-->>TDS: 100x40 matrix
    TDS->>CNN: classify()
    CNN-->>TDS: distressProb > 0.45
    
    Note over TDS: Consecutive detection check
    
    TDS->>PROX: isPhoneAccessible?
    PROX-->>TDS: Yes
    
    alt Phone accessible
        TDS->>CAM: captureFrame()
        CAM->>YOLO: detect()
        YOLO-->>CAM: person/weapon found?
        CAM-->>TDS: Visual threat confirmed
    else Phone not accessible
        TDS->>SOS: startBuffer(15s)
        SOS-->>TDS: Safe word not detected
    end
    
    TDS->>EMS: dispatchEmergency()
```

---

## FIGURE 3.7: UML Activity Diagram - Emergency Response

```mermaid
stateDiagram-v2
    [*] --> LocationCheck
    LocationCheck --> GPSAcquisition : Permission granted
    LocationCheck --> SkipGPS : Permission denied
    GPSAcquisition --> BuildSOS : Location acquired
    GPSAcquisition --> BuildSOS : Fallback to last known
    SkipGPS --> BuildSOS
    
    state BuildSOS {
        [*] --> ConstructMessage
        ConstructMessage --> AddCoordinates
        AddCoordinates --> AddMapsLink
        AddMapsLink --> [*]
    }
    
    BuildSOS --> ParallelDispatch
    
    state ParallelDispatch {
        SMS_Contact1 --> Complete
        SMS_Contact2 --> Complete
        FirebasePush --> Complete
        FlashActivation --> Complete
        AutoCall --> Complete
    }
    
    ParallelDispatch --> [*]
```

---

## FIGURE 3.8: Use Case Diagram - User Interactions

```mermaid
graph LR
    U("User") --> A["Sign In<br/>(Google / Guest)"]
    U --> B["Toggle Protection<br/>(On / Off)"]
    U --> C["Press Panic Button<br/>(Long Press)"]
    U --> D["Manage Contacts<br/>(Add / Edit / Delete)"]
    U --> E["Configure Settings<br/>(Sensitivity / Auto-Call)"]
    U --> F["View Incident History<br/>(Play / Delete)"]
    U --> G["Enable Incognito Mode<br/>(Calculator Disguise)"]
```

---

## FIGURE 3.9: Use Case Diagram - System Operations

```mermaid
graph LR
    S("System<br/>(Autonomous)") --> A["Monitor Ambient Audio<br/>(16kHz, 1-sec chunks)"]
    S --> B["Classify Audio Using CNN<br/>(MFCC + TFLite)"]
    S --> C["Detect Proximity State<br/>(Covered / Accessible)"]
    S --> D["Capture Camera Frame<br/>(Silent YOLOv8)"]
    S --> E["Dispatch Emergency Alerts<br/>(SMS + GPS + Firebase)"]
    S --> F["Record Encrypted Evidence<br/>(AES-256 Audio)"]
    S --> G["Auto-Restart After Boot<br/>(Boot Receiver)"]
```

---

## FIGURE 3.10: Main Application Flowchart

```mermaid
flowchart TD
    START["App Launch"] --> SPLASH["Splash Screen"]
    SPLASH --> ONBOARD{"Onboarding<br/>Complete?"}
    ONBOARD -->|No| AUTH["Login Screen<br/>(Google / Guest)"]
    AUTH --> ONBOARDING["Onboarding Form<br/>(Name, Address, Contact)"]
    ONBOARDING --> DASHBOARD
    ONBOARD -->|Yes| DASHBOARD["Main Dashboard"]
    
    DASHBOARD --> PROTECT["Toggle Protection"]
    DASHBOARD --> PANIC["Press Panic Button"]
    DASHBOARD --> SETTINGS["Settings Screen"]
    DASHBOARD --> CONTACTS["Emergency Contacts"]
    DASHBOARD --> HISTORY["Incident History"]
    
    PROTECT -->|On| THREAT_SERVICE["Start ThreatDetection<br/>Foreground Service"]
    PROTECT -->|Off| STOP_SERVICE["Stop Foreground<br/>Service"]
```

---

## FIGURE 3.11: Audio Classification Pipeline Flowchart

```mermaid
flowchart TD
    AUDIO["1-sec Audio Chunk<br/>16,000 samples @ 16kHz"] --> RMS{"RMS Energy<br/>> 500?"}
    RMS -->|No| DISCARD["Discard -<br/>Silence/Noise"]
    RMS -->|Yes| PREEMP["Pre-emphasis<br/>Filter (α = 0.97)"]
    PREEMP --> FRAME["Framing<br/>25ms windows, 10ms hop"]
    FRAME --> WINDOW["Hamming Windowing"]
    WINDOW --> FFT["512-point FFT"]
    FFT --> MEL["40-channel<br/>Mel Filter Bank"]
    MEL --> LOG["Logarithmic<br/>Compression"]
    LOG --> DCT["Discrete Cosine<br/>Transform"]
    DCT --> MFCC["100 x 40 MFCC<br/>Feature Matrix"]
    MFCC --> CNN["TensorFlow Lite<br/>CNN Interpreter"]
    CNN --> THREAT{"Distress Prob.<br/>> Threshold?"}
    THREAT -->|No| NORMAL["Classify as<br/>Normal"]
    THREAT -->|Yes| CONSEC{"Consecutive<br/>Detections ≥ 2?"}
    CONSEC -->|No| COUNT["Increment<br/>Counter"]
    CONSEC -->|Yes| CONFIRM["Threat Confirmed"]
```

---

## FIGURE 3.12: Emergency Dispatch Protocol Flowchart

```mermaid
flowchart TD
    CONFIRM["Threat Confirmed"] --> WAKE["Acquire 2-min<br/>PARTIAL_WAKE_LOCK"]
    WAKE --> SERVICE["Start Emergency<br/>Foreground Service"]
    SERVICE --> GPS{"Request High-Accuracy<br/>GPS Location"}
    GPS -->|Success| COORDS["Get Coordinates +<br/>Reverse Geocode"]
    GPS -->|Timeout| LAST["Fallback to<br/>Last Known Location"]
    GPS -->|Failed| NOGPS["Location: Unavailable"]
    COORDS --> BUILD["Build SOS Message<br/>(Name + Coords + Maps Link)"]
    LAST --> BUILD
    NOGPS --> BUILD
    
    BUILD --> PARALLEL
    
    subgraph PARALLEL ["Parallel Dispatch"]
        SMS1["SMS to Contact 1<br/>(SendTextMessage)"]
        SMS2["SMS to Contact 2<br/>(SendTextMessage)"]
        FIREBASE["Push to Firebase<br/>RTDB Node"]
        FLASH["Camera Flash<br/>Torch (10 sec)"]
        PHONE["Auto Phone Call<br/>(If Enabled)"]
    end
    
    PARALLEL --> DONE["Emergency<br/>Dispatched ✓"]
```

---

## FIGURE 3.13: Entity-Relationship Diagram

```mermaid
erDiagram
    USER ||--o{ EMERGENCY_CONTACT : has
    USER ||--o{ THREAT_EVENT : generates
    USER ||--o{ SOS_ALERT : dispatches
    USER ||--|| SETTINGS : configures
    THREAT_EVENT ||--o| INCIDENT_RECORDING : has
    
    USER {
        string uid PK
        string displayName
        string email
        string address
        boolean onboardingComplete
    }
    
    EMERGENCY_CONTACT {
        int id PK
        string contactName
        string contactPhone
        string relationship
    }
    
    THREAT_EVENT {
        int id PK
        bigint timestamp
        float confidence
        float distressProbability
        float latitude
        float longitude
    }
    
    SOS_ALERT {
        string alertId PK
        bigint timestamp
        float latitude
        float longitude
        string address
        string userName
    }
    
    INCIDENT_RECORDING {
        string filename PK
        bigint timestamp
        int fileSizeBytes
        string encryptionAlgo
    }
    
    SETTINGS {
        string key PK
        string value
    }
```

---

## FIGURE 4.1: Android Login and Onboarding Screens

```mermaid
graph LR
    subgraph LEFT["Google Sign-In Screen"]
        L1["SafeGuard AI<br/>Logo"]
        L2["Sign in with Google<br/>Button"]
        L3["Continue as Guest<br/>Link"]
    end
    
    subgraph RIGHT["Onboarding Form"]
        R1["Welcome! Set up<br/>your profile"]
        R2["[Name Field]"]
        R3["[Address Field]"]
        R4["[Emergency Contact<br/>Name + Phone]"]
        R5["[Submit Button]"]
    end
    
    LEFT -->|"After Auth"| RIGHT
```

---

## FIGURE 4.2: Main Dashboard Screen

```mermaid
graph TB
    subgraph DASHBOARD["Main Dashboard (Dark Glassmorphism)"]
        HEADER["🔴 Protection Active<br/>Monitoring Threats..."]
        METRICS["24hr Events: 3 | Threats: 1 | GPS: On"]
        PANIC["🆘<br/>PANIC BUTTON<br/>(Long Press)"]
        TOGGLE["[Protection Toggle]"]
        NAV["📊 Dashboard | 📋 History | 👤 Contacts | ⚙️ Settings"]
    end
```

---

## FIGURE 4.3: Camera Capture and YOLOv8 Verification View

```mermaid
graph TB
    subgraph CAMERA["Camera Capture Overlay"]
        FRAME["[Silent Camera Frame Capture]"]
        BOXES["Person: 92%<br/>[Bounding Box]"]
        STATUS["Threat Verification:<br/>✅ Visual Confirmed"]
    end
```

---

## FIGURE 4.4: SOS Alert and Emergency Dispatch Screens

```mermaid
graph LR
    subgraph LEFT2["SOS Buffer Countdown"]
        BUF_TITLE["⚠️ SOS Buffer Active"]
        COUNTDOWN["15"]
        CANCEL["[CANCEL]"]
        SAFEWORD["Say Safe Word to Cancel"]
    end
    
    subgraph RIGHT2["Emergency Dispatch"]
        DISP_TITLE["✅ Emergency Dispatched"]
        SMS_SENT["📨 SMS Sent to 2 Contacts"]
        GPS_SHOWN["📍 GPS: 28.45°N, 77.50°E"]
        CALL_STATUS["📞 Calling Primary Contact..."]
        FIREBASE_OK["☁️ Firebase Alert Pushed"]
    end
    
    LEFT2 -->|"Timeout / No Cancel"| RIGHT2
```

---

## FIGURE 4.5: Incident History Screen

```mermaid
graph TB
    subgraph HISTORY["Incident History"]
        LIST["📅 2025-10-15 14:32<br/>Confidence: 92% | Location: Sector 62<br/>[▶ Play] [🗑 Delete]"]
        LIST2["📅 2025-10-14 09:15<br/>Confidence: 78% | Location: Sector 18<br/>[▶ Play] [🗑 Delete]"]
        LIST3["📅 2025-10-12 22:40<br/>Confidence: 95% | Location: Home<br/>[▶ Play] [🗑 Delete]"]
    end
```

---

## FIGURE 4.6: Calculator Incognito Mode Screen

```mermaid
graph TB
    subgraph CALC["Calculator App (Disguised)"]
        DISPLAY["9911"]
        ROW1["7  8  9"]
        ROW2["4  5  6"]
        ROW3["1  2  3"]
        ROW4["0  .  ="]
        NOTE["Enter Duress PIN + =<br/>to unlock SafeGuard"]
    end
```

---

## FIGURE 4.7: Safe Timer Setup Screen

```mermaid
graph TB
    subgraph TIMER["Safe Timer Configuration"]
        TITLE["⏱ Safe Timer"]
        DURATION["Duration: 30 min"]
        PLUS["[+]  [-]"]
        START_BTN["[START TIMER]"]
        STATUS["🔔 Timer Running<br/>Check-in in 30 min"]
        CANCEL_BTN["[CANCEL TIMER]"]
    end
```

---

## FIGURE 4.8: Web Dashboard Interface

```mermaid
graph TB
    subgraph WEB_DASH["Web Dashboard - Live Monitoring"]
        LOGO["SafeGuard AI | Live Monitor"]
        METRICS_WEB["📊 Events Today: 12 | Active Threats: 2 | GPS: Online"]
        
        subgraph FEED["Live Alert Feed"]
            ALERT1["🔴 HIGH CONFIDENCE - 92%<br/>📍 Sector 62, Noida | 🕐 14:32:15"]
            ALERT2["🟡 MEDIUM CONFIDENCE - 78%<br/>📍 Sector 18, Noida | 🕐 09:15:22"]
            ALERT3["🔴 HIGH CONFIDENCE - 95%<br/>📍 Home Address | 🕐 22:40:01"]
        end
        
        TABS["📊 Dashboard | 📋 History | 👤 Contacts | ⚙️ Settings"]
    end
```

---

## How to Export Each Image

1. Go to **[https://mermaid.live](https://mermaid.live)**
2. Copy each individual diagram code (between ```mermaid and ```)
3. Paste into the left panel
4. Set export width to **1000px** for landscape / **800px** for portrait
5. Export as PNG and save with the figure name, e.g. `figure_3_1_system_architecture.png`
6. Paste into the PDF manually at the corresponding location

**File naming suggestion for all 21 PNGs:**
```
figure_3_1_system_architecture.png
figure_3_2_context_dfd.png
figure_3_3_level1_dfd.png
figure_3_4_level2_dfd.png
figure_3_5_uml_class.png
figure_3_6_uml_sequence.png
figure_3_7_uml_activity.png
figure_3_8_use_case_user.png
figure_3_9_use_case_system.png
figure_3_10_flow_app.png
figure_3_11_flow_audio.png
figure_3_12_flow_emergency.png
figure_3_13_er_diagram.png
figure_4_1_login_onboarding.png
figure_4_2_dashboard.png
figure_4_3_camera_yolo.png
figure_4_4_sos_emergency.png
figure_4_5_incident_history.png
figure_4_6_calculator.png
figure_4_7_safe_timer.png
figure_4_8_web_dashboard.png
```
