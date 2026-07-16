"""
SafeGuard AI - CNN Model Training (Improved)
Trains MFCC-based CNN for audio threat detection
Improvements: Focal Loss, L2 regularization, SpecAugment, fixed class weights, oversampling
"""

import os
import sys
import json
import numpy as np
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix

# TensorFlow/Keras
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers, models, regularizers
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *
from gpu_manager import GPUManager

# Setup GPU
GPUManager.setup_tensorflow_gpu()

# Set random seeds
np.random.seed(RANDOM_SEED)
tf.random.set_seed(RANDOM_SEED)


# ============================================================================
# FOCAL LOSS (better for imbalanced classification)
# ============================================================================
def focal_loss(alpha=FOCAL_LOSS_ALPHA, gamma=FOCAL_LOSS_GAMMA):
    """
    Focal Loss: down-weights easy examples, focuses on hard-to-classify ones.
    
    FL(p_t) = -alpha_t * (1 - p_t)^gamma * log(p_t)
    
    Args:
        alpha: Per-class weight list [normal_weight, distress_weight] or None for uniform.
               E.g. [0.30, 0.70] gives 70% weight to distress class.
        gamma: Focusing parameter (higher = more focus on hard examples). Default 2.0.
    """
    if alpha is None:
        alpha = [1.0, 1.0]
    
    alpha_tensor = tf.constant(alpha, dtype=tf.float32)
    
    def loss(y_true, y_pred):
        # Clip predictions to avoid log(0)
        y_pred = tf.clip_by_value(y_pred, tf.keras.backend.epsilon(), 1.0 - tf.keras.backend.epsilon())
        
        # Convert one-hot to class indices
        y_true_idx = tf.argmax(y_true, axis=1)
        
        # Gather predictions for the true class
        batch_size = tf.shape(y_pred)[0]
        indices = tf.stack([tf.range(batch_size), tf.cast(y_true_idx, tf.int32)], axis=1)
        p_t = tf.gather_nd(y_pred, indices)
        
        # Compute focal loss
        ce_loss = -tf.math.log(p_t)
        modulating_factor = tf.pow(1.0 - p_t, gamma)
        
        # Apply alpha weighting per class
        alpha_t = tf.gather(alpha_tensor, tf.cast(y_true_idx, tf.int32))
        
        focal = alpha_t * modulating_factor * ce_loss
        return tf.reduce_mean(focal)
    
    return loss


# ============================================================================
# FEATURE-SPACE AUGMENTATION (SpecAugment-style on MFCC features)
# ============================================================================
class SpecAugment:
    """
    On-the-fly feature-space augmentation for MFCC features.
    Applied to each batch during training.
    Uses NumPy (not TF) since it runs inside the Keras Sequence generator.
    
    Techniques:
    1. Gaussian noise injection
    2. Time masking (mask consecutive timesteps)
    3. Frequency masking (mask consecutive MFCC coefficients)
    """
    
    def __init__(self, noise_std=AUG_NOISE_STD, 
                 time_mask_size=AUG_TIME_MASK_SIZE,
                 freq_mask_size=AUG_FREQ_MASK_SIZE):
        self.noise_std = noise_std
        self.time_mask_size = time_mask_size
        self.freq_mask_size = freq_mask_size
    
    def __call__(self, X):
        """Apply augmentation to a batch of MFCC features (numpy array)."""
        batch_size, time_steps, freq_bins, channels = X.shape
        
        # 1. Gaussian noise
        if self.noise_std > 0:
            noise = np.random.normal(0, self.noise_std, size=X.shape).astype(np.float32)
            X = X + noise
        
        # 2. Time masking (mask a random contiguous block of timesteps)
        if self.time_mask_size > 0 and time_steps > self.time_mask_size:
            t_start = np.random.randint(0, max(1, time_steps - self.time_mask_size))
            t_end = min(t_start + self.time_mask_size, time_steps)
            X[:, t_start:t_end, :, :] = 0.0
        
        # 3. Frequency masking (mask a random contiguous block of freq bins)
        if self.freq_mask_size > 0 and freq_bins > self.freq_mask_size:
            f_start = np.random.randint(0, max(1, freq_bins - self.freq_mask_size))
            f_end = min(f_start + self.freq_mask_size, freq_bins)
            X[:, :, f_start:f_end, :] = 0.0
        
        return X


# ============================================================================
# BALANCED BATCH GENERATOR (oversamples minority class)
# ============================================================================
class BalancedBatchGenerator(keras.utils.Sequence):
    """
    Generates batches with balanced class distribution by oversampling
    the minority class (normal samples).
    
    Each batch contains 50% normal / 50% distress samples.
    This prevents the model from simply predicting the majority class.
    """
    
    def __init__(self, X, y, batch_size=BATCH_SIZE, shuffle=True, augment=True):
        self.X = X
        self.y = y
        self.batch_size = batch_size
        self.shuffle = shuffle
        
        # Find indices per class
        self.normal_idx = np.where(y == 0)[0].copy()
        self.distress_idx = np.where(y == 1)[0].copy()
        
        self.n_normal = len(self.normal_idx)
        self.n_distress = len(self.distress_idx)
        
        # Samples per class per batch (balanced: 50/50)
        self.samples_per_class = batch_size // 2
        
        # Steps per epoch: base on the larger class so each epoch sees all samples
        # of the majority class at least once
        self.max_class_size = max(self.n_normal, self.n_distress)
        self.steps_per_epoch = max(1, self.max_class_size // self.samples_per_class)
        
        # Augmenter
        self.augmenter = SpecAugment() if (augment and TRAIN_AUGMENTATION) else None
    
    def __len__(self):
        return self.steps_per_epoch
    
    def __getitem__(self, idx):
        # Sample balanced indices for this batch
        # Use np.random.choice with replace=True so minority class is oversampled naturally
        normal_batch_idx = np.random.choice(self.normal_idx, size=self.samples_per_class, replace=True)
        distress_batch_idx = np.random.choice(self.distress_idx, size=self.samples_per_class, replace=True)
        
        batch_idx = np.concatenate([normal_batch_idx, distress_batch_idx])
        
        if self.shuffle:
            np.random.shuffle(batch_idx)
        
        X_batch = self.X[batch_idx]
        y_batch = self.y[batch_idx]
        
        # Apply feature-space augmentation
        if self.augmenter is not None:
            X_batch = self.augmenter(X_batch)
        
        # Convert labels to categorical
        y_batch_cat = keras.utils.to_categorical(y_batch, num_classes=2)
        
        return X_batch, y_batch_cat


# ============================================================================
# MODEL BUILDING
# ============================================================================
def build_cnn_model(input_shape, num_classes=2):
    """
    Build improved CNN model for MFCC classification.
    
    Improvements over baseline:
    - Increased dropout (0.3 -> 0.5) to reduce overfitting
    - L2 regularization on Conv2D and Dense layers
    - He normal initialization for better gradient flow
    """
    l2_reg = regularizers.l2(L2_REG)
    
    model = models.Sequential([
        # Input layer
        layers.Input(shape=input_shape),
        
        # Conv Block 1
        layers.Conv2D(
            CNN_FILTERS[0], KERNEL_SIZE, padding='same', activation='relu',
            kernel_regularizer=l2_reg,
            kernel_initializer='he_normal'
        ),
        layers.BatchNormalization(),
        layers.MaxPooling2D(POOL_SIZE),
        layers.Dropout(DROPOUT_RATE),
        
        # Conv Block 2
        layers.Conv2D(
            CNN_FILTERS[1], KERNEL_SIZE, padding='same', activation='relu',
            kernel_regularizer=l2_reg,
            kernel_initializer='he_normal'
        ),
        layers.BatchNormalization(),
        layers.MaxPooling2D(POOL_SIZE),
        layers.Dropout(DROPOUT_RATE),
        
        # Conv Block 3
        layers.Conv2D(
            CNN_FILTERS[2], KERNEL_SIZE, padding='same', activation='relu',
            kernel_regularizer=l2_reg,
            kernel_initializer='he_normal'
        ),
        layers.BatchNormalization(),
        layers.MaxPooling2D(POOL_SIZE),
        layers.Dropout(DROPOUT_RATE),
        
        # Flatten and Dense
        layers.Flatten(),
        layers.Dense(
            DENSE_UNITS, activation='relu',
            kernel_regularizer=l2_reg,
            kernel_initializer='he_normal'
        ),
        layers.Dropout(DROPOUT_RATE),
        layers.Dense(num_classes, activation='softmax')
    ])
    
    return model


def plot_training_history(history):
    """Plot training curves (accuracy, loss, F1 score)"""
    fig, axes = plt.subplots(1, 3, figsize=(18, 5))
    
    # Accuracy
    axes[0].plot(history.history.get('accuracy', []), label='Train Accuracy')
    axes[0].plot(history.history.get('val_accuracy', []), label='Val Accuracy')
    axes[0].set_title('Model Accuracy')
    axes[0].set_xlabel('Epoch')
    axes[0].set_ylabel('Accuracy')
    axes[0].legend()
    axes[0].grid(True)
    
    # Loss
    axes[1].plot(history.history.get('loss', []), label='Train Loss')
    axes[1].plot(history.history.get('val_loss', []), label='Val Loss')
    axes[1].set_title('Model Loss')
    axes[1].set_xlabel('Epoch')
    axes[1].set_ylabel('Loss')
    axes[1].legend()
    axes[1].grid(True)
    
    # F1 Score plot
    if 'precision' in history.history and 'recall' in history.history:
        train_prec = history.history['precision']
        train_rec = history.history['recall']
        val_prec = history.history.get('val_precision', [0])
        val_rec = history.history.get('val_recall', [0])
        
        train_f1 = [2 * p * r / (p + r + 1e-8) for p, r in zip(train_prec, train_rec)]
        val_f1 = [2 * p * r / (p + r + 1e-8) for p, r in zip(val_prec, val_rec)]
        
        axes[2].plot(train_f1, label='Train F1')
        axes[2].plot(val_f1, label='Val F1')
        axes[2].set_title('F1 Score')
        axes[2].set_xlabel('Epoch')
        axes[2].set_ylabel('F1')
        axes[2].legend()
        axes[2].grid(True)
    else:
        # Fallback: plot loss if precision/recall not tracked
        axes[2].plot(history.history.get('loss', []), label='Train Loss')
        axes[2].set_title('Loss (no F1 available)')
        axes[2].set_xlabel('Epoch')
        axes[2].legend()
        axes[2].grid(True)
    
    plt.tight_layout()
    plt.savefig(TRAINING_CURVES_PNG, dpi=300, bbox_inches='tight')
    print(f"✅ Training curves saved: {TRAINING_CURVES_PNG}")
    plt.close()


def train_model():
    """Main training function"""
    print("=" * 60)
    print("🧠 SAFEGUARD AI - IMPROVED MODEL TRAINING")
    print("=" * 60)
    
    print("\n📋 Training Configuration:")
    print(f"   • Focal Loss:     {'✓ ENABLED' if USE_FOCAL_LOSS else '✗ DISABLED'} (gamma={FOCAL_LOSS_GAMMA}, alpha={FOCAL_LOSS_ALPHA})")
    print(f"   • Class Weights:  Normal={NORMAL_CLASS_WEIGHT}, Distress={DISTRESS_CLASS_WEIGHT}")
    print(f"   • Dropout:        {DROPOUT_RATE}")
    print(f"   • L2 Reg:         {L2_REG}")
    print(f"   • Augmentation:   {'✓ ENABLED' if TRAIN_AUGMENTATION else '✗ DISABLED'} (noise+time_mask+freq_mask)")
    print(f"   • Balanced Batch: ✓ (50/50 normal/distress per batch)")
    print(f"   • Learning Rate:  {LEARNING_RATE}")
    
    # Load processed data
    print("\n📂 Loading processed data...")
    if not os.path.exists(MFCC_FEATURES_NPY) or not os.path.exists(LABELS_NPY):
        print("❌ ERROR: Processed data not found!")
        print("   Run 2_preprocess_audio.py first")
        return
    
    X = np.load(MFCC_FEATURES_NPY)
    y = np.load(LABELS_NPY)
    
    print(f"   Features shape: {X.shape}")
    print(f"   Labels shape: {y.shape}")
    
    # Generate train/val/test splits
    n_samples = len(X)
    indices = np.arange(n_samples)
    np.random.seed(RANDOM_SEED)
    np.random.shuffle(indices)
    
    train_size = int(n_samples * TRAIN_SPLIT)
    val_size = int(n_samples * VAL_SPLIT)
    
    train_idx = indices[:train_size]
    val_idx = indices[train_size:train_size + val_size]
    test_idx = indices[train_size + val_size:]
    
    print(f"\n✅ Generated dynamic train/val/test splits:")
    print(f"   Train indices: {len(train_idx)} samples")
    print(f"   Val indices: {len(val_idx)} samples")
    print(f"   Test indices: {len(test_idx)} samples")
    
    # Split the data
    X_train = X[train_idx]
    y_train = y[train_idx]
    X_val = X[val_idx]
    y_val = y[val_idx]
    X_test = X[test_idx]
    y_test = y[test_idx]
    
    # Class distribution
    train_normal = np.sum(y_train == 0)
    train_distress = np.sum(y_train == 1)
    print(f"\n📊 Training class distribution:")
    print(f"   Normal:   {train_normal} samples ({train_normal/len(y_train)*100:.1f}%)")
    print(f"   Distress: {train_distress} samples ({train_distress/len(y_train)*100:.1f}%)")
    print(f"   Ratio:    {train_distress/train_normal:.2f}:1")
    
    # Reshape for CNN: (samples, time_steps, n_mfcc, 1)
    X_train = X_train[..., np.newaxis]
    X_val = X_val[..., np.newaxis]
    X_test = X_test[..., np.newaxis]
    
    print(f"\n🔄 Reshaped for CNN: {X_train.shape}")
    
    # Convert labels to categorical
    y_val_cat = keras.utils.to_categorical(y_val, num_classes=2)
    y_test_cat = keras.utils.to_categorical(y_test, num_classes=2)
    
    # Class weights for safety-critical distress detection
    class_weights_dict = {
        0: NORMAL_CLASS_WEIGHT,    # Normal
        1: DISTRESS_CLASS_WEIGHT   # Distress (safety-critical: higher weight)
    }
    
    print(f"\n⚖️  Class weights (safety-critical: high distress recall): {class_weights_dict}")
    
    # Save class weights
    with open(CLASS_WEIGHTS_FILE, 'w') as f:
        json.dump(class_weights_dict, f, indent=2)
    
    # Build model
    print("\n🏗️  Building improved CNN model...")
    input_shape = (MAX_TIME_STEPS, N_MFCC, 1)
    model = build_cnn_model(input_shape, num_classes=2)
    
    # Model summary
    model.summary()
    
    # Save model architecture
    with open(MODEL_ARCHITECTURE, 'w') as f:
        model_json = model.to_json()
        f.write(model_json)
    
    print(f"✅ Model architecture saved: {MODEL_ARCHITECTURE}")
    
    # Choose loss function
    if USE_FOCAL_LOSS:
        print(f"\n🔥 Using Focal Loss (gamma={FOCAL_LOSS_GAMMA}, alpha={FOCAL_LOSS_ALPHA})")
        loss_fn = focal_loss(alpha=FOCAL_LOSS_ALPHA, gamma=FOCAL_LOSS_GAMMA)
    else:
        print(f"\n📊 Using Categorical Cross-Entropy")
        loss_fn = 'categorical_crossentropy'
    
    # Compile model
    print("\n⚙️  Compiling model...")
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=LEARNING_RATE),
        loss=loss_fn,
        metrics=['accuracy', keras.metrics.Precision(name='precision'), keras.metrics.Recall(name='recall')]
    )
    
    # Callbacks
    callbacks = [
        EarlyStopping(
            monitor='val_accuracy',
            patience=EARLY_STOPPING_PATIENCE,
            restore_best_weights=True,
            verbose=1
        ),
        ModelCheckpoint(
            MODEL_H5,
            monitor='val_accuracy',
            save_best_only=True,
            verbose=1
        ),
        ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=8,
            min_lr=1e-7,
            verbose=1
        )
    ]
    
    # Create balanced batch generator for training
    print("\n🔄 Using balanced batch generator (50/50 normal/distress per batch)...")
    train_generator = BalancedBatchGenerator(
        X_train, y_train,
        batch_size=BATCH_SIZE,
        shuffle=True,
        augment=TRAIN_AUGMENTATION
    )
    
    # Train model
    print("\n🚀 Starting training...")
    print("=" * 60)
    
    history = model.fit(
        train_generator,
        validation_data=(X_val, y_val_cat),
        epochs=EPOCHS,
        class_weight=class_weights_dict,
        callbacks=callbacks,
        verbose=VERBOSE
    )
    
    print("=" * 60)
    print("✅ Training complete!")
    
    # Save training history
    history_dict = {}
    for key in history.history:
        history_dict[key] = [float(x) for x in history.history[key]]
    
    with open(TRAINING_HISTORY, 'w') as f:
        json.dump(history_dict, f, indent=2)
    
    print(f"✅ Training history saved: {TRAINING_HISTORY}")
    
    # Plot training curves
    plot_training_history(history)
    
    # Evaluate on test set
    print("\n📊 Evaluating on test set...")
    test_loss, test_acc, test_prec, test_recall = model.evaluate(
        X_test, y_test_cat, verbose=0
    )
    
    f1_score = 2 * (test_prec * test_recall) / (test_prec + test_recall + 1e-8)
    
    print(f"\n🎯 Test Results:")
    print(f"   Accuracy:  {test_acc*100:.2f}%")
    print(f"   Precision: {test_prec*100:.2f}%")
    print(f"   Recall:    {test_recall*100:.2f}%")
    print(f"   F1-Score:  {f1_score*100:.2f}%")
    
    # Predictions
    y_pred = model.predict(X_test)
    y_pred_classes = np.argmax(y_pred, axis=1)
    
    # Classification report
    print("\n" + "=" * 60)
    print("📋 CLASSIFICATION REPORT")
    print("=" * 60)
    report = classification_report(
        y_test, 
        y_pred_classes, 
        target_names=LABEL_NAMES,
        digits=4
    )
    print(report)
    
    # Save classification report
    with open(CLASSIFICATION_REPORT_TXT, 'w') as f:
        f.write("SafeGuard AI - Classification Report (Improved Model)\n")
        f.write("=" * 60 + "\n\n")
        f.write(f"Test Accuracy:  {test_acc*100:.2f}%\n")
        f.write(f"Test Precision: {test_prec*100:.2f}%\n")
        f.write(f"Test Recall:    {test_recall*100:.2f}%\n")
        f.write(f"Test F1-Score:  {f1_score*100:.2f}%\n\n")
        f.write(report)
    
    print(f"✅ Classification report saved: {CLASSIFICATION_REPORT_TXT}")
    
    # Print test confusion matrix
    cm = confusion_matrix(y_test, y_pred_classes)
    print("\n📊 Confusion Matrix:")
    print(f"{'':15s} {'Pred Normal':>12s} {'Pred Distress':>14s}")
    print(f"{'Actual Normal':15s} {cm[0,0]:>12d} {cm[0,1]:>14d}")
    print(f"{'Actual Distress':15s} {cm[1,0]:>12d} {cm[1,1]:>14d}")
    print(f"\n   False Negatives (missed distress): {cm[1,0]} — ⚠️  TARGET: minimize this")
    print(f"   False Positives (false alarms):   {cm[0,1]}")
    
    print("\n✅ Model training complete!")
    print(f"📁 Model saved: {MODEL_H5}")
    print("▶️  Next step: Run 4_test_models.py")


if __name__ == "__main__":
    train_model()
