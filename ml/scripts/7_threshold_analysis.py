"""
Threshold analysis for SafeGuard AI.

This script loads the test set, runs the trained Keras model (or the exported TFLite model),
computes prediction probabilities, and evaluates precision, recall, accuracy and F1‑score
for a range of decision thresholds.  The output is a concise table that helps you pick the
optimal operating point for the real‑time Android app.
"""

import os
import sys
import json
import numpy as np
from sklearn.metrics import precision_score, recall_score, f1_score, accuracy_score
import tensorflow as tf
from tensorflow import keras

# Add project root to path so we can import config
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import (
    MFCC_FEATURES_NPY,
    LABELS_NPY,
    TRAIN_SPLIT,
    VAL_SPLIT,
    RANDOM_SEED,
    MODEL_H5,
    MODEL_TFLITE,
    TFLITE_QUANTIZATION,
    FOCAL_LOSS_ALPHA,
    FOCAL_LOSS_GAMMA,
)


# Focal Loss (must be defined for model loading)
def focal_loss(alpha=FOCAL_LOSS_ALPHA, gamma=FOCAL_LOSS_GAMMA):
    """Focal Loss for imbalanced classification."""
    if alpha is None:
        alpha = [1.0, 1.0]
    alpha_tensor = tf.constant(alpha, dtype=tf.float32)
    def loss(y_true, y_pred):
        y_pred = tf.clip_by_value(y_pred, tf.keras.backend.epsilon(), 1.0 - tf.keras.backend.epsilon())
        y_true_idx = tf.argmax(y_true, axis=1)
        batch_size = tf.shape(y_pred)[0]
        indices = tf.stack([tf.range(batch_size), tf.cast(y_true_idx, tf.int32)], axis=1)
        p_t = tf.gather_nd(y_pred, indices)
        ce_loss = -tf.math.log(p_t)
        modulating_factor = tf.pow(1.0 - p_t, gamma)
        alpha_t = tf.gather(alpha_tensor, tf.cast(y_true_idx, tf.int32))
        focal = alpha_t * modulating_factor * ce_loss
        return tf.reduce_mean(focal)
    return loss


def load_test_data():
    """Load the test split dynamically (same logic as in export/evaluation scripts)."""
    X = np.load(MFCC_FEATURES_NPY)
    y = np.load(LABELS_NPY)
    n_samples = len(X)
    indices = np.arange(n_samples)
    np.random.seed(RANDOM_SEED)
    np.random.shuffle(indices)
    train_size = int(n_samples * TRAIN_SPLIT)
    val_size = int(n_samples * VAL_SPLIT)
    test_idx = indices[train_size + val_size :]
    X_test = X[test_idx]
    y_test = y[test_idx]
    # Add channel dimension expected by the CNN
    X_test = X_test[..., np.newaxis].astype(np.float32)
    return X_test, y_test

def load_keras_model():
    if not os.path.exists(MODEL_H5):
        raise FileNotFoundError(f"Keras model not found at {MODEL_H5}")
    return tf.keras.models.load_model(
        MODEL_H5,
        custom_objects={'loss': focal_loss()}
    )

def load_tflite_interpreter():
    if not os.path.exists(MODEL_TFLITE):
        raise FileNotFoundError(f"TFLite model not found at {MODEL_TFLITE}")
    interpreter = tf.lite.Interpreter(model_path=MODEL_TFLITE)
    interpreter.allocate_tensors()
    return interpreter

def predict_with_keras(model, X):
    probs = model.predict(X, verbose=0)
    # model outputs softmax probabilities for two classes
    return probs[:, 1]  # probability of the "Distress" class

def predict_with_tflite(interpreter, X):
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    probs = []
    for i in range(len(X)):
        inp = X[i : i + 1]
        # Handle possible int8 quantisation
        if TFLITE_QUANTIZATION == "int8":
            scale, zero_point = input_details[0]["quantization"]
            inp = inp / scale + zero_point
            inp = inp.astype(np.int8)
        interpreter.set_tensor(input_details[0]["index"], inp)
        interpreter.invoke()
        out = interpreter.get_tensor(output_details[0]["index"])
        if TFLITE_QUANTIZATION == "int8":
            scale, zero_point = output_details[0]["quantization"]
            out = (out.astype(np.float32) - zero_point) * scale
        probs.append(out[0, 1])  # probability of class 1 (Distress)
    return np.array(probs)

def evaluate_thresholds(y_true, probs, thresholds):
    rows = []
    for thr in thresholds:
        y_pred = (probs >= thr).astype(int)
        acc = accuracy_score(y_true, y_pred)
        prec = precision_score(y_true, y_pred, zero_division=0)
        rec = recall_score(y_true, y_pred, zero_division=0)
        f1 = f1_score(y_true, y_pred, zero_division=0)
        rows.append((thr, acc, prec, rec, f1))
    return rows

def main(use_tflite=False):
    X_test, y_test = load_test_data()
    if use_tflite:
        interpreter = load_tflite_interpreter()
        probs = predict_with_tflite(interpreter, X_test)
    else:
        model = load_keras_model()
        probs = predict_with_keras(model, X_test)

    thresholds = np.arange(0.5, 0.95 + 0.05, 0.05)
    results = evaluate_thresholds(y_test, probs, thresholds)

    print("\nThreshold analysis (probability of Distress class)\n")
    print(f"{'Thresh':>6} | {'Acc':>6} | {'Prec':>6} | {'Rec':>6} | {'F1':>6}")
    print("-" * 38)
    for thr, acc, prec, rec, f1 in results:
        print(f"{thr:6.2f} | {acc:6.2%} | {prec:6.2%} | {rec:6.2%} | {f1:6.2%}")
    # Show the best F1‑score threshold
    best = max(results, key=lambda r: r[4])
    print("\nBest F1‑score at threshold {0:.2f}: {1:.2%}".format(best[0], best[4]))
    
    # Also find best recall (safety-critical — minimize missed threats)
    best_recall = max(results, key=lambda r: r[3])
    print("Best recall at threshold {0:.2f}: {1:.2%}".format(best_recall[0], best_recall[3]))
    
    # Save results to JSON
    results_dict = {
        'thresholds': [{'threshold': float(t), 'accuracy': float(a), 'precision': float(p), 'recall': float(r), 'f1': float(f)} for t, a, p, r, f in results],
        'best_f1': {'threshold': float(best[0]), 'f1': float(best[4])},
        'best_recall': {'threshold': float(best_recall[0]), 'recall': float(best_recall[3])}
    }
    results_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'results', 'threshold_analysis.json')
    os.makedirs(os.path.dirname(results_path), exist_ok=True)
    with open(results_path, 'w') as f:
        json.dump(results_dict, f, indent=2)
    print(f"\n✅ Results saved to: {results_path}")
    
    # Return best threshold
    return best[0]

if __name__ == "__main__":
    # Set use_tflite=False to evaluate the Keras model (faster for many samples).
    # Set use_tflite=True if you want to verify the exact TFLite inference behaviour.
    main(use_tflite=False)
