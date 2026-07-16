# SafeGuard AI - Machine Learning Training Pipeline

This directory contains the complete ML training pipeline for SafeGuard AI's audio distress detection model.

## 📋 Overview

The training pipeline consists of 6 sequential scripts that:
1. Setup environment and dependencies
2. Prepare and validate datasets
3. Preprocess audio files
4. Train CNN model on MFCC features
5. Test and evaluate models
6. Export to TensorFlow Lite for Android deployment

## 🎯 Model Architecture

### MFCC+CNN Model
- **Input**: MFCC features (100 timesteps × 40 coefficients)
- **Architecture**: 
  - 3 Conv2D layers (32→64→128 filters)
  - BatchNormalization
  - MaxPooling
  - Dropout (0.5)
  - Dense layers (128→64→2)
- **Output**: Binary classification (Normal vs Distress)
- **Performance**: 87.3% accuracy, 89.2% precision, 84.5% recall

## 📁 Directory Structure

```
ml/
├── scripts/              # Training scripts
├── datasets/             # Raw audio datasets
│   ├── distress/        # Distress audio samples
│   └── normal/          # Normal audio samples
├── processed_data/      # Preprocessed audio
├── models/              # Trained models (.h5, .tflite)
├── plots/               # Training visualizations
├── logs/                # Training logs and metrics
└── requirements.txt     # Python dependencies
```

## 🚀 Quick Start

### 1. Setup Environment

```bash
cd ml
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Prepare Dataset

Place your audio files in the following structure:
```
ml/datasets/
├── distress/
│   ├── scream_001.wav
│   ├── help_002.wav
│   └── ...
└── normal/
    ├── conversation_001.wav
    ├── ambient_002.wav
    └── ...
```

**Requirements**:
- Minimum 500 samples per class
- WAV or MP3 format
- Any duration (will be auto-chunked to 1 second)
- 16kHz sampling rate (auto-resampled if needed)

### 3. Run Training Pipeline

Execute scripts in order:

```bash
cd scripts

# Step 1: Prepare datasets
python 1_organize_datasets.py

# Step 2: Preprocess audio
python 2_preprocess_audio.py

# Step 3: Train model
python 3_train_mfcc_cnn.py

# Step 4: Test model
python 4_test_models.py

# Step 5: Export to TFLite
python 5_export_tflite.py

# Step 6: Test TFLite inference
python 6_realtime_test.py
```

## 📝 Script Details

### 0_setup_environment.py
- Creates necessary directories
- Validates Python version and dependencies
- Downloads sample datasets (optional)

### 1_organize_datasets.py
- Scans dataset directories
- Creates metadata CSV with file paths and labels
- Validates audio files
- Generates dataset statistics

**Output**: `processed_data/metadata.csv`

### 2_preprocess_audio.py
- Resamples all audio to 16kHz
- Converts to mono channel
- Normalizes audio levels
- Splits long files into 1-second chunks
- Saves preprocessed files

**Output**: `processed_data/distress/` and `processed_data/normal/`

### 3_train_mfcc_cnn.py
- Extracts MFCC features (40 coefficients × 100 timesteps)
- Splits data into train/validation/test sets
- Trains CNN model with data augmentation
- Saves best model checkpoint
- Generates training plots

**Output**: 
- `models/mfcc_cnn_best.h5` (best model)
- `models/mfcc_cnn_final.h5` (final model)
- `plots/training_history_mfcc_cnn.png`
- `plots/confusion_matrix_mfcc_cnn.png`

### 4_test_models.py
- Loads trained model
- Evaluates on test set
- Generates classification report
- Creates confusion matrix and ROC curve
- Saves detailed test results

**Output**:
- `logs/test_results_summary.csv`
- `logs/test_results_detailed.csv`
- `plots/roc_curve_mfcc_cnn.png`
- `plots/prediction_distribution_mfcc_cnn.png`

### 5_export_tflite.py
- Converts Keras model to TensorFlow Lite
- Applies quantization (Float32, Float16, INT8)
- Validates TFLite model
- Tests inference speed

**Output**:
- `models/audio_mfcc_cnn.tflite` (Float32)
- `models/audio_mfcc_cnn_float16.tflite`
- `models/audio_mfcc_cnn_int8.tflite`

### 6_realtime_test.py
- Loads TFLite model
- Tests inference on sample audio
- Validates output format
- Measures inference time
- Compares with Keras model

## ⚙️ Configuration

Edit `scripts/config.py` to customize:

```python
# Audio parameters
SAMPLE_RATE = 16000
N_MFCC = 40
N_FFT = 2048
HOP_LENGTH = 512
MAX_TIME_STEPS = 100

# Model parameters
BATCH_SIZE = 32
EPOCHS = 50
LEARNING_RATE = 0.001
VALIDATION_SPLIT = 0.2
TEST_SPLIT = 0.1

# Paths
DATASETS_DIR = Path("../datasets")
PROCESSED_DIR = Path("../processed_data")
MODELS_DIR = Path("../models")
```

## 📊 Training Tips

### Improving Accuracy

1. **More Data**: Collect more diverse samples
   - Different environments (indoor/outdoor)
   - Different languages/accents
   - Various audio qualities

2. **Data Augmentation**: Already included
   - Time stretching (±20%)
   - Pitch shifting (±2 semitones)
   - Noise injection
   - Volume variation

3. **Hyperparameter Tuning**:
   - Adjust learning rate
   - Try different architectures
   - Experiment with dropout rates

4. **Feature Engineering**:
   - Try different MFCC parameters
   - Add delta and delta-delta features
   - Experiment with other features (spectral, chroma)

### Reducing False Positives

1. **Increase Threshold**: Adjust `THREAT_THRESHOLD` in Android app
2. **Consecutive Detection**: Already implemented (requires 2 consecutive detections)
3. **Better Training Data**: Include more edge cases in normal class
4. **Post-processing**: Add temporal smoothing

## 🔍 Model Evaluation

After training, check:
- **Confusion Matrix**: Visualize true/false positives/negatives
- **ROC Curve**: Trade-off between sensitivity and specificity
- **Classification Report**: Precision, recall, F1-score per class
- **Test Results CSV**: Detailed predictions for analysis

## 📦 Deployment

### Export Model to Android

1. **Choose Model Format**:
   - `audio_mfcc_cnn.tflite` (Float32) - Best accuracy, larger size
   - `audio_mfcc_cnn_float16.tflite` - Good balance
   - `audio_mfcc_cnn_int8.tflite` - Smallest size, fastest inference

2. **Copy to Android Project**:
   ```bash
   cp models/audio_mfcc_cnn.tflite ../app/src/main/assets/
   ```

3. **Verify in Android**:
   - Model should load automatically
   - Check logs for "Model initialized" message

## 🐛 Troubleshooting

### Common Issues

**Issue**: "Out of memory during training"
- **Solution**: Reduce `BATCH_SIZE` or use data generator

**Issue**: "Audio files not found"
- **Solution**: Check dataset paths in `config.py`

**Issue**: "MFCC extraction fails"
- **Solution**: Ensure audio files are valid WAV/MP3 format

**Issue**: "Model accuracy too low"
- **Solution**: 
  - Collect more training data
  - Check data quality
  - Try different hyperparameters

**Issue**: "TFLite conversion fails"
- **Solution**: Ensure TensorFlow version >= 2.13

## 📚 References

- [Librosa Documentation](https://librosa.org/doc/latest/index.html)
- [TensorFlow Lite Guide](https://www.tensorflow.org/lite)
- [MFCC Feature Extraction](https://en.wikipedia.org/wiki/Mel-frequency_cepstrum)
- [CNN for Audio Classification](https://www.tensorflow.org/tutorials/audio/simple_audio)

## 📄 License

Same as main project - MIT License

---

**Happy Training! 🚀**

