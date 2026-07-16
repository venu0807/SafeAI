"""
Quick script to regenerate train/val/test indices matching current features
"""

import numpy as np
from pathlib import Path
import sys

sys.path.append(str(Path(__file__).parent.parent))
from config import BASE_DIR

base = Path(BASE_DIR)
features = np.load(base / 'processed_data' / 'mfcc_features.npy')
n = len(features)

# Get current split ratio
train_split = 0.7
val_split = 0.15
# test_split = 0.15

# Create indices
all_indices = np.arange(n)
np.random.seed(42)
np.random.shuffle(all_indices)

n_train = int(n * train_split)
n_val = int(n * val_split)

train_idx = sorted(all_indices[:n_train])
val_idx = sorted(all_indices[n_train:n_train + n_val])
test_idx = sorted(all_indices[n_train + n_val:])

print(f"Total samples: {n}")
print(f"Train: {len(train_idx)} ({len(train_idx)/n:.1%})")
print(f"Val:   {len(val_idx)} ({len(val_idx)/n:.1%})")
print(f"Test:  {len(test_idx)} ({len(test_idx)/n:.1%})")

# Save
np.save(base / 'processed_data' / 'train_indices.npy', train_idx)
np.save(base / 'processed_data' / 'val_indices.npy', val_idx)
np.save(base / 'processed_data' / 'test_indices.npy', test_idx)

print("[OK] Indices saved")
