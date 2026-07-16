"""
SafeGuard AI - Fix WSL Paths to Windows Paths
Converts /mnt/d/... paths in metadata.csv to D:\... paths
and ensures all metadata files use Windows-compatible paths.
"""

import csv
import os
import sys

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import METADATA_CSV
from config import normalize_path


def fix_metadata_csv(filepath, label="metadata"):
    """Fix WSL paths in a metadata CSV file. Returns True on success."""
    if not os.path.exists(filepath):
        print(f"  [SKIP] {label} not found at: {filepath}")
        return True

    print(f"  [LOAD] {filepath}")

    rows = []
    wsl_count = 0
    with open(filepath, "r", newline="") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        if "path" not in fieldnames:
            print(f"  [SKIP] No 'path' column in {filepath}")
            return True
        for row in reader:
            original = row["path"]
            row["path"] = normalize_path(original)
            if "/mnt/" in original:
                wsl_count += 1
            rows.append(row)

    print(f"  [FIX] Found {wsl_count} WSL paths to fix out of {len(rows)} total rows")

    if wsl_count > 0:
        with open(filepath, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(rows)
        print(f"  [OK] Saved {len(rows)} rows with Windows paths")

    # Show samples
    print("  [SAMPLE] First 3 paths:")
    for i in range(min(3, len(rows))):
        print(f"     {i}: {rows[i]['path']}")

    return True


def main():
    print("=" * 60)
    print(" SAFEGUARD AI - FIX METADATA PATHS FOR WINDOWS")
    print("=" * 60)

    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    # 1. Main metadata.csv
    print("\n[1] Main metadata.csv")
    fix_metadata_csv(METADATA_CSV, "processed_data/metadata.csv")

    # 2. Wav2Vec2 cached metadata.csv
    wav2vec_meta = os.path.join(base_dir, "processed_data", "wav2vec2_cached", "metadata.csv")
    print("\n[2] Wav2Vec2 cached metadata.csv")
    fix_metadata_csv(wav2vec_meta, "processed_data/wav2vec2_cached/metadata.csv")

    print("\n" + "=" * 60)
    print(" ALL DONE! Paths are now Windows-compatible.")
    print("=" * 60)
    print("""\nNext steps (run in order):
  1. python scripts/1_organize_datasets.py
  2. python scripts/2_preprocess_audio.py
  3. python scripts/3_train_mfcc_cnn.py
  4. python scripts/3b_extract_wav2vec_features.py
  5. python scripts/3_train_wav2vec2_bert.py
  6. python scripts/4_test_models.py
  7. python scripts/5_export_tflite.py""")


if __name__ == "__main__":
    main()
