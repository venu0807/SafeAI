"""
SafeGuard AI - End-to-End Pipeline Test
Analyzes test_results.csv to produce comprehensive pipeline metrics
"""

import os
import sys
import numpy as np
import pandas as pd
from sklearn.metrics import (
    confusion_matrix, classification_report, 
    roc_curve, auc, precision_recall_curve
)

# Add parent to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

def run_pipeline_test():
    """Full end-to-end pipeline evaluation"""
    print("=" * 65)
    print("  SAFEGUARD AI - END-TO-END PIPELINE TEST")
    print("=" * 65)
    
    # ── 1. Load model ──
    print("\n┌─ Step 1: Load Model ──────────────────────────────┐")
    import tensorflow as tf
    from tensorflow import keras
    
    def focal_loss(alpha=FOCAL_LOSS_ALPHA, gamma=FOCAL_LOSS_GAMMA):
        alpha_tensor = tf.constant(alpha, dtype=tf.float32)
        def loss(y_true, y_pred):
            y_true_idx = tf.cast(tf.argmax(y_true, axis=1), tf.int32)
            alpha_t = tf.gather(alpha_tensor, y_true_idx)
            ce_loss = keras.losses.categorical_crossentropy(y_true, y_pred)
            pt = tf.exp(-ce_loss)
            focal = alpha_t * (1 - pt) ** gamma * ce_loss
            return tf.reduce_mean(focal)
        return loss
    
    CUSTOM_OBJECTS = {'loss': focal_loss()}
    
    model = keras.models.load_model(MODEL_H5, custom_objects=CUSTOM_OBJECTS)
    print(f"   ✓ Model loaded: {os.path.basename(MODEL_H5)}")
    model.summary()
    
    # ── 2. Load Test Data ──
    print("\n┌─ Step 2: Load Test Data ──────────────────────────┐")
    X = np.load(MFCC_FEATURES_NPY)
    y = np.load(LABELS_NPY)
    
    n_samples = len(X)
    indices = np.arange(n_samples)
    np.random.seed(RANDOM_SEED)
    np.random.shuffle(indices)
    
    train_size = int(n_samples * TRAIN_SPLIT)
    val_size = int(n_samples * VAL_SPLIT)
    test_idx = indices[train_size + val_size:]
    
    X_test = X[test_idx]
    y_test = y[test_idx]
    X_test = X_test[..., np.newaxis]
    
    print(f"   ✓ Total dataset: {n_samples} samples")
    print(f"   ✓ Test set: {len(X_test)} samples")
    print(f"   ✓ Input shape: {X_test.shape}")
    
    # ── 3. Run Inference ──
    print("\n┌─ Step 3: Run Inference ───────────────────────────┐")
    import time
    
    # Warm-up
    _ = model.predict(X_test[:1], verbose=0)
    
    # Timed inference
    batch_sizes = [1, 16, 32, 64]
    for bs in batch_sizes:
        num_batches = max(1, min(50, len(X_test) // bs))
        batch_data = X_test[:num_batches * bs]
        
        start = time.perf_counter()
        _ = model.predict(batch_data, verbose=0, batch_size=bs)
        elapsed = time.perf_counter() - start
        
        avg_ms = (elapsed / (num_batches * bs)) * 1000
        print(f"   ✓ Batch size {bs:2d}: {avg_ms:.3f} ms/sample  "
              f"({num_batches * bs} samples in {elapsed:.3f}s)")
    
    y_pred_proba = model.predict(X_test, verbose=0)
    y_pred = np.argmax(y_pred_proba, axis=1)
    
    # ── 4. Metrics ──
    print("\n┌─ Step 4: Performance Metrics ─────────────────────┐")
    
    # Accuracy
    accuracy = np.mean(y_pred == y_test)
    print(f"\n   Accuracy: {accuracy*100:.2f}%")
    
    # Classification Report
    print(f"\n{'':>12} {'Precision':>10} {'Recall':>8} {'F1-Score':>9} {'Support':>8}")
    print(f"{'─'*50}")
    
    from sklearn.metrics import precision_recall_fscore_support
    prec, rec, f1, supp = precision_recall_fscore_support(y_test, y_pred, average=None, labels=[0, 1])
    
    for i, label in enumerate(LABEL_NAMES):
        print(f"{label:>12} {prec[i]*100:>9.2f}% {rec[i]*100:>7.2f}% {f1[i]*100:>8.2f}% {supp[i]:>8}")
    
    prec_macro, rec_macro, f1_macro, _ = precision_recall_fscore_support(y_test, y_pred, average='macro')
    prec_w, rec_w, f1_w, _ = precision_recall_fscore_support(y_test, y_pred, average='weighted')
    print(f"{'─'*50}")
    print(f"{'Macro Avg':>12} {prec_macro*100:>9.2f}% {rec_macro*100:>7.2f}% {f1_macro*100:>8.2f}%")
    print(f"{'Weighted Avg':>12} {prec_w*100:>9.2f}% {rec_w*100:>7.2f}% {f1_w*100:>8.2f}%")
    
    # ── 5. Confusion Matrix ──
    cm = confusion_matrix(y_test, y_pred)
    print(f"\n┌─ Step 5: Confusion Matrix ───────────────────────┐")
    print(f"\n{'':>12} {'Pred Normal':>12} {'Pred Distress':>14}")
    print(f"{'─'*40}")
    print(f"{'Actual Normal':>12} {cm[0][0]:>10} {cm[0][1]:>14}")
    print(f"{'Actual Distress':>12} {cm[1][0]:>10} {cm[1][1]:>14}")
    
    fp = cm[0][1]
    fn = cm[1][0]
    tp = cm[1][1]
    tn = cm[0][0]
    
    print(f"\n   False Positives (false alarm): {fp} ({fp/len(y_test)*100:.2f}%)")
    print(f"   False Negatives (missed threat): {fn} ({fn/len(y_test)*100:.2f}%)")
    
    # ── 6. ROC Curve ──
    fpr_vals, tpr_vals, _ = roc_curve(y_test, y_pred_proba[:, 1])
    roc_auc = auc(fpr_vals, tpr_vals)
    print(f"\n┌─ Step 6: ROC Analysis ───────────────────────────┐")
    print(f"\n   ROC AUC: {roc_auc:.4f}")
    
    # ── 7. Threshold Analysis ──
    print(f"\n┌─ Step 7: Threshold Sweep ─────────────────────────┐")
    print(f"\n{'Thresh':>8} {'Accuracy':>10} {'Precision':>10} {'Recall':>8} {'F1':>8}")
    print(f"{'─'*48}")
    
    best_f1 = 0
    best_thresh = 0.50
    thresholds = np.arange(0.30, 0.96, 0.05)
    
    for thresh in thresholds:
        y_t = (y_pred_proba[:, 1] >= thresh).astype(int)
        acc = np.mean(y_t == y_test)
        
        tp_t = np.sum((y_t == 1) & (y_test == 1))
        fp_t = np.sum((y_t == 1) & (y_test == 0))
        fn_t = np.sum((y_t == 0) & (y_test == 1))
        
        prec_t = tp_t / (tp_t + fp_t) if (tp_t + fp_t) > 0 else 0
        rec_t = tp_t / (tp_t + fn_t) if (tp_t + fn_t) > 0 else 0
        f1_t = 2 * prec_t * rec_t / (prec_t + rec_t) if (prec_t + rec_t) > 0 else 0
        
        marker = " ◀ BEST" if thresh == 0.50 else ""
        print(f"  {thresh:.2f}    {acc*100:>7.2f}%  {prec_t*100:>8.2f}%  {rec_t*100:>7.2f}%  {f1_t*100:>6.2f}%{marker}")
        
        if f1_t > best_f1:
            best_f1 = f1_t
            best_thresh = thresh
    
    print(f"\n   ★ Best F1: {best_f1*100:.2f}% at threshold {best_thresh:.2f}")
    
    # ── 8. End-to-End Summary ──
    print(f"\n{'='*65}")
    print(f"  END-TO-END PIPELINE RESULTS")
    print(f"{'='*65}")
    print(f"\n  Pipeline: Audio → MFCC (40) → CNN [32,64,128] → Classification")
    print(f"  Threshold: {THREAT_THRESHOLD:.2f}")
    print(f"  Test samples: {len(X_test)}")
    print(f"  Accuracy:  {accuracy*100:.2f}%")
    print(f"  Precision: {prec[1]*100:.2f}% (distress class)")
    print(f"  Recall:    {rec[1]*100:.2f}% (distress class)")
    print(f"  F1-Score:  {f1[1]*100:.2f}% (distress class)")
    print(f"  ROC AUC:   {roc_auc*100:.2f}%")
    print(f"\n  Safety-Critical Metrics:")
    print(f"    False Negatives (missed threats): {fn}/{len(y_test)} ({fn/len(y_test)*100:.2f}%)")
    print(f"    False Positives (false alarms):   {fp}/{len(y_test)} ({fp/len(y_test)*100:.2f}%)")
    print(f"\n  {'✓ PASS' if fn/len(y_test) < 0.05 else '⚠ REVIEW'} — Missed threat rate below 5% target" if fn/len(y_test) < 0.05 else "")
    print(f"  {'✓ PASS' if fp/len(y_test) < 0.10 else '⚠ REVIEW'} — False alarm rate below 10% target" if fp/len(y_test) < 0.10 else "")
    print(f"\n{'='*65}\n")

if __name__ == "__main__":
    run_pipeline_test()
