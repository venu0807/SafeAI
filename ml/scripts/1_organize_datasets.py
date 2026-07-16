"""
SafeGuard AI - Dataset Collection & Organization
Validates audio files and creates metadata.csv
"""

import os
import sys
import librosa
import pandas as pd
import numpy as np
from tqdm import tqdm
from pathlib import Path

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

def validate_audio_file(file_path):
    """Validate audio file and return its metadata"""
    try:
        # Load audio
        audio, sr = librosa.load(file_path, sr=SAMPLE_RATE, duration=None)
        
        # Get duration
        duration = len(audio) / sr
        
        # Basic validation
        if duration < 0.5:  # Too short
            return None
            
        if len(audio) == 0:  # Empty
            return None
        
        return {
            'path': normalize_path(file_path),
            'duration': duration,
            'sample_rate': sr,
            'samples': len(audio)
        }
    except Exception as e:
        print(f"❌ Error loading {file_path}: {e}")
        return None

def collect_dataset():
    """Collect and organize dataset"""
    print("=" * 60)
    print("📁 SAFEGUARD AI - DATASET COLLECTION")
    print("=" * 60)
    
    metadata_list = []
    
    # Collect distress samples
    print("\n🚨 Collecting DISTRESS samples...")
    distress_files = list(Path(DISTRESS_DIR).glob('**/*.wav')) + \
                     list(Path(DISTRESS_DIR).glob('**/*.mp3'))
    
    for file_path in tqdm(distress_files, desc="Distress"):
        meta = validate_audio_file(str(file_path))
        if meta:
            meta['label'] = 'distress'
            meta['label_id'] = LABEL_MAP['distress']
            metadata_list.append(meta)
    
    # Collect normal samples
    print("\n✅ Collecting NORMAL samples...")
    normal_files = list(Path(NORMAL_DIR).glob('**/*.wav')) + \
                   list(Path(NORMAL_DIR).glob('**/*.mp3'))
    
    for file_path in tqdm(normal_files, desc="Normal"):
        meta = validate_audio_file(str(file_path))
        if meta:
            meta['label'] = 'normal'
            meta['label_id'] = LABEL_MAP['normal']
            metadata_list.append(meta)
    
    # Create DataFrame
    df = pd.DataFrame(metadata_list)
    
    if len(df) == 0:
        print("\n❌ ERROR: No audio files found!")
        print(f"Please add audio files to:")
        print(f"  - Distress: {DISTRESS_DIR}")
        print(f"  - Normal: {NORMAL_DIR}")
        return
    
    # Statistics
    print("\n" + "=" * 60)
    print("📊 DATASET STATISTICS")
    print("=" * 60)
    print(f"Total samples: {len(df)}")
    print(f"\nClass distribution:")
    print(df['label'].value_counts())
    print(f"\nDuration statistics:")
    print(df.groupby('label')['duration'].describe())
    
    # Check class balance
    class_counts = df['label'].value_counts()
    imbalance_ratio = class_counts.max() / class_counts.min()
    
    if imbalance_ratio > 2.0:
        print(f"\n⚠️  WARNING: Dataset is imbalanced (ratio: {imbalance_ratio:.2f})")
        print("   Consider collecting more samples or using data augmentation")
    
    # Save metadata
    df.to_csv(METADATA_CSV, index=False)
    print(f"\n✅ Metadata saved to: {METADATA_CSV}")
    
    # Create train/val/test splits
    print("\n📂 Creating dataset splits...")
    
    # Stratified split
    from sklearn.model_selection import train_test_split
    
    # Set random seed
    np.random.seed(RANDOM_SEED)
    
    # Split: train (70%), temp (30%)
    train_df, temp_df = train_test_split(
        df, 
        test_size=(VAL_SPLIT + TEST_SPLIT),
        stratify=df['label_id'],
        random_state=RANDOM_SEED
    )
    
    # Split temp: val (50%), test (50%) of temp
    val_df, test_df = train_test_split(
        temp_df,
        test_size=0.5,
        stratify=temp_df['label_id'],
        random_state=RANDOM_SEED
    )
    
    # Save indices
    np.save(TRAIN_INDICES_NPY, train_df.index.values)
    np.save(VAL_INDICES_NPY, val_df.index.values)
    np.save(TEST_INDICES_NPY, test_df.index.values)
    
    print(f"  Train: {len(train_df)} samples ({len(train_df)/len(df)*100:.1f}%)")
    print(f"  Val:   {len(val_df)} samples ({len(val_df)/len(df)*100:.1f}%)")
    print(f"  Test:  {len(test_df)} samples ({len(test_df)/len(df)*100:.1f}%)")
    
    print("\n✅ Dataset collection complete!")
    print("▶️  Next step: Run 2_preprocess_audio.py")

if __name__ == "__main__":
    collect_dataset()
