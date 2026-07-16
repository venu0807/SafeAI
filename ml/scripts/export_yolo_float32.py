from ultralytics import YOLO

# Load the official YOLOv8 Nano model
model = YOLO("yolov8n.pt")

print("Exporting YOLOv8 Nano to Float32 TFLite for Android...")

# Export to Float32 TFLite (matches Android YoloV8TFLiteDetector.java expectations)
model.export(format="tflite", imgsz=640, int8=False)

print("\n✅ Export Complete!")
print("Look for the 'yolov8n_saved_model' folder")
print("The .tflite file should be around 6-7 MB (Float32)")
