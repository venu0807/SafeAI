"""
SafeGuard AI - Model Testing & Evaluation
Comprehensive model evaluation with visualizations
"""

import os
import sys
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import (
    confusion_matrix, classification_report, 
    roc_curve, auc, precision_recall_curve
)
import tensorflow as tf
from tensorflow import keras

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *


def focal_loss(alpha=FOCAL_LOSS_ALPHA, gamma=FOCAL_LOSS_GAMMA):
    """Focal loss for imbalanced classification (same as training)."""
    alpha_tensor = tf.constant(alpha, dtype=tf.float32)

    def loss(y_true, y_pred):
        y_true_idx = tf.cast(tf.argmax(y_true, axis=1), tf.int32)
        alpha_t = tf.gather(alpha_tensor, y_true_idx)
        ce_loss = tf.keras.losses.categorical_crossentropy(y_true, y_pred)
        pt = tf.exp(-ce_loss)
        focal = alpha_t * (1 - pt) ** gamma * ce_loss
        return tf.reduce_mean(focal)

    return loss


CUSTOM_OBJECTS = {'loss': focal_loss()}

def plot_confusion_matrix(y_true, y_pred, labels):
    """Plot confusion matrix"""
    cm = confusion_matrix(y_true, y_pred)
    
    plt.figure(figsize=(8, 6))
    sns.heatmap(
        cm, annot=True, fmt='d', cmap='Blues',
        xticklabels=labels, yticklabels=labels,
        cbar_kws={'label': 'Count'}
    )
    plt.title('Confusion Matrix', fontsize=16, fontweight='bold')
    plt.ylabel('True Label', fontsize=12)
    plt.xlabel('Predicted Label', fontsize=12)
    plt.tight_layout()
    plt.savefig(CONFUSION_MATRIX_PNG, dpi=300, bbox_inches='tight')
    print(f"✅ Confusion matrix saved: {CONFUSION_MATRIX_PNG}")
    plt.close()

def plot_roc_curve(y_true, y_pred_proba):
    """Plot ROC curve"""
    fpr, tpr, thresholds = roc_curve(y_true, y_pred_proba[:, 1])
    roc_auc = auc(fpr, tpr)
    
    plt.figure(figsize=(8, 6))
    plt.plot(fpr, tpr, color='darkorange', lw=2, 
             label=f'ROC curve (AUC = {roc_auc:.3f})')
    plt.plot([0, 1], [0, 1], color='navy', lw=2, linestyle='--', label='Random')
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel('False Positive Rate', fontsize=12)
    plt.ylabel('True Positive Rate', fontsize=12)
    plt.title('Receiver Operating Characteristic (ROC) Curve', 
              fontsize=14, fontweight='bold')
    plt.legend(loc="lower right", fontsize=10)
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig(ROC_CURVE_PNG, dpi=300, bbox_inches='tight')
    print(f"✅ ROC curve saved: {ROC_CURVE_PNG}")
    plt.close()
    
    return roc_auc

def test_model():
    """Comprehensive model testing"""
    print("=" * 60)
    print("📊 SAFEGUARD AI - MODEL EVALUATION")
    print("=" * 60)
    
    # Check if model exists
    if not os.path.exists(MODEL_H5):
        print("❌ ERROR: Trained model not found!")
        print("   Run 3_train_mfcc_cnn.py first")
        return
    
    # Load model
    print("\n📂 Loading trained model...")
    model = keras.models.load_model(MODEL_H5, custom_objects=CUSTOM_OBJECTS)
    print(f"   ✅ Model loaded from: {MODEL_H5}")
    
    # Load test data
    print("\n📂 Loading test data...")
    X = np.load(MFCC_FEATURES_NPY)
    y = np.load(LABELS_NPY)
    
    # Generate test split dynamically based on actual data size
    n_samples = len(X)
    indices = np.arange(n_samples)
    np.random.seed(RANDOM_SEED)
    np.random.shuffle(indices)
    
    # Calculate split points (same as in training)
    train_size = int(n_samples * TRAIN_SPLIT)
    val_size = int(n_samples * VAL_SPLIT)
    test_idx = indices[train_size + val_size:]
    
    X_test = X[test_idx]
    y_test = y[test_idx]
    
    # Reshape for CNN
    X_test = X_test[..., np.newaxis]
    
    print(f"   Test samples: {len(X_test)}")
    print(f"   Shape: {X_test.shape}")
    
    # Predictions
    print("\n🔮 Making predictions...")
    y_pred_proba = model.predict(X_test, verbose=0)
    y_pred = np.argmax(y_pred_proba, axis=1)
    
    # Accuracy
    accuracy = np.mean(y_pred == y_test)
    print(f"\n🎯 Test Accuracy: {accuracy*100:.2f}%")
    
    # Classification Report
    print("\n" + "=" * 60)
    print("📋 CLASSIFICATION REPORT")
    print("=" * 60)
    report = classification_report(
        y_test, y_pred, 
        target_names=LABEL_NAMES,
        digits=4
    )
    print(report)
    
    # Confusion Matrix
    print("\n📊 Generating confusion matrix...")
    plot_confusion_matrix(y_test, y_pred, LABEL_NAMES)
    
    # ROC Curve
    print("\n📈 Generating ROC curve...")
    roc_auc = plot_roc_curve(y_test, y_pred_proba)
    
    # Per-sample results
    print("\n💾 Saving detailed results...")
    results_df = pd.DataFrame({
        'true_label': y_test,
        'predicted_label': y_pred,
        'normal_prob': y_pred_proba[:, 0],
        'distress_prob': y_pred_proba[:, 1],
        'correct': y_test == y_pred
    })
    
    results_df.to_csv(TEST_RESULTS_CSV, index=False)
    print(f"   ✅ Results saved: {TEST_RESULTS_CSV}")
    
    # Error analysis
    print("\n" + "=" * 60)
    print("🔍 ERROR ANALYSIS")
    print("=" * 60)
    
    false_positives = np.sum((y_pred == 1) & (y_test == 0))
    false_negatives = np.sum((y_pred == 0) & (y_test == 1))
    
    print(f"False Positives: {false_positives} ({false_positives/len(y_test)*100:.2f}%)")
    print(f"False Negatives: {false_negatives} ({false_negatives/len(y_test)*100:.2f}%)")
    
    # Find most confident errors
    errors = results_df[results_df['correct'] == False]
    if len(errors) > 0:
        print(f"\nTotal Errors: {len(errors)}")
        print("\nMost confident errors (top 5):")
        errors['confidence'] = errors[['normal_prob', 'distress_prob']].max(axis=1)
        top_errors = errors.nlargest(5, 'confidence')
        print(top_errors[['true_label', 'predicted_label', 'confidence']])
    
    # Detection threshold analysis
    print("\n" + "=" * 60)
    print("🎚️  THRESHOLD ANALYSIS")
    print("=" * 60)
    
    thresholds = [0.5, 0.6, 0.7, 0.8, 0.9]
    print(f"{'Threshold':<12} {'Accuracy':<12} {'Precision':<12} {'Recall':<12}")
    print("-" * 50)
    
    for thresh in thresholds:
        y_pred_thresh = (y_pred_proba[:, 1] >= thresh).astype(int)
        acc = np.mean(y_pred_thresh == y_test)
        
        tp = np.sum((y_pred_thresh == 1) & (y_test == 1))
        fp = np.sum((y_pred_thresh == 1) & (y_test == 0))
        fn = np.sum((y_pred_thresh == 0) & (y_test == 1))
        
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        
        print(f"{thresh:<12.1f} {acc:<12.4f} {precision:<12.4f} {recall:<12.4f}")
    
    # Summary
    print("\n" + "=" * 60)
    print("📊 FINAL SUMMARY")
    print("=" * 60)
    print(f"Test Accuracy: {accuracy*100:.2f}%")
    print(f"ROC AUC: {roc_auc:.4f}")
    print(f"Total Errors: {len(errors)} / {len(y_test)}")
    print(f"\n✅ Model evaluation complete!")
    print("▶️  Next step: Run 5_export_tflite.py")

if __name__ == "__main__":
    test_model()
