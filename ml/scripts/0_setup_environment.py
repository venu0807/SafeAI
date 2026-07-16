"""
SafeGuard AI - Step 0: Environment Setup
Creates necessary directories and validates dataset structure
"""

import os
import sys
from pathlib import Path

def create_directory_structure():
    """Create all necessary directories"""
    base_dir = Path(__file__).parent.parent  # D:\Proposals\SafeguardAI\ml
    
    directories = [
        base_dir / "processed_data" / "distress",
        base_dir / "processed_data" / "normal",
        base_dir / "models",
        base_dir / "plots",
        base_dir / "logs",
    ]
    
    for directory in directories:
        directory.mkdir(parents=True, exist_ok=True)
        print(f"✓ Created: {directory}")

def validate_datasets():
    """Check if all dataset folders exist and contain audio files"""
    base_dir = Path(__file__).parent.parent
    dataset_dir = base_dir / "datasets"
    
    # Automatically discover all folders in the datasets directory
    if not dataset_dir.exists():
        print(f"✗ Datasets directory not found: {dataset_dir}")
        sys.exit(1)
    
    # Get all subdirectories in the datasets folder
    dataset_folders = [f.name for f in dataset_dir.iterdir() if f.is_dir()]
    
    if not dataset_folders:
        print(f"✗ No dataset folders found in {dataset_dir}")
        sys.exit(1)
    
    print("\n" + "="*60)
    print("DATASET VALIDATION")
    print("="*60)
    
    total_files = 0
    for folder in sorted(dataset_folders):
        folder_path = dataset_dir / folder
        
        # Count .wav files recursively
        wav_files = list(folder_path.rglob("*.wav"))
        total_files += len(wav_files)
        print(f"✓ {folder}: {len(wav_files)} .wav files found")
    
    print(f"\nTotal audio files: {total_files}")
    
    if total_files < 100:
        print("⚠ WARNING: Less than 100 audio files found. Model may underperform.")
    else:
        print("✓ Sufficient data for training!")
    
    return total_files

def check_dependencies():
    """Verify all required packages are installed"""
    print("\n" + "="*60)
    print("DEPENDENCY CHECK")
    print("="*60)
    
    required_packages = [
        ("numpy", "numpy"),
        ("pandas", "pandas"),
        ("librosa", "librosa"),
        ("tensorflow", "tensorflow"),
        ("torch", "torch"),
        ("transformers", "transformers"),
    ]
    
    all_good = True
    for package_name, import_name in required_packages:
        try:
            __import__(import_name)
            print(f"✓ {package_name} installed")
        except ImportError:
            print(f"✗ {package_name} NOT installed")
            all_good = False
    
    if not all_good:
        print("\n❌ Missing dependencies! Run:")
        print("   pip install -r requirements.txt")
        sys.exit(1)
    else:
        print("\n✓ All dependencies installed!")


def main():
    print("="*60)
    print("SAFEGUARD AI - ML PIPELINE SETUP")
    print("="*60)
    
    # Step 1: Create directories
    print("\n[1/3] Creating directory structure...")
    create_directory_structure()
    
    # Step 2: Validate datasets
    print("\n[2/3] Validating datasets...")
    total_files = validate_datasets()
    
    # Step 3: Check dependencies
    print("\n[3/3] Checking dependencies...")
    check_dependencies()
    
    print("\n" + "="*60)
    print("✅ SETUP COMPLETE!")
    print("="*60)
    print("\nNext step: Run '1_prepare_datasets.py'")

if __name__ == "__main__":
    main()
