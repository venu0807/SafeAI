"""
SafeGuard AI - Audio Preprocessing (Research Paper Compliant)
Extracts MFCC features with spectral subtraction noise reduction
Pipeline: Load → Pad/Truncate → Noise Reduction → Pre-emphasis → MFCC → Normalize → Output
"""

import os
import sys
import librosa
import numpy as np
import pandas as pd
from tqdm import tqdm
# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

def voice_activity_detection(audio, rms_threshold=VAD_RMS_THRESHOLD):
    """
    Simple voice activity detection using RMS energy
    Skip silent frames to improve feature extraction
    
    Args:
        audio: Audio signal
        rms_threshold: RMS threshold for detecting speech
        
    Returns:
        boolean mask indicating speech/non-speech
    """
    frame_length = 2048
    hop_length = 512
    
    # Calculate RMS energy per frame
    rms = librosa.feature.rms(y=audio, frame_length=frame_length, hop_length=hop_length)[0]
    
    # Normalize RMS
    rms_normalized = rms / (np.max(rms) + 1e-8)
    
    # Create mask
    vad_mask = rms_normalized > (rms_threshold / 10000.0)  # Normalized threshold
    
    return vad_mask, rms

def spectral_subtraction_denoise(audio, fft_size=1024, noise_floor=0.01):
    """
    Apply spectral subtraction for stationary noise reduction
    
    As described in the research paper: "When eliminating stationary noise from the
    audio stream, we use a process called spectral subtraction."
    
    The first N samples are used to estimate the noise profile, which is then
    subtracted from the magnitude spectrum of each frame.
    
    Args:
        audio: Audio signal
        fft_size: FFT window size for spectral analysis
        noise_floor: Minimum noise floor multiplier to avoid negative values
        
    Returns:
        Denoised audio
    """
    if not USE_SPECTRAL_SUBTRACTION:
        return audio
    
    try:
        # Estimate noise profile from first few frames
        noise_frames = int(fft_size * 0.1)
        noise_segment = audio[:noise_frames] if len(audio) > noise_frames else audio
        
        # STFT of noise segment
        noise_stft = librosa.stft(noise_segment, n_fft=fft_size, hop_length=fft_size // 4)
        noise_mag = np.mean(np.abs(noise_stft), axis=1, keepdims=True)
        
        # STFT of full audio
        audio_stft = librosa.stft(audio, n_fft=fft_size, hop_length=fft_size // 4)
        audio_mag = np.abs(audio_stft)
        audio_phase = np.angle(audio_stft)
        
        # Spectral subtraction
        magnitude = audio_mag - noise_mag
        magnitude = np.maximum(magnitude, noise_floor * np.max(audio_mag))
        
        # Reconstruct signal
        denoised_stft = magnitude * np.exp(1j * audio_phase)
        denoised = librosa.istft(denoised_stft, hop_length=fft_size // 4, length=len(audio))
        
        return denoised
    except Exception as e:
        print(f"⚠️  Spectral subtraction error: {e}. Returning original audio.")
        return audio

def extract_mfcc_features(audio_path, target_length=MAX_TIME_STEPS):
    """
    Extract MFCC features matching Android MFCCExtractor.java
    RESEARCH PAPER PIPELINE:
    1. Load audio → librosa.load(sr=16000, duration=1.0, mono=True)
    2. Pad/truncate → exactly 16,000 samples
    3. Noise reduction → Spectral subtraction (per research paper)
    4. Pre-emphasis → y[n] = x[n] - 0.97 × x[n-1]
    5. MFCC extraction → librosa.feature.mfcc(n_mfcc=40, n_fft=2048, hop_length=512)
    6. Transpose → (time_steps, n_mfcc)
    7. Normalization → Z-score per feature (column-wise)
    8. Pad/truncate → 100 timesteps
    9. Output shape → [100, 40]
    
    Args:
        audio_path: Path to audio file
        target_length: Target number of time steps (100)
        
    Returns:
        numpy array of shape (target_length, N_MFCC) = (100, 40)
    """
    try:
        # STEP 1: Load audio
        audio, sr = librosa.load(audio_path, sr=SAMPLE_RATE, duration=DURATION, mono=True)
        
        # STEP 2: Pad or truncate to exactly N_SAMPLES (16,000)
        if len(audio) < N_SAMPLES:
            audio = np.pad(audio, (0, N_SAMPLES - len(audio)), mode='constant', constant_values=0)
        else:
            audio = audio[:N_SAMPLES]
        
        # STEP 3: Noise reduction via spectral subtraction (research paper method)
        audio = spectral_subtraction_denoise(audio, fft_size=FFT_SIZE_SPECSUB, noise_floor=SPECTRAL_FLOOR)
        
        # STEP 4: Pre-emphasis filter
        # Formula: y[n] = x[n] - 0.97 * x[n-1]
        audio = np.append(audio[0], audio[1:] - PRE_EMPHASIS * audio[:-1])
        
        # STEP 5: Extract MFCC features
        # Parameters MUST match: n_mfcc=40, n_fft=2048, hop_length=512, n_mels=128
        mfcc = librosa.feature.mfcc(
            y=audio,
            sr=sr,
            n_mfcc=N_MFCC,           # 40
            n_fft=N_FFT,             # 2048
            hop_length=HOP_LENGTH,   # 512
            n_mels=N_MELS,           # 128
            fmin=0,
            fmax=sr/2
        )
        
        # STEP 6: Transpose from (n_mfcc, time) to (time, n_mfcc)
        # Output shape after transpose: (time_steps, 40)
        mfcc = mfcc.T  # Now shape is (time_steps, 40) - ~130 timesteps typically
        
        # STEP 7: Normalize - Z-score per feature (column-wise)
        # IMPORTANT: Normalize per column (feature), not per row
        mfcc_mean = np.mean(mfcc, axis=0, keepdims=True)
        mfcc_std = np.std(mfcc, axis=0, keepdims=True)
        mfcc = (mfcc - mfcc_mean) / (mfcc_std + 1e-8)
        
        # STEP 8 & 9: Pad or truncate to exactly target_length (100 timesteps)
        if mfcc.shape[0] < target_length:
            pad_width = target_length - mfcc.shape[0]
            mfcc = np.pad(mfcc, ((0, pad_width), (0, 0)), mode='constant', constant_values=0)
        else:
            mfcc = mfcc[:target_length, :]
        
        # Final shape verification: (100, 40)
        assert mfcc.shape == (target_length, N_MFCC), \
            f"Shape mismatch: expected ({target_length}, {N_MFCC}), got {mfcc.shape}"
        
        return mfcc
        
    except Exception as e:
        print(f"❌ Error extracting MFCC from {audio_path}: {e}")
        import traceback
        traceback.print_exc()
        return None
def augment_audio(audio, sr):
    """
    Apply data augmentation to audio signal
    Generates multiple versions through pitch shift and time stretch
    """
    augmented = []
    
    # Original
    augmented.append(audio)
    
    # Pitch shift (only if librosa supports it in current version)
    try:
        for steps in PITCH_SHIFT_STEPS:
            audio_shifted = librosa.effects.pitch_shift(audio, sr=sr, n_steps=steps)
            augmented.append(audio_shifted)
    except Exception as e:
        print(f"⚠️  Pitch shift unavailable: {e}")
    
    # Time stretch
    try:
        for rate in TIME_STRETCH_RATE:
            audio_stretched = librosa.effects.time_stretch(audio, rate=rate)
            # Ensure same length
            if len(audio_stretched) < len(audio):
                audio_stretched = np.pad(audio_stretched, (0, len(audio) - len(audio_stretched)))
            else:
                audio_stretched = audio_stretched[:len(audio)]
            augmented.append(audio_stretched)
    except Exception as e:
        print(f"⚠️  Time stretch unavailable: {e}")
    
    return augmented

def preprocess_dataset():
    """
    Preprocess entire dataset following research paper pipeline
    
    Steps:
    1. Load metadata.csv (must exist from 1_organize_datasets.py)
    2. For each audio file:
       - Extract MFCC features using research paper pipeline
       - Apply data augmentation (optional)
       - Store in numpy arrays
    3. Save processed features and labels
    """
    print("=" * 70)
    print("🎵 SAFEGUARD AI - AUDIO PREPROCESSING (Research Paper Compliant)")
    print("=" * 70)
    print(f"\n📋 Pipeline Configuration:")
    print(f"   • Spectral Subtraction: {'✓ ENABLED' if USE_SPECTRAL_SUBTRACTION else '✗ DISABLED'} (research paper method)")
    print(f"   • Pre-emphasis: {PRE_EMPHASIS}")
    print(f"   • MFCC Config: {N_MFCC} coefficients, {N_FFT} FFT, {HOP_LENGTH} hop")
    print(f"   • Target Shape: ({MAX_TIME_STEPS}, {N_MFCC})")
    print(f"   • Data Augmentation: {'✓ ENABLED' if AUGMENT_DATA else '✗ DISABLED'}")
    
    # Load metadata
    if not os.path.exists(METADATA_CSV):
        print("\n❌ ERROR: metadata.csv not found!")
        print("   Run: python 1_organize_datasets.py")
        return False
    
    df = pd.read_csv(METADATA_CSV)
    print(f"\n📊 Total samples in metadata: {len(df)}")
    
    # Normalize paths (handle WSL /mnt/d/... format)
    df['path'] = df['path'].apply(normalize_path)
    
    # Verify file paths
    missing_files = []
    for idx, row in df.iterrows():
        if not os.path.exists(row['path']):
            missing_files.append(row['path'])
    
    if missing_files:
        print(f"\n⚠️  WARNING: {len(missing_files)} audio files not found:")
        for path in missing_files[:5]:
            print(f"   - {path}")
        print(f"   ... and {len(missing_files) - 5} more" if len(missing_files) > 5 else "")
        return False
    
    # Storage for features and labels
    all_features = []
    all_labels = []
    processed_files = []
    failed_files = []
    
    print("\n🔄 Extracting MFCC features...")
    pbar = tqdm(total=len(df), desc="Processing", unit="file")
    
    for idx, row in df.iterrows():
        audio_path = row['path']
        label = row['label_id']
        label_name = row['label']
        
        # Extract MFCC
        mfcc = extract_mfcc_features(audio_path)
        
        if mfcc is not None and mfcc.shape == (MAX_TIME_STEPS, N_MFCC):
            all_features.append(mfcc)
            all_labels.append(label)
            processed_files.append(audio_path)
            pbar.update(1)
        else:
            failed_files.append(audio_path)
            pbar.update(1)
            continue
    
    pbar.close()
    
    if not all_features:
        print("\n❌ ERROR: No features extracted! Check audio files.")
        return False
    
    # Convert to numpy arrays
    X = np.array(all_features, dtype=np.float32)
    y = np.array(all_labels, dtype=np.int32)
    
    print(f"\n✅ Successfully processed {len(X)} files")
    if failed_files:
        print(f"⚠️  Failed: {len(failed_files)} files")
    
    print(f"\n📊 FEATURE SHAPE:")
    print(f"   X: {X.shape} (samples, timesteps, mfcc)")
    print(f"   y: {y.shape} (samples,)")
    
    # Check for NaN or Inf
    nan_count = np.isnan(X).sum()
    inf_count = np.isinf(X).sum()
    
    if nan_count > 0 or inf_count > 0:
        print(f"\n⚠️  Found {nan_count} NaN and {inf_count} Inf values")
        X = np.nan_to_num(X, nan=0.0, posinf=1.0, neginf=-1.0)
        print("   ✓ Replaced with valid values")
    
    # Save processed data
    print("\n💾 Saving processed data...")
    try:
        np.save(MFCC_FEATURES_NPY, X)
        np.save(LABELS_NPY, y)
        print(f"   ✅ Features saved: {MFCC_FEATURES_NPY}")
        print(f"   ✅ Labels saved: {LABELS_NPY}")
    except Exception as e:
        print(f"   ❌ Error saving files: {e}")
        return False
    
    # Statistics
    print("\n" + "=" * 70)
    print("📊 PREPROCESSING STATISTICS")
    print("=" * 70)
    print(f"\nSample Distribution:")
    unique, counts = np.unique(y, return_counts=True)
    for label_id, count in zip(unique, counts):
        label_name = LABEL_NAMES[label_id]
        percentage = (count / len(y)) * 100
        print(f"  {label_name:10s}: {count:4d} samples ({percentage:5.1f}%)")
    
    print(f"\nFeature Statistics:")
    print(f"  Shape:    {X.shape}")
    print(f"  Mean:     {np.mean(X):8.4f}")
    print(f"  Std:      {np.std(X):8.4f}")
    print(f"  Min:      {np.min(X):8.4f}")
    print(f"  Max:      {np.max(X):8.4f}")
    print(f"  Median:   {np.median(X):8.4f}")
    
    print("\n" + "=" * 70)
    print("✅ PREPROCESSING COMPLETE!")
    print("=" * 70)
    print("\n▶️  Next Step: python 3_train_mfcc_cnn.py")
    
    return True

if __name__ == "__main__":
    success = preprocess_dataset()
    sys.exit(0 if success else 1)
