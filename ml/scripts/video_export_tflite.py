from ultralytics import YOLO

# 1. Download/Load the perfectly optimized official YOLOv8 Nano model
# This contains all 80 standard COCO classes, meaning it will detect "person", "knife", "backpack", etc.
model = YOLO("yolov8n.pt") 

print("Exporting official pre-trained model to Android TensorFlow Lite format...")

# 2. Export it instantly to an Android-compatible TFLite format
# Setting int8=True shrinks the file size down to around 3 Megabytes, making it ultra-light for phones!
model.export(format="tflite", imgsz=640, int8=True)

print("\n✅ Export Complete! The .tflite file is saved!")
print("Look for the 'yolov8n_saved_model' folder right here in /scripts/")
print("Grab the smallest '.tflite' file you find and move it to Android Studio!")