"""
Extract and cache Wav2Vec2 features for faster training
Pre-computes features from audio files to avoid expensive feature extraction during training
"""

import os
import sys
import numpy as np
import pandas as pd
import warnings
from pathlib import Path
import time
import argparse
import contextlib
import queue
import threading
from concurrent.futures import ThreadPoolExecutor

import torch
import librosa
import h5py
from transformers import Wav2Vec2Model, Wav2Vec2FeatureExtractor, logging as transformers_logging

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

transformers_logging.set_verbosity_error()
warnings.filterwarnings("ignore")

# Setup GPU
from gpu_manager import GPUManager


def parse_args():
    p = argparse.ArgumentParser(description="Extract and cache Wav2Vec2 features (GPU optimized)")
    p.add_argument('--batch-size', type=int, default=32, help='batch size for feature extraction')
    p.add_argument('--mixed-precision', action='store_true', default=True, help='use AMP on GPU')
    p.add_argument('--allow-cpu', action='store_true', help='allow CPU fallback if CUDA is unavailable')
    p.add_argument('--num-workers', type=int, default=None, help='threads for audio loading (0=disable)')
    p.add_argument('--prefetch', action=argparse.BooleanOptionalAction, default=True, help='prefetch audio batches asynchronously')
    p.add_argument('--prefetch-depth', type=int, default=2, help='prefetch queue depth')
    return p.parse_args()


def main():
    args = parse_args()

    # GPU setup (fail fast if CUDA not available)
    GPUManager.setup_pytorch_gpu()
    if not torch.cuda.is_available() and not args.allow_cpu:
        print("\nERROR: CUDA not available for PyTorch.")
        print("Fix: install the CUDA-enabled build of torch for your GPU/driver.")
        print("If you really want CPU, re-run with --allow-cpu.")
        return

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"[INFO] Using device: {device}")

    # Performance toggles
    torch.backends.cudnn.benchmark = True
    torch.backends.cuda.matmul.allow_tf32 = True
    torch.backends.cudnn.allow_tf32 = True
    try:
        torch.set_float32_matmul_precision('high')
    except Exception:
        pass

    # Load model
    print("[INFO] Loading Wav2Vec2 model...")
    model = Wav2Vec2Model.from_pretrained('facebook/wav2vec2-base')
    model = model.to(device)
    model.eval()
    feature_extractor = Wav2Vec2FeatureExtractor.from_pretrained('facebook/wav2vec2-base')

    def resolve_audio_path(p, root):
        # Normalize WSL paths to Windows paths first
        p_str = normalize_path(str(p))
        p = Path(p_str)
        return p if p.is_absolute() else (root / p)

    def load_audio(path, target_len=80000, sr=16000):
        try:
            import soundfile as sf
            wav, file_sr = sf.read(str(path), dtype='float32', always_2d=False)
            if wav.ndim > 1:
                wav = np.mean(wav, axis=1)
            if file_sr != sr:
                wav = librosa.resample(wav, orig_sr=file_sr, target_sr=sr, res_type='kaiser_fast')
        except Exception:
            wav, _ = librosa.load(str(path), sr=sr, mono=True, res_type='kaiser_fast')

        if len(wav) > target_len:
            wav = wav[:target_len]
        else:
            wav = np.pad(wav, (0, target_len - len(wav)))
        return wav

    # Load metadata
    metadata_path = Path(BASE_DIR) / 'processed_data' / 'metadata.csv'
    audio_root = Path(BASE_DIR)
    output_dir = Path(BASE_DIR) / 'processed_data' / 'wav2vec2_cached'
    output_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(metadata_path)
    print(f"[INFO] Loading {len(df)} samples...")
    print(f"[INFO] Saving to HDF5 format (memory efficient)...")

    # Use HDF5 for memory efficiency (handles variable-length sequences)
    h5_path = output_dir / 'features.h5'
    all_labels = []
    all_filenames = []

    batch_size = args.batch_size
    num_workers = args.num_workers if args.num_workers is not None else min(4, os.cpu_count() or 4)
    if num_workers < 0:
        num_workers = 0
    prefetch = args.prefetch
    prefetch_depth = max(1, args.prefetch_depth)

    num_batches = (len(df) + batch_size - 1) // batch_size

    start_time = time.time()

    with h5py.File(str(h5_path), 'w') as h5f:
        use_amp = args.mixed_precision and torch.cuda.is_available()
        if use_amp:
            print("[INFO] Using mixed precision (AMP)")
        autocast_ctx = torch.autocast('cuda', enabled=use_amp) if torch.cuda.is_available() else contextlib.nullcontext()

        executor = ThreadPoolExecutor(max_workers=num_workers) if num_workers > 0 else None

        def load_audio_batch(audio_paths):
            if executor:
                return list(executor.map(load_audio, audio_paths))
            return [load_audio(p) for p in audio_paths]

        def iter_batches():
            for batch_idx in range(num_batches):
                start = batch_idx * batch_size
                end = min(start + batch_size, len(df))
                batch_df = df.iloc[start:end]
                audio_paths = [resolve_audio_path(row['path'], audio_root) for _, row in batch_df.iterrows()]
                audio_batch = load_audio_batch(audio_paths)
                yield batch_idx, start, end, batch_df, audio_batch

        def prefetch_batches():
            q = queue.Queue(maxsize=prefetch_depth)
            stop = object()
            exc = []

            def producer():
                try:
                    for item in iter_batches():
                        q.put(item)
                except Exception as e:
                    exc.append(e)
                finally:
                    q.put(stop)

            t = threading.Thread(target=producer, daemon=True)
            t.start()
            while True:
                item = q.get()
                if item is stop:
                    if exc:
                        raise exc[0]
                    break
                yield item

        batch_iter = prefetch_batches() if prefetch else iter_batches()

        with torch.inference_mode():
            for batch_idx, start, end, batch_df, audio_batch in batch_iter:

                # Extract features
                audio_batch = np.array(audio_batch)
                inputs = feature_extractor(audio_batch, sampling_rate=16000, return_tensors='pt', padding=True)
                input_values = inputs['input_values'].to(device, non_blocking=True)

                with autocast_ctx:
                    outputs = model(input_values)
                    features = outputs.last_hidden_state.cpu().numpy()  # (batch, seq_len, 768)

                # Save features to HDF5 incrementally
                for local_idx, (_, row) in enumerate(batch_df.iterrows()):
                    feat = features[local_idx]  # (seq_len, 768) - use local index
                    sample_num = start + local_idx  # Correct global sample number
                    group = h5f.create_group(f'sample_{sample_num}')
                    group.create_dataset('features', data=feat)

                    # Convert label to int (match config LABEL_MAP: normal=0, distress=1)
                    if 'label_id' in row and not pd.isna(row['label_id']):
                        label_val = int(row['label_id'])
                    else:
                        label_val = row['label']
                        if isinstance(label_val, str):
                            label_val = LABEL_MAP.get(label_val.lower(), 0)
                        else:
                            label_val = int(label_val)
                    group.attrs['label'] = label_val

                    all_labels.append(label_val)
                    all_filenames.append(normalize_path(row['path']))

                elapsed = time.time() - start_time
                rate = (end / elapsed) if elapsed > 0 else 0
                print(f"[{batch_idx+1}/{num_batches}] Processed {end}/{len(df)} samples ({rate:.1f} samples/s)")

        if executor:
            executor.shutdown(wait=True)

    # Save labels and metadata
    print("[INFO] Saving labels and metadata...")
    np.save(str(output_dir / 'labels.npy'), np.array(all_labels))

    metadata_df = pd.DataFrame({
        'path': all_filenames,
        'label': all_labels
    })
    metadata_df.to_csv(output_dir / 'metadata.csv', index=False)

    print(f"[OK] Features saved to {output_dir}/features.h5")
    print(f"[OK] Labels saved to {output_dir}/labels.npy")
    print(f"[INFO] Total time: {time.time() - start_time:.1f}s")
    print(f"[INFO] HDF5 file size: {h5_path.stat().st_size / 1e9:.2f} GB")


if __name__ == '__main__':
    main()
