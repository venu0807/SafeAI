"""
Train a simple Transformer model on pre-extracted MFCC features
Fast GPU training without expensive feature extraction
"""

import os
import sys
import json
import numpy as np
import pandas as pd
import warnings
from pathlib import Path
import time
import argparse

import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader, WeightedRandomSampler
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import matplotlib.pyplot as plt

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

warnings.filterwarnings("ignore")

# Setup GPU
from gpu_manager import GPUManager
GPUManager.setup_pytorch_gpu()

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"[INFO] Using device: {device}")

# ============================================================================
# DATASET
# ============================================================================

class MFCCDataset(Dataset):
    """PyTorch dataset for MFCC features"""
    
    def __init__(self, features, labels, train_indices=None, augment=False):
        """
        features: (n_samples, n_mfcc, n_frames)
        labels: (n_samples,)
        train_indices: indices to use (default: all)
        """
        if train_indices is not None:
            self.features = features[train_indices]
            self.labels = labels[train_indices]
        else:
            self.features = features
            self.labels = labels
        self.augment = augment
        
    def __len__(self):
        return len(self.labels)
    
    def __getitem__(self, idx):
        feat = torch.from_numpy(self.features[idx]).float()  # (n_mfcc, n_frames)
        label = torch.tensor(self.labels[idx], dtype=torch.long)
        
        # Simple augmentation: random time shifting
        if self.augment and np.random.random() < 0.3:
            shift = np.random.randint(-5, 6)  # shift by up to 5 frames
            if shift > 0:
                feat = torch.cat([torch.zeros(feat.shape[0], shift), feat[:, :-shift]], dim=1)
            elif shift < 0:
                feat = torch.cat([feat[:, -shift:], torch.zeros(feat.shape[0], -shift)], dim=1)
        
        return feat, label


def collate_fn(batch):
    """Collate batch"""
    feats, labels = zip(*batch)
    feats = torch.stack(feats)  # (batch_size, n_mfcc, n_frames)
    labels = torch.stack(labels)
    return feats, labels


# ============================================================================
# MODEL
# ============================================================================

class MFCCTransformer(nn.Module):
    """Simple Transformer classifier for MFCC features"""
    
    def __init__(self, n_mfcc=40, hidden_dim=128, nheads=4, num_layers=2, num_labels=2, dropout=0.1):
        super().__init__()
        self.embedding = nn.Linear(n_mfcc, hidden_dim)
        
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_dim,
            nhead=nheads,
            dim_feedforward=256,
            dropout=dropout,
            batch_first=True,
            activation='relu'
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        
        # Global average pooling + classification
        self.classifier = nn.Sequential(
            nn.Linear(hidden_dim, 64),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(64, num_labels)
        )
        
    def forward(self, x):
        # x: (batch, n_mfcc, n_frames)
        x = x.transpose(1, 2)  # -> (batch, n_frames, n_mfcc)
        x = self.embedding(x)  # -> (batch, n_frames, hidden_dim)
        x = self.transformer(x)  # -> (batch, n_frames, hidden_dim)
        x = x.mean(dim=1)  # global average pooling -> (batch, hidden_dim)
        x = self.classifier(x)  # -> (batch, num_labels)
        return x


# ============================================================================
# TRAINING
# ============================================================================

def train_epoch(model, train_loader, optimizer, loss_fn, device, use_amp=True):
    """Train for one epoch"""
    model.train()
    total_loss = 0.0
    all_preds = []
    all_labels = []
    
    scaler = torch.cuda.amp.GradScaler() if use_amp else None
    
    for batch_idx, (feats, labels) in enumerate(train_loader):
        feats = feats.to(device)
        labels = labels.to(device)
        
        optimizer.zero_grad()
        
        with torch.amp.autocast('cuda', enabled=use_amp):
            logits = model(feats)
            loss = loss_fn(logits, labels)
        
        if use_amp and scaler:
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
        else:
            loss.backward()
            optimizer.step()
        
        total_loss += loss.item() * labels.size(0)
        preds = torch.argmax(logits, dim=-1)
        all_preds.extend(preds.cpu().numpy())
        all_labels.extend(labels.cpu().numpy())
        
        if (batch_idx + 1) % 50 == 0 or (batch_idx + 1) == len(train_loader):
            print(f"   Batch {batch_idx+1}/{len(train_loader)} | Loss: {loss.item():.4f}")
    
    avg_loss = total_loss / len(train_loader.dataset)
    accuracy = accuracy_score(all_labels, all_preds)
    return avg_loss, accuracy


def validate(model, val_loader, loss_fn, device, use_amp=True):
    """Validation"""
    model.eval()
    total_loss = 0.0
    all_preds = []
    all_labels = []
    
    with torch.no_grad():
        for feats, labels in val_loader:
            feats = feats.to(device)
            labels = labels.to(device)
            
            with torch.amp.autocast('cuda', enabled=use_amp):
                logits = model(feats)
                loss = loss_fn(logits, labels)
            
            total_loss += loss.item() * labels.size(0)
            preds = torch.argmax(logits, dim=-1)
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())
    
    avg_loss = total_loss / len(val_loader.dataset)
    accuracy = accuracy_score(all_labels, all_preds)
    return avg_loss, accuracy, all_preds, all_labels


# ============================================================================
# MAIN
# ============================================================================

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--batch-size', type=int, default=32)
    parser.add_argument('--epochs', type=int, default=20)
    parser.add_argument('--lr', type=float, default=1e-3)
    parser.add_argument('--hidden-dim', type=int, default=128)
    parser.add_argument('--nheads', type=int, default=4)
    parser.add_argument('--num-layers', type=int, default=2)
    parser.add_argument('--dropout', type=float, default=0.1)
    parser.add_argument('--augment', action='store_true')
    args = parser.parse_args()
    
    print(f"[INFO] Loading MFCC features...")
    
    # Load features
    features = np.load(str(Path(BASE_DIR) / 'processed_data' / 'mfcc_features.npy'))
    labels = np.load(str(Path(BASE_DIR) / 'processed_data' / 'labels.npy'))
    train_idx = np.load(str(Path(BASE_DIR) / 'processed_data' / 'train_indices.npy'))
    val_idx = np.load(str(Path(BASE_DIR) / 'processed_data' / 'val_indices.npy'))
    
    print(f"   Features shape: {features.shape}")
    print(f"   Labels shape: {labels.shape}")
    print(f"   Train samples: {len(train_idx)}, Val samples: {len(val_idx)}")
    
    # Datasets
    train_dataset = MFCCDataset(features, labels, train_idx, augment=args.augment)
    val_dataset = MFCCDataset(features, labels, val_idx, augment=False)
    
    # Weighted sampler for training (balance classes)
    train_labels = labels[train_idx]
    class_counts = np.bincount(train_labels)
    weights = 1.0 / class_counts
    sample_weights = weights[train_labels]
    sampler = WeightedRandomSampler(sample_weights, len(sample_weights))
    
    train_loader = DataLoader(
        train_dataset, batch_size=args.batch_size, sampler=sampler,
        collate_fn=collate_fn, num_workers=0
    )
    val_loader = DataLoader(
        val_dataset, batch_size=args.batch_size, shuffle=False,
        collate_fn=collate_fn, num_workers=0
    )
    
    # Model
    print(f"[INFO] Building model...")
    model = MFCCTransformer(
        n_mfcc=features.shape[1],
        hidden_dim=args.hidden_dim,
        nheads=args.nheads,
        num_layers=args.num_layers,
        num_labels=2,
        dropout=args.dropout
    )
    model = model.to(device)
    print(f"   Model parameters: {sum(p.numel() for p in model.parameters()):,}")
    
    # Training setup
    optimizer = torch.optim.Adam(model.parameters(), lr=args.lr)
    loss_fn = nn.CrossEntropyLoss()
    use_amp = torch.cuda.is_available()
    
    print(f"\n[INFO] Starting training...")
    print(f"   Batch size: {args.batch_size}")
    print(f"   Epochs: {args.epochs}")
    print(f"   Mixed precision: {use_amp}")
    
    best_val_acc = 0.0
    history = {'train_loss': [], 'train_acc': [], 'val_loss': [], 'val_acc': []}
    
    for epoch in range(args.epochs):
        print(f"\n📍 Epoch {epoch+1}/{args.epochs}")
        
        # Train
        train_loss, train_acc = train_epoch(model, train_loader, optimizer, loss_fn, device, use_amp)
        print(f"   Train - Loss: {train_loss:.4f}, Accuracy: {train_acc:.4f}")
        
        # Validate
        val_loss, val_acc, val_preds, val_labels = validate(model, val_loader, loss_fn, device, use_amp)
        print(f"   Val   - Loss: {val_loss:.4f}, Accuracy: {val_acc:.4f}")
        
        history['train_loss'].append(train_loss)
        history['train_acc'].append(train_acc)
        history['val_loss'].append(val_loss)
        history['val_acc'].append(val_acc)
        
        # Save best model
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            model_path = Path(BASE_DIR) / 'models' / 'mfcc_transformer_best.pt'
            model_path.parent.mkdir(parents=True, exist_ok=True)
            torch.save(model.state_dict(), model_path)
            print(f"   [SAVE] Best model saved (acc={val_acc:.4f})")
        
        # GPU memory
        if torch.cuda.is_available():
            allocated = torch.cuda.memory_allocated() / 1e9
            reserved = torch.cuda.memory_reserved() / 1e9
            print(f"   GPU Memory: {allocated:.2f}GB / {reserved:.2f}GB")
    
    print(f"\n[OK] Training complete!")
    print(f"   Best validation accuracy: {best_val_acc:.4f}")
    
    # Save history
    history_path = Path(BASE_DIR) / 'models' / 'mfcc_transformer_history.json'
    with open(history_path, 'w') as f:
        json.dump(history, f, indent=2)
    
    # Final classification report
    print(f"\n[INFO] Final validation classification report:")
    print(classification_report(val_labels, val_preds, target_names=['distress', 'normal']))
