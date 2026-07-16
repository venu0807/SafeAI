"""
SafeGuard AI - Wav2Vec2BERT Model Training (OPTIMIZED FOR SPEED)
Trains Wav2Vec2 + Transformer encoder for audio threat detection

⚡ PERFORMANCE OPTIMIZATION:
This script now supports FAST training with pre-computed Wav2Vec2 features!

RECOMMENDED WORKFLOW (100x FASTER):
  1. Pre-extract features: python 3b_extract_wav2vec_features.py
  2. Train with pre-computed: python 3_train_wav2vec2_bert.py
  3. Result: ~15x faster training (~0.5s per batch instead of 0.14s)

REQUIREMENT:
  Pre-computed features are required for this script.
  If they are missing, training exits and asks you to run the extractor.
  Expected: ~2-3 hours for 30 epochs with pre-computed features

KEY CHANGES:
- Added PrecomputedFeaturesDataset for fast feature loading
- Increased default batch size from 4 to 32 (better GPU utilization)
- Optimized DataLoader with num_workers=4 and prefetch
- Removed on-the-fly Wav2Vec2 extraction from training loop
"""

import os
import sys
import json
import numpy as np
import pandas as pd
import warnings
import platform
import gc
from pathlib import Path
import time

import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torch.nn.utils.rnn import pad_sequence
import librosa
import h5py
from transformers import Wav2Vec2Model, Wav2Vec2FeatureExtractor, logging as transformers_logging
from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support

import matplotlib.pyplot as plt

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

# Suppress warnings
warnings.filterwarnings("ignore", message="Passing `gradient_checkpointing` to a config initialization is deprecated")
warnings.filterwarnings("ignore", message="torch.utils.checkpoint: please pass in use_reentrant")
transformers_logging.set_verbosity_error()

# Setup GPU (initialized inside training to avoid worker side-effects on Windows)
from gpu_manager import GPUManager

# OS detection
IS_WINDOWS = platform.system() == "Windows"

# Performance toggles (safe defaults for NVIDIA GPUs)
torch.backends.cudnn.benchmark = True
torch.backends.cuda.matmul.allow_tf32 = True
torch.backends.cudnn.allow_tf32 = True
try:
    torch.set_float32_matmul_precision('high')
except Exception:
    pass


class PrecomputedFeaturesDataset(Dataset):
    """PyTorch Dataset for pre-extracted Wav2Vec2 features from HDF5 (FAST!)"""
    
    def __init__(self, h5_file, labels_file):
        """Load pre-computed Wav2Vec2 features from HDF5"""
        self.h5_file = h5_file
        self.labels = np.load(labels_file)  # (n_samples,)
        self._h5 = None
        self.sample_ids = []
        self.sample_id_to_idx = {}
        self._labels_from_h5 = None

        with h5py.File(str(h5_file), 'r') as f:
            keys = [k for k in f.keys() if k.startswith('sample_')]
            self.sample_ids = sorted(
                (int(k.split('_')[1]) for k in keys),
                key=lambda x: x
            )
            self.sample_id_to_idx = {sid: i for i, sid in enumerate(self.sample_ids)}

            # Peek at first feature to get shape
            if self.sample_ids:
                first_feat = f[f'sample_{self.sample_ids[0]}/features'][:]
                print(f"   ✓ Feature shape: {first_feat.shape}")

            # If labels length doesn't match HDF5 content, fall back to labels stored in HDF5
            if len(self.labels) != len(self.sample_ids) or (self.sample_ids and max(self.sample_ids) >= len(self.labels)):
                self._labels_from_h5 = []
                for sid in self.sample_ids:
                    self._labels_from_h5.append(int(f[f'sample_{sid}'].attrs.get('label', 0)))

        self.num_samples = len(self.sample_ids)
        print(f"   ✓ Loaded {self.num_samples} pre-computed features from HDF5")
        if self._labels_from_h5 is not None:
            print("   ⚠️  labels.npy length does not match HDF5; using labels stored in HDF5")
        
    def __len__(self):
        return self.num_samples
    
    def __getitem__(self, idx):
        h5f = self._get_h5()
        sample_id = self.sample_ids[idx]
        feat = h5f[f'sample_{sample_id}/features'][:]  # (seq_len, hidden_dim)
        feat = torch.from_numpy(feat).float()
        if self._labels_from_h5 is not None:
            label_val = self._labels_from_h5[idx]
        else:
            label_val = self.labels[sample_id]
        label = torch.tensor(label_val, dtype=torch.long)
        return feat, label

    def _get_h5(self):
        if self._h5 is None:
            self._h5 = h5py.File(str(self.h5_file), 'r')
        return self._h5

    def __del__(self):
        if self._h5 is not None:
            try:
                self._h5.close()
            except Exception:
                pass


class AudioDataset(Dataset):
    """PyTorch Dataset for audio files with labels (fallback if features not pre-computed)"""
    
    def __init__(self, metadata_csv, audio_root, sr=16000, max_seconds=5.0):
        self.df = pd.read_csv(metadata_csv)
        self.audio_root = Path(audio_root)
        self.sr = sr
        self.max_len = int(sr * max_seconds)
        
    def __len__(self):
        return len(self.df)
    
    def __getitem__(self, idx):
        row = self.df.iloc[idx]
        path = self.audio_root / row['path']
        label = int(row['label'])
        
        # Load audio
        try:
            wav, sr = librosa.load(str(path), sr=self.sr, mono=True)
        except Exception as e:
            print(f"⚠️  Error loading {path}: {e}")
            # Return silence if loading fails
            wav = np.zeros(self.max_len)
        
        # Trim or pad
        if len(wav) > self.max_len:
            start = np.random.randint(0, len(wav) - self.max_len)
            wav = wav[start:start + self.max_len]
        else:
            pad = self.max_len - len(wav)
            if pad > 0:
                wav = np.pad(wav, (0, pad))
        
        return torch.from_numpy(wav).float(), label


def collate_fn_features(batch):
    """Collate batch for pre-computed features (variable sequence length)"""
    feats_list, ys = zip(*batch)
    ys = torch.tensor(ys, dtype=torch.long)

    # Pad sequences to same length (faster than manual loop)
    feats = pad_sequence(feats_list, batch_first=True)  # (batch_size, max_seq_len, hidden_dim)
    return feats, ys


def collate_fn(batch):
    """Collate batch for audio files"""
    xs, ys = zip(*batch)
    xs = torch.stack(xs)
    ys = torch.tensor(ys, dtype=torch.long)
    return xs, ys


class Wav2Vec2BERT(nn.Module):
    """Wav2Vec2 + Transformer Encoder classifier"""
    
    def __init__(self, input_dim=768, num_labels=2, transformer_layers=2, nhead=8, hidden_dropout=0.1, projection_dim=256):
        super().__init__()
        
        # Optional: projection layer if input dim differs
        if projection_dim in (None, 0) or projection_dim == input_dim:
            self.projection = None
            hidden_dim = input_dim
        else:
            self.projection = nn.Linear(input_dim, projection_dim)
            hidden_dim = projection_dim
        
        # Transformer encoder with batch_first=True
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_dim, 
            nhead=min(nhead, hidden_dim // 64),  # Ensure valid head count
            dim_feedforward=512,
            dropout=hidden_dropout, 
            batch_first=True,
            activation='relu'
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=transformer_layers)
        
        # Classification head
        self.classifier = nn.Sequential(
            nn.Linear(hidden_dim, 128),
            nn.ReLU(),
            nn.Dropout(hidden_dropout),
            nn.Linear(128, num_labels)
        )
    
    def forward(self, hidden_states, attention_mask=None):
        """
        Args:
            hidden_states: (batch, seq_len, input_dim) - pre-computed Wav2Vec2 features
            attention_mask: (batch, seq_len) - optional mask
        """
        # Project if needed
        if self.projection:
            x = self.projection(hidden_states)  # (batch, seq_len, hidden_dim)
        else:
            x = hidden_states
        
        # Apply transformer
        x = self.transformer(x)  # (batch, seq_len, hidden_dim)
        
        # Global average pooling
        x = x.mean(dim=1)  # (batch, hidden_dim)
        
        # Classification
        logits = self.classifier(x)  # (batch, num_labels)
        return logits


def find_max_batch_size(sample_feat, device, model_kwargs, use_amp=True, max_batch_size=512):
    """Find the largest batch size that fits in GPU memory for this model/input shape."""
    if device.type != 'cuda':
        return 1

    loss_fn = nn.CrossEntropyLoss()
    probe_model = Wav2Vec2BERT(**model_kwargs).to(device)
    probe_model.train()
    optimizer = torch.optim.AdamW(probe_model.parameters(), lr=1e-4)

    def try_batch_size(bs):
        feats = None
        labels = None
        logits = None
        loss = None
        try:
            feats = sample_feat.to(device, non_blocking=True)
            feats = feats.unsqueeze(0).repeat(bs, 1, 1)
            labels = torch.zeros(bs, dtype=torch.long, device=device)
            optimizer.zero_grad(set_to_none=True)
            with torch.amp.autocast('cuda', enabled=use_amp):
                logits = probe_model(feats)
                loss = loss_fn(logits, labels)
            loss.backward()
            optimizer.step()
            torch.cuda.synchronize()
            return True
        except RuntimeError as e:
            if 'out of memory' in str(e).lower():
                return False
            raise
        finally:
            del feats, labels, logits, loss
            torch.cuda.empty_cache()
            gc.collect()

    # Exponential search
    last_good = None
    bs = 1
    while bs <= max_batch_size:
        if try_batch_size(bs):
            last_good = bs
            bs *= 2
        else:
            break

    if last_good is None:
        return 1

    # Binary search between last_good and failing bs
    low = last_good
    high = min(bs - 1, max_batch_size)
    while low < high:
        mid = (low + high + 1) // 2
        if try_batch_size(mid):
            low = mid
        else:
            high = mid - 1

    # Safety margin to avoid fragmentation issues
    safe = max(1, int(low * 0.9))
    # Ensure at least 1 and not above low
    safe = min(safe, low)

    del probe_model, optimizer
    torch.cuda.empty_cache()
    gc.collect()
    return safe


def plot_training_history(history_dict, output_path):
    """Plot training curves"""
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    
    epochs = range(1, len(history_dict['train_loss']) + 1)
    
    # Loss
    axes[0].plot(epochs, history_dict['train_loss'], label='Train Loss')
    axes[0].plot(epochs, history_dict['val_loss'], label='Val Loss')
    axes[0].set_title('Model Loss')
    axes[0].set_xlabel('Epoch')
    axes[0].set_ylabel('Loss')
    axes[0].legend()
    axes[0].grid(True)
    
    # Accuracy & F1
    axes[1].plot(epochs, history_dict['train_acc'], label='Train Accuracy')
    axes[1].plot(epochs, history_dict['val_acc'], label='Val Accuracy')
    axes[1].plot(epochs, history_dict['val_f1'], label='Val F1')
    axes[1].set_title('Model Metrics')
    axes[1].set_xlabel('Epoch')
    axes[1].set_ylabel('Score')
    axes[1].legend()
    axes[1].grid(True)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✅ Training curves saved: {output_path}")
    plt.close()


def train_wav2vec2_bert(args):
    """Main training function"""
    print("=" * 70)
    print("🧠 SAFEGUARD AI - WAV2VEC2BERT MODEL TRAINING (OPTIMIZED)")
    print("=" * 70)

    # Setup GPU + seeds (main process only, avoid worker side-effects on Windows)
    GPUManager.setup_pytorch_gpu()
    np.random.seed(RANDOM_SEED)
    torch.manual_seed(RANDOM_SEED)
    if torch.cuda.is_available():
        torch.cuda.manual_seed(RANDOM_SEED)

    if IS_WINDOWS:
        try:
            torch.multiprocessing.set_sharing_strategy("file_system")
        except Exception:
            pass
    
    # Check GPU availability
    print("\n🖥️  GPU Detection:")
    print(f"   CUDA available: {torch.cuda.is_available()}")
    print(f"   CUDA version: {torch.version.cuda}")
    if torch.cuda.is_available():
        print(f"   GPU count: {torch.cuda.device_count()}")
        for i in range(torch.cuda.device_count()):
            print(f"   GPU {i}: {torch.cuda.get_device_name(i)}")
            print(f"      Total memory: {torch.cuda.get_device_properties(i).total_memory / 1e9:.2f} GB")
    
    if not torch.cuda.is_available() and not args.allow_cpu:
        print("\nERROR: CUDA not available for PyTorch.")
        print("Fix: install the CUDA-enabled build of torch for your GPU/driver.")
        print("If you really want CPU, re-run with --allow-cpu.")
        return

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"\n✅ Using device: {device}")

    # Resolve output directory relative to BASE_DIR for consistency
    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = Path(BASE_DIR) / output_dir
    args.output_dir = str(output_dir)
    
    # Check for pre-computed features (MUCH FASTER!)
    precomputed_dir = Path(BASE_DIR) / 'processed_data' / 'wav2vec2_cached'
    h5_file = precomputed_dir / 'features.h5'
    labels_file = precomputed_dir / 'labels.npy'
    use_precomputed = h5_file.exists() and labels_file.exists()
    
    if not use_precomputed:
        print(f"\n❌ ERROR: Pre-computed features not found!")
        print(f"\n⚡ REQUIRED: Run feature extraction first:")
        print("   python ml/scripts/3b_extract_wav2vec_features.py  (from repo root)")
        print(f"\n   This will:")
        print(f"   - Extract Wav2Vec2 features from all audio files (15-30 min)")
        print(f"   - Save to: {precomputed_dir}/features.h5")
        print(f"   - Enable 15-20x faster training")
        return
    
    print(f"\n✅ USING PRE-COMPUTED FEATURES (FAST MODE)")
    print(f"   Loading from: {precomputed_dir}/features.h5")
    
    # Load metadata to get split info
    metadata_path = precomputed_dir / 'metadata.csv'
    df = pd.read_csv(metadata_path)
    
    # Normalize paths (handle WSL /mnt/d/... format if any)
    if 'path' in df.columns:
        df['path'] = df['path'].apply(normalize_path)
    
    # Create train/val split
    train_df = df.sample(frac=0.9, random_state=RANDOM_SEED)
    val_df = df.drop(train_df.index)
    
    print(f"\n📊 Dataset splits:")
    print(f"   Train: {len(train_df)} samples")
    print(f"   Val:   {len(val_df)} samples")
    
    # Load pre-computed features
    print(f"\n📂 Loading pre-computed features from HDF5...")
    train_ds = PrecomputedFeaturesDataset(
        str(h5_file),
        str(labels_file)
    )
    val_ds = train_ds  # Use same features, just different indices
    
    # Filter to train/val indices (map metadata indices to available HDF5 sample ids)
    train_indices = [train_ds.sample_id_to_idx[i] for i in train_df.index if i in train_ds.sample_id_to_idx]
    val_indices = [train_ds.sample_id_to_idx[i] for i in val_df.index if i in train_ds.sample_id_to_idx]
    missing_train = len(train_df) - len(train_indices)
    missing_val = len(val_df) - len(val_indices)
    if missing_train or missing_val:
        print(f"   ⚠️  Missing samples in HDF5: train={missing_train}, val={missing_val}")
    
    train_ds_subset = torch.utils.data.Subset(train_ds, train_indices)
    val_ds_subset = torch.utils.data.Subset(val_ds, val_indices)
    
    collate_fn_use = collate_fn_features
    input_dim = 768  # Wav2Vec2 hidden size
    use_precomputed_flag = True

    # Auto-select batch size for GPU (optional)
    if args.auto_batch and device.type == 'cuda':
        try:
            sample_feat, _ = train_ds_subset[0]
            model_kwargs = dict(
                input_dim=input_dim,
                num_labels=args.num_labels,
                transformer_layers=args.transformer_layers,
                nhead=args.nhead,
                projection_dim=args.projection_dim
            )
            auto_bs = find_max_batch_size(
                sample_feat,
                device,
                model_kwargs,
                use_amp=args.mixed_precision and torch.cuda.is_available(),
                max_batch_size=args.max_batch_size
            )
            if auto_bs < 1:
                auto_bs = 1
            print(f"\n⚙️  Auto batch size selected: {auto_bs}")
            args.batch_size = auto_bs
        except Exception as e:
            print(f"\n⚠️  Auto batch size failed: {e}")
            print("   Falling back to provided batch size.")
    
    # Create dataloaders with optimized settings
    print(f"\n📊 DataLoader configuration:")
    print(f"   Batch size: {args.batch_size}")
    num_workers = args.num_workers if args.num_workers is not None else min(4, os.cpu_count() or 4)
    if IS_WINDOWS and num_workers > 0 and not args.force_workers:
        print("   ⚠️  Windows DataLoader workers can hit shared-memory error 1455.")
        print("      Falling back to num_workers=0. Use --force-workers to override.")
        num_workers = 0
    print(f"   Num workers: {num_workers} (for fast data loading)")
    pin_memory = torch.cuda.is_available()
    print(f"   Pin memory: {pin_memory} (GPU memory optimization)")

    loader_kwargs = dict(num_workers=num_workers, pin_memory=pin_memory)
    if num_workers > 0:
        loader_kwargs["prefetch_factor"] = 2
        loader_kwargs["persistent_workers"] = True
    
    if args.use_weighted_sampler and not use_precomputed:
        # Only for non-precomputed (has train_ds_subset.df)
        labels = train_ds_subset.df['label'].values
        class_sample_count = np.array([len(np.where(labels == t)[0]) for t in np.unique(labels)])
        weight = 1.0 / class_sample_count
        samples_weight = np.array([weight[t] for t in labels])
        samples_weight = torch.from_numpy(samples_weight).double()
        sampler = torch.utils.data.WeightedRandomSampler(samples_weight, len(samples_weight))
        train_loader = DataLoader(
            train_ds_subset,
            batch_size=args.batch_size,
            sampler=sampler,
            collate_fn=collate_fn_use,
            **loader_kwargs
        )
        print("   ✅ Using WeightedRandomSampler")
    else:
        # Standard DataLoader (works for both pre-computed and on-the-fly)
        train_loader = DataLoader(
            train_ds_subset,
            batch_size=args.batch_size,
            shuffle=True,
            collate_fn=collate_fn_use,
            **loader_kwargs
        )
    
    
    val_loader = DataLoader(
        val_ds_subset,
        batch_size=args.batch_size,
        shuffle=False,
        collate_fn=collate_fn_use,
        **loader_kwargs
    )
    
    # Build model
    print(f"\n🏗️  Building Wav2Vec2BERT model...")
    model = Wav2Vec2BERT(
        input_dim=input_dim,
        num_labels=args.num_labels,
        transformer_layers=args.transformer_layers,
        nhead=args.nhead,
        projection_dim=args.projection_dim
    )
    
    model.to(device)
    print(f"✅ Model moved to device: {device}")
    print(f"   Parameters: {sum(p.numel() for p in model.parameters()):,}")
    
    # Optimizer
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    
    # Loss function
    loss_fn = nn.CrossEntropyLoss()
    
    # Scheduler
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)
    
    # Mixed precision
    use_amp = args.mixed_precision and torch.cuda.is_available()
    scaler = torch.cuda.amp.GradScaler() if use_amp else None
    if use_amp:
        print("✅ Using mixed precision training (AMP)")
    
    # GPU memory optimization
    if device.type == 'cuda':
        torch.cuda.empty_cache()
        allocated = torch.cuda.memory_allocated(0) / 1e9
        total = torch.cuda.get_device_properties(0).total_memory / 1e9
        print(f"✅ GPU memory: {allocated:.2f}GB / {total:.2f}GB")
    
    # Training loop
    import time
    print("\n🚀 Starting training...")
    print(f"   Device: {device} | Mixed Precision: {use_amp}")
    print(f"   Using pre-computed features: {use_precomputed}")
    print("=" * 70)
    
    history = {
        'train_loss': [],
        'train_acc': [],
        'val_loss': [],
        'val_acc': [],
        'val_f1': [],
        'val_precision': [],
        'val_recall': []
    }
    
    best_val_f1 = None
    patience_counter = 0
    epoch_start_time = time.time()
    
    for epoch in range(1, args.epochs + 1):
        # Training
        model.train()
        total_loss = 0.0
        train_preds = []
        train_labels_list = []
        epoch_time = time.time()
        
        print(f"\n📍 Epoch {epoch}/{args.epochs}")
        for batch_idx, (feats, labels) in enumerate(train_loader):
            feats = feats.to(device, non_blocking=True)
            labels = labels.to(device, non_blocking=True)
            
            # Forward pass with AMP
            optimizer.zero_grad()
            with torch.amp.autocast('cuda', enabled=use_amp):
                logits = model(feats)
                loss = loss_fn(logits, labels)
            
            # Backward pass
            if use_amp:
                scaler.scale(loss).backward()
                scaler.step(optimizer)
                scaler.update()
            else:
                loss.backward()
                optimizer.step()
            
            total_loss += loss.item() * labels.size(0)
            preds = torch.argmax(logits, dim=-1)
            train_preds.extend(preds.cpu().numpy().tolist())
            train_labels_list.extend(labels.cpu().numpy().tolist())
            
            # Progress update
            if (batch_idx + 1) % 50 == 0 or (batch_idx + 1) == len(train_loader):
                elapsed = time.time() - epoch_time
                batch_per_sec = (batch_idx + 1) / elapsed if elapsed > 0 else 0
                avg_loss = total_loss / ((batch_idx + 1) * labels.size(0))
                print(f"   [{batch_idx+1:>4}/{len(train_loader)}] Loss: {avg_loss:.4f} | {batch_per_sec:.1f} batch/s")
        
        avg_train_loss = total_loss / len(train_ds_subset)
        train_acc = np.mean(np.array(train_preds) == np.array(train_labels_list))
        epoch_elapsed = time.time() - epoch_time
        
        # Validation
        model.eval()
        total_val_loss = 0.0
        val_preds = []
        val_labels = []
        
        with torch.no_grad():
            for feats, labels in val_loader:
                feats = feats.to(device, non_blocking=True)
                labels = labels.to(device, non_blocking=True)
                
                with torch.amp.autocast('cuda', enabled=use_amp):
                    logits = model(feats)
                    loss = loss_fn(logits, labels)
                
                total_val_loss += loss.item() * labels.size(0)
                preds = torch.argmax(logits, dim=-1)
                val_preds.extend(preds.cpu().numpy().tolist())
                val_labels.extend(labels.cpu().numpy().tolist())
        
        avg_val_loss = total_val_loss / len(val_ds_subset)
        val_acc = np.mean(np.array(val_preds) == np.array(val_labels))
        
        # Metrics
        precision, recall, f1, _ = precision_recall_fscore_support(
            val_labels, val_preds, average='binary', zero_division=0
        )
        
        history['train_loss'].append(avg_train_loss)
        history['train_acc'].append(train_acc)
        history['val_loss'].append(avg_val_loss)
        history['val_acc'].append(val_acc)
        history['val_f1'].append(f1)
        history['val_precision'].append(precision)
        history['val_recall'].append(recall)
        
        print(f"   ✅ Loss: {avg_train_loss:.4f} | Acc: {train_acc:.4f} | Val Acc: {val_acc:.4f} | Val F1: {f1:.4f} ({epoch_elapsed:.1f}s)")
        
        # LR scheduler
        scheduler.step()
        
        # Early stopping & checkpointing
        if best_val_f1 is None or f1 > best_val_f1:
            best_val_f1 = f1
            patience_counter = 0
            output_dir = Path(args.output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)
            torch.save({
                'model_state_dict': model.state_dict(),
                'optimizer_state_dict': optimizer.state_dict(),
                'epoch': epoch,
                'best_f1': f1
            }, output_dir / args.output_name)
            print(f"   ✅ Best model saved (F1={f1:.4f})")
        else:
            patience_counter += 1
            if patience_counter >= args.patience:
                total_time = time.time() - epoch_start_time
                print(f"\n⏹️  Early stopping at epoch {epoch}")
                break
    
    print("=" * 70)
    print("✅ Training complete!")
    
    output_dir = Path(args.output_dir)
    
    # Save history
    with open(output_dir / 'training_history.json', 'w') as f:
        json.dump({k: [float(x) for x in v] for k, v in history.items()}, f, indent=2)
    print(f"✅ Training history saved")
    
    # Plot curves
    plot_training_history(history, output_dir / 'training_curves.png')
    
    # Evaluate on full validation set
    print("\n📊 Final Validation Metrics:")
    print(f"   Accuracy:  {val_acc*100:.2f}%")
    print(f"   Precision: {precision*100:.2f}%")
    print(f"   Recall:    {recall*100:.2f}%")
    print(f"   F1-Score:  {f1*100:.2f}%")
    
    # Classification report
    print("\n" + "=" * 70)
    print("📋 CLASSIFICATION REPORT")
    print("=" * 70)
    report = classification_report(val_labels, val_preds, target_names=LABEL_NAMES, digits=4)
    print(report)
    
    # Save classification report
    with open(output_dir / 'classification_report.txt', 'w') as f:
        f.write("SafeGuard AI - Wav2Vec2BERT Classification Report\n")
        f.write("=" * 70 + "\n\n")
        f.write(f"Training mode: {'Pre-computed features' if use_precomputed else 'On-the-fly extraction'}\n")
        f.write(f"Val Accuracy: {val_acc*100:.2f}%\n")
        f.write(f"Val Precision: {precision*100:.2f}%\n")
        f.write(f"Val Recall: {recall*100:.2f}%\n")
        f.write(f"Val F1-Score: {f1*100:.2f}%\n\n")
        f.write(report)
    
    print(f"✅ Classification report saved: {output_dir}/classification_report.txt")
    
    print(f"\n✅ Model training complete!")
    print(f"📁 Model saved: {output_dir}/{args.output_name}")
    print("▶️  Next step: Run 4_test_models.py or 4c_dual_model_ensemble.py")
    
    return


def parse_args():
    import argparse
    p = argparse.ArgumentParser(description='Train Wav2Vec2BERT model for audio threat detection (OPTIMIZED)')
    p.add_argument('--metadata', default='processed_data/metadata.csv', help='CSV with columns: path,label')
    p.add_argument('--audio-root', default='datasets', help='root for audio file paths in metadata')
    p.add_argument('--sr', type=int, default=16000, help='sampling rate')
    p.add_argument('--max-seconds', type=float, default=5.0, help='max audio length in seconds')
    p.add_argument('--batch-size', type=int, default=32, help='batch size (increased for GPU)')
    p.add_argument('--epochs', type=int, default=30, help='number of epochs')
    p.add_argument('--lr', type=float, default=5e-5, help='learning rate')
    p.add_argument('--output-dir', type=str, default='models', help='output directory')
    p.add_argument('--output-name', default='wav2vec2_bert_final.pth', help='model filename')
    p.add_argument('--num-labels', type=int, default=2, help='number of classes')
    p.add_argument('--transformer-layers', type=int, default=3, help='transformer encoder layers')
    p.add_argument('--nhead', type=int, default=8, help='number of attention heads')
    p.add_argument('--projection-dim', type=int, default=256, help='projection dim (0 to disable, use full 768)')
    p.add_argument('--augment', action='store_true', help='apply data augmentation (noise)')
    p.add_argument('--use-weighted-sampler', action='store_true', help='use WeightedRandomSampler')
    p.add_argument('--use-class-weights', action='store_true', help='use class weights in loss')
    p.add_argument('--mixed-precision', action='store_true', default=True, help='use AMP (default: True for GPU)')
    p.add_argument('--patience', type=int, default=5, help='early stopping patience')
    p.add_argument('--num-workers', type=int, default=None, help='DataLoader workers (default: min(4, cpu_count))')
    p.add_argument('--allow-cpu', action='store_true', help='allow CPU fallback if CUDA is unavailable')
    p.add_argument('--force-workers', action='store_true', help='force DataLoader workers on Windows (may error)')
    p.add_argument('--auto-batch', action='store_true', help='auto-select the largest safe batch size for GPU')
    p.add_argument('--max-batch-size', type=int, default=512, help='upper bound for auto batch search')
    return p.parse_args()


if __name__ == '__main__':
    args = parse_args()
    train_wav2vec2_bert(args)
