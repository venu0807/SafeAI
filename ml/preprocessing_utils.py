"""
SafeGuard AI - Preprocessing Utilities
Utility functions for audio preprocessing and MFCC extraction
"""

import numpy as np
import librosa
from config import (
    SAMPLE_RATE, DURATION, N_SAMPLES, N_MFCC, N_FFT, HOP_LENGTH, N_MELS,
    PRE_EMPHASIS, MAX_TIME_STEPS
)


def extract_mfcc_features(audio_path, target_length=MAX_TIME_STEPS):
    """
    Extract MFCC features matching Android MFCCExtractor.java
    
    Args:
        audio_path: Path to audio file
        target_length: Target number of time steps
        
    Returns:
        numpy array of shape (target_length, N_MFCC)
    """
    try:
        # Load audio
        audio, sr = librosa.load(audio_path, sr=SAMPLE_RATE, duration=DURATION)
        
        # Ensure correct length
        if len(audio) < N_SAMPLES:
            audio = np.pad(audio, (0, N_SAMPLES - len(audio)))
        else:
            audio = audio[:N_SAMPLES]
        
        # Pre-emphasis filter
        audio = np.append(audio[0], audio[1:] - PRE_EMPHASIS * audio[:-1])
        
        # Extract MFCC
        mfcc = librosa.feature.mfcc(
            y=audio,
            sr=sr,
            n_mfcc=N_MFCC,
            n_fft=N_FFT,
            hop_length=HOP_LENGTH,
            n_mels=N_MELS,
            fmin=0,
            fmax=sr/2
        )
        
        # Transpose to (time, features)
        mfcc = mfcc.T
        
        # Normalize (per feature)
        mfcc = (mfcc - np.mean(mfcc, axis=0)) / (np.std(mfcc, axis=0) + 1e-8)
        
        # Pad or truncate to target length
        if mfcc.shape[0] < target_length:
            pad_width = target_length - mfcc.shape[0]
            mfcc = np.pad(mfcc, ((0, pad_width), (0, 0)), mode='constant')
        else:
            mfcc = mfcc[:target_length, :]
        
        return mfcc
        
    except Exception as e:
        print(f"❌ Error extracting MFCC from {audio_path}: {e}")
        return None
