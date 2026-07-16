"""
Convert existing yolov8n.onnx to TFLite Float32 for Android
Usage: pip install onnx-tf && python convert_onnx_to_tflite.py
"""
import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'  # Suppress TF warnings

ONNX_PATH = "yolov8n.onnx"
SAVED_MODEL_DIR = "yolov8n_tf_model"
TFLITE_OUTPUT = "yolov8n_float32.tflite"

# Step 1: ONNX -> TensorFlow SavedModel
print("[1/3] Loading ONNX model...")
import onnx
onnx_model = onnx.load(ONNX_PATH)

print("[2/3] Converting ONNX -> TensorFlow SavedModel...")
# Method A: Try onnx_tf backend
try:
    from onnx_tf.backend import prepare
    tf_rep = prepare(onnx_model)
    tf_rep.export_graph(SAVED_MODEL_DIR)
    print("      ✓ Used onnx_tf")
except ImportError:
    print("      onnx_tf not found. Method B: trying onnx2tf manual install...")
    # Method B: Install onnx2tf v1.19.16 (compatible with Python 3.9)
    import subprocess
    subprocess.check_call([
        sys.executable, "-m", "pip", "install", "onnx2tf==1.19.16",
        "onnx_graphsurgeon>=0.3.26", "sng4onnx>=1.0.1"
    ])
    from onnx2tf import convert
    convert(
        input_onnx_file_path=ONNX_PATH,
        output_folder_path=SAVED_MODEL_DIR,
    )
    print("      ✓ Used onnx2tf v1.19.16")

# Step 3: SavedModel -> TFLite Float32
print("[3/3] Converting SavedModel -> TFLite Float32...")
import tensorflow as tf
converter = tf.lite.TFLiteConverter.from_saved_model(SAVED_MODEL_DIR)
converter.optimizations = []
converter.target_spec.supported_types = []
tflite_model = converter.convert()

with open(TFLITE_OUTPUT, "wb") as f:
    f.write(tflite_model)

size_mb = os.path.getsize(TFLITE_OUTPUT) / (1024 * 1024)
print(f"      ✓ Saved: {TFLITE_OUTPUT} ({size_mb:.2f} MB)")
print()
print("Done! Copy to Android assets:")
print(f"  copy {TFLITE_OUTPUT} ..\\..\\android\\app\\src\\main\\assets\\")
