"""
SafeGuard AI - ML Configuration
All hyperparameters and constants in one place
"""

import os
import re

# ============================================================================
# PROJECT PATHS
# ============================================================================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, 'data')
RAW_DATA_DIR = os.path.join(BASE_DIR, 'processed_data')  # Point to actual audio location
PROCESSED_DATA_DIR = os.path.join(BASE_DIR, 'processed_data')
AUGMENTED_DATA_DIR = os.path.join(BASE_DIR, 'processed_data')
MODELS_DIR = os.path.join(BASE_DIR, 'models')
RESULTS_DIR = os.path.join(BASE_DIR, 'results')

# Create directories if they don't exist
for directory in [DATA_DIR, PROCESSED_DATA_DIR, MODELS_DIR, RESULTS_DIR]:
    os.makedirs(directory, exist_ok=True)

# Subdirectories for raw data (point to actual audio location)
DISTRESS_DIR = os.path.join(BASE_DIR, 'processed_data', 'distress')
NORMAL_DIR = os.path.join(BASE_DIR, 'processed_data', 'normal')
os.makedirs(DISTRESS_DIR, exist_ok=True)
os.makedirs(NORMAL_DIR, exist_ok=True)

# ============================================================================
# AUDIO PARAMETERS (MUST MATCH ANDROID MFCCExtractor.java)
# ============================================================================
SAMPLE_RATE = 16000              # 16kHz sampling rate
DURATION = 1.0                   # 1 second audio chunks
N_SAMPLES = int(SAMPLE_RATE * DURATION)  # 16,000 samples

# MFCC Parameters
N_MFCC = 40                      # Number of MFCC coefficients
N_FFT = 2048                     # FFT window size
HOP_LENGTH = 512                 # Hop length for STFT
N_MELS = 128                     # Number of Mel filter banks
MAX_TIME_STEPS = 100             # Fixed sequence length (time dimension)

# Pre-emphasis
PRE_EMPHASIS = 0.97              # Pre-emphasis coefficient (standard default)

# Noise Reduction (Spectral Subtraction - per research paper)
# Paper uses spectral subtraction (not Wiener filter) for stationary noise removal
USE_SPECTRAL_SUBTRACTION = True  # Enable spectral subtraction noise reduction
FFT_SIZE_SPECSUB = 1024          # FFT size for spectral subtraction
SPECTRAL_FLOOR = 0.01            # Noise floor multiplier to avoid negative values

# Voice Activity Detection (VAD)
VAD_RMS_THRESHOLD = 500.0        # RMS threshold for silence detection
VAD_ENABLED = True               # Enable VAD to skip silence

# ============================================================================
# MODEL HYPERPARAMETERS
# ============================================================================
# CNN Architecture
CNN_FILTERS = [32, 64, 128]      # Convolutional filters per layer
KERNEL_SIZE = (3, 3)             # Kernel size for Conv2D
POOL_SIZE = (2, 2)               # MaxPooling size
DROPOUT_RATE = 0.5               # Dropout for regularization (increased from 0.3 to combat overfitting)
DENSE_UNITS = 128                # Dense layer units

# L2 Regularization
L2_REG = 1e-4                    # L2 weight decay for all kernel weights

# Training Parameters
LEARNING_RATE = 0.0005           # Adam optimizer learning rate (increased from 1e-4 for faster convergence)
BATCH_SIZE = 32                  # Training batch size
EPOCHS = 400                     # Maximum training epochs
EARLY_STOPPING_PATIENCE = 20     # Early stopping patience
VALIDATION_SPLIT = 0.15          # Validation split from training data

# Class weights (for imbalanced datasets)
# SAFETY-CRITICAL: We prioritize distress recall over precision.
# False negatives (missed distress) are FAR worse than false alarms.
# Weights: Normal=1.0, Distress=2.0 → model penalized 2x more for missing distress
# Note: batches are already balanced 50/50 by the generator, so milder weight is sufficient
USE_CLASS_WEIGHTS = True         # Enable class weighting
NORMAL_CLASS_WEIGHT = 1.0        # Weight for normal samples
DISTRESS_CLASS_WEIGHT = 2.0      # Weight for distress samples (safety-critical: high recall)

# Focal Loss (better for imbalanced classification than cross-entropy)
# Down-weights easy examples, focuses on hard-to-classify ones
USE_FOCAL_LOSS = True            # Enable focal loss
FOCAL_LOSS_GAMMA = 2.0           # Focusing parameter (higher = more focus on hard examples)
# alpha is a per-class weight list for focal loss: [normal_weight, distress_weight]
# [0.30, 0.70] gives 70% weight to distress class
FOCAL_LOSS_ALPHA = [0.30, 0.70]  # List of per-class weights

# Training-time augmentation (SpecAugment-style on MFCC features)
# Applied on-the-fly to each batch for better generalization
TRAIN_AUGMENTATION = True        # Enable feature-space augmentation during training
AUG_NOISE_STD = 0.005            # Gaussian noise std for feature augmentation
AUG_TIME_MASK_SIZE = 5           # Max timesteps to mask (SpecAugment)
AUG_FREQ_MASK_SIZE = 2           # Max freq bins to mask (SpecAugment)

# ============================================================================
# DATASET CONFIGURATION
# ============================================================================
# Dataset splits
TRAIN_SPLIT = 0.70               # 70% training
VAL_SPLIT = 0.15                 # 15% validation
TEST_SPLIT = 0.15                # 15% testing

# Data augmentation
AUGMENT_DATA = True              # Enable data augmentation
AUGMENTATION_FACTOR = 2          # Number of augmented samples per original

# Augmentation parameters
PITCH_SHIFT_STEPS = [-2, 2]     # Pitch shift range (semitones)
TIME_STRETCH_RATE = [0.9, 1.1]  # Time stretch range

# ============================================================================
# MODEL FILES
# ============================================================================
MODEL_H5 = os.path.join(MODELS_DIR, 'audio_mfcc_cnn.h5')
MODEL_TFLITE = os.path.join(MODELS_DIR, 'audio_mfcc_cnn.tflite')
MODEL_ARCHITECTURE = os.path.join(MODELS_DIR, 'model_architecture.json')
TRAINING_HISTORY = os.path.join(MODELS_DIR, 'training_history.json')
CLASS_WEIGHTS_FILE = os.path.join(MODELS_DIR, 'class_weights.json')

# ============================================================================
# PROCESSED DATA FILES
# ============================================================================
METADATA_CSV = os.path.join(PROCESSED_DATA_DIR, 'metadata.csv')
MFCC_FEATURES_NPY = os.path.join(PROCESSED_DATA_DIR, 'mfcc_features.npy')
LABELS_NPY = os.path.join(PROCESSED_DATA_DIR, 'labels.npy')
TRAIN_INDICES_NPY = os.path.join(PROCESSED_DATA_DIR, 'train_indices.npy')
VAL_INDICES_NPY = os.path.join(PROCESSED_DATA_DIR, 'val_indices.npy')
TEST_INDICES_NPY = os.path.join(PROCESSED_DATA_DIR, 'test_indices.npy')

# ============================================================================
# RESULTS FILES
# ============================================================================
CONFUSION_MATRIX_PNG = os.path.join(RESULTS_DIR, 'confusion_matrix.png')
ROC_CURVE_PNG = os.path.join(RESULTS_DIR, 'roc_curve.png')
TRAINING_CURVES_PNG = os.path.join(RESULTS_DIR, 'training_curves.png')
CLASSIFICATION_REPORT_TXT = os.path.join(RESULTS_DIR, 'classification_report.txt')
TEST_RESULTS_CSV = os.path.join(RESULTS_DIR, 'test_results.csv')

# ============================================================================
# LABELS
# ============================================================================
LABEL_MAP = {
    'normal': 0,
    'distress': 1
}

LABEL_NAMES = ['Normal', 'Distress']

# ============================================================================
# TFLITE EXPORT CONFIGURATION
# ============================================================================
TFLITE_QUANTIZATION = 'float16'  # Options: 'none', 'float16', 'int8'

# ============================================================================
# LOGGING
# ============================================================================
LOG_LEVEL = 'INFO'               # Logging level
VERBOSE = 1                      # Keras verbose level (0, 1, 2)

# ============================================================================
# PATH UTILITY FUNCTIONS
# ============================================================================

def normalize_path(path_str):
    """
    Convert any path format to proper Windows path.
    Handles:
      - /mnt/d/... -> D:\...
      - /mnt/c/... -> C:\...
      - mixed forward/backslashes
      - Already correct Windows paths (unchanged)
    
    This ensures scripts work on Windows even if metadata was generated under WSL.
    """
    path_str = str(path_str).strip()
    
    # Check if it's a WSL path like /mnt/d/...
    wsl_match = re.match(r'^/mnt/([a-zA-Z])/(.*)', path_str)
    if wsl_match:
        drive_letter = wsl_match.group(1).upper()
        rest = wsl_match.group(2)
        rest = rest.replace('/', '\\')
        windows_path = f"{drive_letter}:\\{rest}"
        return windows_path
    
    # Already a Windows path - just normalize separators
    return path_str.replace('/', '\\')


# ============================================================================
# RANDOM SEED (for reproducibility)
# ============================================================================
RANDOM_SEED = 42

# ============================================================================
# DETECTION THRESHOLD (for Android app)
# ============================================================================
THREAT_THRESHOLD = 0.45          # Optimal threshold: best F1=92.63% at 0.45 from threshold analysis (recall=98.13%)
CONSECUTIVE_DETECTIONS = 2       # Consecutive detections required (paper: 2 consecutive detections needed)

print(f"[OK] Configuration loaded from: {__file__}")
print(f"[DIR] Base directory: {BASE_DIR}")
print(f"[AUDIO] Audio: {SAMPLE_RATE}Hz, {DURATION}s, {N_MFCC} MFCCs, {MAX_TIME_STEPS} timesteps")
print(f"[MODEL] Model: CNN with {CNN_FILTERS} filters")
print(f"[DATASET] Dataset: Train={TRAIN_SPLIT}, Val={VAL_SPLIT}, Test={TEST_SPLIT}")
print(f"[NOISE] Noise reduction: {'Spectral Subtraction' if USE_SPECTRAL_SUBTRACTION else 'None'} (research paper method)")
print(f"[THRESH] Detection threshold: {THREAT_THRESHOLD} (optimal from threshold analysis)")
print(f"[PATH] Path utility: normalize_path() ready (WSL-to-Windows conversion)")
    