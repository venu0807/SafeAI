"""
SafeGuard AI - TFLite Model Export
Converts Keras model to TensorFlow Lite for Android deployment
"""

import os
import sys
import numpy as np
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

def convert_to_tflite():
    """Convert Keras model to TFLite"""
    print("=" * 60)
    print("📦 SAFEGUARD AI - TFLITE EXPORT")
    print("=" * 60)
    
    # Check if model exists
    if not os.path.exists(MODEL_H5):
        print("❌ ERROR: Trained model not found!")
        print("   Run 3_train_mfcc_cnn.py first")
        return
    
    # Load Keras model
    print("\n📂 Loading Keras model...")
    model = keras.models.load_model(MODEL_H5, custom_objects=CUSTOM_OBJECTS)
    print(f"   ✅ Model loaded from: {MODEL_H5}")
    
    # Model info
    print("\n📊 Model Information:")
    print(f"   Input shape: {model.input_shape}")
    print(f"   Output shape: {model.output_shape}")
    
    # Convert to TFLite
    print(f"\n🔄 Converting to TFLite (quantization: {TFLITE_QUANTIZATION})...")
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Apply optimizations
    if TFLITE_QUANTIZATION == 'float16':
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
        print("   ⚙️  Float16 quantization enabled")
    elif TFLITE_QUANTIZATION == 'int8':
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        
        # Representative dataset for int8 quantization
        def representative_dataset():
            X = np.load(MFCC_FEATURES_NPY)
            X = X[..., np.newaxis].astype(np.float32)
            for i in range(min(100, len(X))):
                yield [X[i:i+1]]
        
        converter.representative_dataset = representative_dataset
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        converter.inference_input_type = tf.int8
        converter.inference_output_type = tf.int8
        print("   ⚙️  Int8 quantization enabled")
    else:
        print("   ⚙️  No quantization (full precision)")
    
    # Convert
    tflite_model = converter.convert()
    
    # Save TFLite model
    with open(MODEL_TFLITE, 'wb') as f:
        f.write(tflite_model)
    
    print(f"\n✅ TFLite model saved: {MODEL_TFLITE}")
    
    # File sizes
    h5_size = os.path.getsize(MODEL_H5) / (1024 * 1024)
    tflite_size = os.path.getsize(MODEL_TFLITE) / (1024 * 1024)
    
    print(f"\n📊 File Sizes:")
    print(f"   Keras (.h5):    {h5_size:.2f} MB")
    print(f"   TFLite (.tflite): {tflite_size:.2f} MB")
    print(f"   Compression:    {(1 - tflite_size/h5_size)*100:.1f}% smaller")
    
    # Test TFLite model
    print("\n🧪 Testing TFLite model...")
    
    # Load test data (generate split dynamically to avoid stale index files)
    X = np.load(MFCC_FEATURES_NPY)
    y = np.load(LABELS_NPY)
    # Dynamically compute test indices based on current dataset size
    n_samples = len(X)
    indices = np.arange(n_samples)
    np.random.seed(RANDOM_SEED)
    np.random.shuffle(indices)
    train_size = int(n_samples * TRAIN_SPLIT)
    val_size = int(n_samples * VAL_SPLIT)
    test_idx = indices[train_size + val_size:]
    
    X_test = X[test_idx]
    y_test = y[test_idx]
    X_test = X_test[..., np.newaxis].astype(np.float32)
    
    # Initialize TFLite interpreter
    interpreter = tf.lite.Interpreter(model_path=MODEL_TFLITE)
    interpreter.allocate_tensors()
    
    # Get input/output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"\n📋 TFLite Model Details:")
    print(f"   Input shape: {input_details[0]['shape']}")
    print(f"   Input type: {input_details[0]['dtype']}")
    print(f"   Output shape: {output_details[0]['shape']}")
    print(f"   Output type: {output_details[0]['dtype']}")
    
    # Test inference
    print("\n⏱️  Testing inference speed...")
    
    import time
    inference_times = []
    correct = 0
    
    num_samples = min(100, len(X_test))
    
    for i in range(num_samples):
        input_data = X_test[i:i+1]
        
        # Handle quantization
        if TFLITE_QUANTIZATION == 'int8':
            input_scale, input_zero_point = input_details[0]['quantization']
            input_data = input_data / input_scale + input_zero_point
            input_data = input_data.astype(np.int8)
        
        # Set input
        interpreter.set_tensor(input_details[0]['index'], input_data)
        
        # Run inference
        start_time = time.time()
        interpreter.invoke()
        inference_time = (time.time() - start_time) * 1000  # ms
        inference_times.append(inference_time)
        
        # Get output
        output_data = interpreter.get_tensor(output_details[0]['index'])
        
        # Handle quantization
        if TFLITE_QUANTIZATION == 'int8':
            output_scale, output_zero_point = output_details[0]['quantization']
            output_data = (output_data.astype(np.float32) - output_zero_point) * output_scale
        
        # Prediction
        pred = np.argmax(output_data[0])
        if pred == y_test[i]:
            correct += 1
    
    accuracy = correct / num_samples
    avg_inference_time = np.mean(inference_times)
    
    print(f"\n🎯 TFLite Model Performance:")
    print(f"   Accuracy: {accuracy*100:.2f}% (on {num_samples} samples)")
    print(f"   Avg Inference Time: {avg_inference_time:.2f} ms")
    print(f"   Min: {np.min(inference_times):.2f} ms")
    print(f"   Max: {np.max(inference_times):.2f} ms")
    
    # Android deployment instructions
    print("\n" + "=" * 60)
    print("📱 ANDROID DEPLOYMENT INSTRUCTIONS")
    print("=" * 60)
    print(f"\n1. Copy TFLite model to Android project:")
    print(f"   FROM: {MODEL_TFLITE}")
    print(f"   TO:   android/app/src/main/assets/audio_mfcc_cnn.tflite")
    print(f"\n2. Ensure assets folder exists:")
    print(f"   Right-click: app/src/main → New → Folder → Assets Folder")
    print(f"\n3. Verify model in build.gradle:")
    print(f"   aaptOptions {{")
    print(f"       noCompress 'tflite'")
    print(f"   }}")
    print(f"\n4. Test on Android device:")
    print(f"   Expected inference time: <100ms on mid-range devices")
    
    print("\n✅ TFLite export complete!")
    print("▶️  Next step: Run 6_realtime_test.py")

if __name__ == "__main__":
    convert_to_tflite()
