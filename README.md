# 🛡️ SafeguardAI — On-Device Threat Detection

**89% accuracy** — TensorFlow Lite on Android. Audio-based violence detection running entirely on-device. No internet required.

## Architecture

```
SafeguardAI/
├── ml/                          # ML pipeline (Python)
│   ├── config.py                # Training configuration
│   ├── preprocessing_utils.py   # Audio preprocessing (MFCC, spectral subtraction)
│   ├── scripts/
│   │   ├── 1_organize_datasets.py
│   │   ├── 2_preprocess_audio.py
│   │   ├── 3_train_mfcc_cnn.py          # MFCC CNN training
│   │   ├── 3_train_wav2vec2_bert.py     # Wav2Vec2 alternative
│   │   ├── 4_test_models.py
│   │   ├── 5_export_tflite.py           # Export to TFLite
│   │   ├── 6_realtime_test.py
│   │   └── 7_threshold_analysis.py
│   └── models/                  # Pre-trained TFLite models
│       ├── audio_mfcc_cnn_int8.tflite
│       ├── audio_mfcc_cnn_fp32.tflite
│       ├── audio_wav2vec2_final.tflite
│       └── ...
│
├── android/                     # Android app (Java + Compose)
│   ├── gradlew                  # Gradle wrapper
│   ├── build_apk.sh             # One-click APK build
│   └── app/src/main/
│       ├── assets/              # TFLite models bundled with app
│       ├── java/com/example/android/
│       │   ├── SafeGuardApp.java
│       │   ├── ml/              # TFLite inference (AudioClassifier, YOLO)
│       │   ├── models/          # Data models (ThreatEvent, EmergencyContact)
│       │   ├── db/              # Room database
│       │   └── receivers/       # AlarmReceiver, BootReceiver
│       └── res/layout/          # Activity layouts
│
└── web/                         # Web dashboard (optional)
```

## Build APK

```bash
cd android
export ANDROID_HOME=~/Android/Sdk
./build_apk.sh
```

APK output: `android/app/build/outputs/apk/release/app-release.apk`

## ML Pipeline

| Model | Accuracy | Size | Use Case |
|-------|----------|------|----------|
| MFCC CNN (int8) | 87% | 1.2 MB | Real-time on-device |
| MFCC CNN (fp32) | 89% | 4.5 MB | Accurate detection |
| Wav2Vec2 | 91% | 45 MB | High-accuracy when battery allows |

## Prerequisites for APK Build

- **JDK 17+** (`sudo apt install openjdk-17-jdk`)
- **Android SDK** with platform 34 + build-tools
- Set `ANDROID_HOME` environment variable

## Tech Stack

- **ML:** Python, TensorFlow, scikit-learn, librosa
- **On-Device:** TensorFlow Lite, Android (Java + Jetpack Compose)
- **Real-time:** CameraX, Coroutines, Room DB
- **Safety:** SOS timer, emergency contacts, location sharing
