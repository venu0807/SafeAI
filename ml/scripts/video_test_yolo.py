from ultralytics import YOLO

# Load the official pre-trained YOLOv8 Nano model (automatically downloads if missing)
# This model recognizes 80 common objects natively.
model = YOLO("yolov8n.pt") 

print("========================================")
print("FIRING UP YOUR WEBCAM FOR LIVE INFERENCE")
print("========================================")
print("Hold up anything (like yourself, a cup, or a kitchen knife) to see if it detects them!")
print("Press 'q' or 'Esc' on your keyboard while selecting the video window to instantly close it.\n")

# Run live inference on your default laptop webcam (source=0)
# 'conf=0.4' means it will only show bounding boxes if it's 40% confident of what it sees.
# 'show=True' forces a visual window to physically pop up on your screen.
results = model.predict(source=0, conf=0.4, show=True)

print("Tests successfully complete!")
