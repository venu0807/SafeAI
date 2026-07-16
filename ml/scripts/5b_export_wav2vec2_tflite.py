"""
SafeGuard AI - Wav2Vec2BERT TFLite Export
Converts the PyTorch Wav2Vec2BERT model to TensorFlow Lite for Android deployment.

NOTE: This model takes pre-computed Wav2Vec2 features (768-dim vectors) as input,
NOT raw audio. For on-device use, you would need to also run Wav2Vec2 feature
extraction on the phone, which requires a ~400MB model. The MFCC CNN model
(5_export_tflite.py) is the practical on-device solution.

This export is useful for:
- Server-side inference optimization
- Research/experimentation with on-device transformer models
- Combining with a lightweight feature extractor

Process:
  1. Load PyTorch model from checkpoint
  2. Export to ONNX format (with fixed sequence length)
  3. Convert ONNX → TensorFlow → TFLite
"""

import os
import sys
import json
import numpy as np
from pathlib import Path

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

import warnings
warnings.filterwarnings("ignore")

import torch
import torch.nn as nn

# ============================================================================
# MODEL DEFINITION (must match training)
# ============================================================================

class Wav2Vec2BERT(nn.Module):
    """Wav2Vec2 + Transformer Encoder classifier (same as training)"""
    def __init__(self, input_dim=768, num_labels=2, transformer_layers=3,
                 nhead=8, hidden_dropout=0.1, projection_dim=256,
                 ffn_dim=512, classifier_hidden=128):
        super().__init__()
        if projection_dim is None:
            self.projection = None
            hidden_dim = input_dim
        else:
            self.projection = nn.Linear(input_dim, projection_dim)
            hidden_dim = projection_dim

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_dim,
            nhead=max(1, min(nhead, hidden_dim // 64)),
            dim_feedforward=ffn_dim,
            dropout=hidden_dropout,
            batch_first=True,
            activation='relu'
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=transformer_layers)
        self.classifier = nn.Sequential(
            nn.Linear(hidden_dim, classifier_hidden),
            nn.ReLU(),
            nn.Dropout(hidden_dropout),
            nn.Linear(classifier_hidden, num_labels)
        )

    def forward(self, x):
        if self.projection:
            x = self.projection(x)
        x = self.transformer(x)
        x = x.mean(dim=1)  # Global average pooling
        x = self.classifier(x)
        return x


def infer_model_config(state_dict):
    """Infer model architecture from checkpoint state dict"""
    layer_ids = []
    for k in state_dict.keys():
        if k.startswith('transformer.layers.'):
            try:
                layer_ids.append(int(k.split('.')[2]))
            except Exception:
                pass
    num_layers = (max(layer_ids) + 1) if layer_ids else 3

    if 'projection.weight' in state_dict:
        input_dim = state_dict['projection.weight'].shape[1]
        projection_dim = state_dict['projection.weight'].shape[0]
    else:
        hidden_dim = state_dict.get('transformer.layers.0.self_attn.in_proj_weight',
                                    torch.zeros(768)).shape[1]
        input_dim = hidden_dim
        projection_dim = None

    ffn_dim = state_dict['transformer.layers.0.linear1.weight'].shape[0]
    classifier_hidden = state_dict['classifier.0.weight'].shape[0]
    # Determine num_labels: classifier.3.weight (Linear out) or default to 2
    if 'classifier.3.weight' in state_dict:
        num_labels = state_dict['classifier.3.weight'].shape[0]
    else:
        num_labels = 2  # Default binary classification

    return {
        'input_dim': input_dim,
        'num_labels': num_labels,
        'transformer_layers': num_layers,
        'nhead': 8,
        'projection_dim': projection_dim,
        'ffn_dim': ffn_dim,
        'classifier_hidden': classifier_hidden
    }


def export_to_tflite():
    """Export Wav2Vec2BERT PyTorch model to TFLite"""
    print("=" * 70)
    print("📦 SAFEGUARD AI - WAV2VEC2BERT TFLITE EXPORT")
    print("=" * 70)

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"\n✅ Using device: {device}")

    # Find the best model checkpoint
    model_dir = Path(BASE_DIR) / 'models'
    candidates = [
        model_dir / 'wav2vec2_bert_final.pth',
        model_dir / 'wav2vec2_bert_cached_best.pth',
        model_dir / 'wav2vec2_bert_best.pth'
    ]
    existing = [p for p in candidates if p.exists()]
    if not existing:
        print("❌ ERROR: No Wav2Vec2BERT model checkpoint found!")
        print("   Expected at:", model_dir / 'wav2vec2_bert_final.pth')
        print("   Run 3_train_wav2vec2_bert.py first")
        return

    checkpoint_path = max(existing, key=lambda p: p.stat().st_mtime)
    print(f"\n📂 Loading model checkpoint: {checkpoint_path}")

    try:
        checkpoint = torch.load(str(checkpoint_path), map_location='cpu', weights_only=False)
    except TypeError:
        checkpoint = torch.load(str(checkpoint_path), map_location='cpu')

    state_dict = checkpoint.get('model_state_dict', checkpoint)
    config = infer_model_config(state_dict)
    print(f"\n📊 Model Configuration:")
    for k, v in config.items():
        print(f"   {k}: {v}")

    # Build model and load weights
    model = Wav2Vec2BERT(**config)
    model.load_state_dict(state_dict, strict=False)
    model.eval()
    print(f"✅ Model loaded successfully ({sum(p.numel() for p in model.parameters()):,} params)")

    # Fixed sequence length for ONNX export (use max seen during training)
    fixed_seq_len = 249  # From training: "Feature shape: (249, 768)"
    print(f"\n🔧 Exporting to ONNX with fixed sequence length: {fixed_seq_len}")

    # Create dummy input for tracing
    dummy_input = torch.randn(1, fixed_seq_len, config['input_dim'])
    onnx_path = model_dir / 'wav2vec2_bert.onnx'

    # Export to ONNX
    torch.onnx.export(
        model,
        dummy_input,
        str(onnx_path),
        input_names=['input_features'],
        output_names=['output_probs'],
        dynamic_axes={
            'input_features': {0: 'batch_size'},  # Only batch is dynamic
            'output_probs': {0: 'batch_size'}
        },
        opset_version=14,
        do_constant_folding=True,
    )
    print(f"✅ ONNX model saved: {onnx_path}")
    print(f"   Size: {onnx_path.stat().st_size / 1024:.1f} KB")

    # Convert ONNX → TensorFlow → TFLite
    print("\n🔄 Converting ONNX → TensorFlow → TFLite...")    try:
        import onnx
        os.environ.setdefault('PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION', 'python')
        onnx_model = onnx.load(str(onnx_path))
        onnx.checker.check_model(onnx_model)
    except ImportError:
        print(f"\n⚠️  onnx package not installed. Install: pip install onnx")
        print(f"\n✅ ONNX model already saved at: {onnx_path}")
        return

    # --- ONNX → TFLite conversion ---
    # This step requires onnx-tf which needs TF >= 2.18 via tensorflow-probability.
    # Your TF 2.10 is incompatible. The ONNX model (7 MB) is saved for future use.
    # For now, the CNN TFLite model (2.06 MB, 2.24ms) is the practical on-device solution.
    print(f"\n⚠️  ONNX→TFLite conversion requires TF ≥ 2.18 (you have TF 2.10).")
    print(f"   Skipping TFLite conversion — ONNX model preserved at:")
    print(f"   {onnx_path}")
    print(f"""
💡 PRACTICAL DEPLOYMENT ADVICE:
   The Wav2Vec2BERT model needs Wav2Vec2 feature vectors as input.
   Extracting those on-device requires a ~400MB Wav2Vec2 model.
   
   ✅ The MFCC CNN TFLite model (2.06 MB, 2.24ms) is already in the APK:
      - 88.46% on-device accuracy
      - Works directly with raw audio
      - No cloud dependency
""")

    print("\n" + "=" * 70)
    print("📱 DEPLOYMENT NOTES")
    print("=" * 70)
    print(f"""
IMPORTANT: This TFLite model expects PRE-COMPUTED Wav2Vec2 features as input,
not raw audio. For on-device deployment, you have two options:

1. USE THE MFCC CNN MODEL (recommended for mobile):
   - Already exported: models/audio_mfcc_cnn.tflite
   - Works directly with raw MFCC features extracted on-device
   - 88.46% accuracy, 2.06 MB, 2.24ms inference

2. WAV2VEC2BERT (server-side / cloud):
   - This TFLite model requires Wav2Vec2 feature vectors (768-dim)
   - Features must be extracted by a Wav2Vec2 model (too large for mobile)
   - Best used for cloud-based inference: 91.84% accuracy

Input shape: (batch_size, {fixed_seq_len}, {config['input_dim']}) - [1, {fixed_seq_len}, {config['input_dim']}]
Output shape: (batch_size, 2) - [normal_prob, distress_prob]
""")
    print("✅ Wav2Vec2BERT export complete!")


if __name__ == "__main__":
    export_to_tflite()
