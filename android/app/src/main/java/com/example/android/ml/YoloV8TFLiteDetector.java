package com.example.android.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class YoloV8TFLiteDetector {

    private static final String TAG = "YoloDetector";
    private static final String MODEL_FILE = "yolov8n_float32.tflite";
    
    // YOLOv8 Nano standard input size
    private static final int INPUT_SIZE = 640;
    
    // Output shape: [1][84][8400]
    // 84 channels = 4 box coordinates + 80 class confidences.
    // 8400 = grid cells
    private static final int NUM_ELEMENTS = 84; 
    private static final int NUM_ANCHORS = 8400;

    // COCO Class Indices
    private static final int CLASS_PERSON = 0; // The primary trigger for human threat verification
    private static final int CLASS_KNIFE = 43; 
    private static final int CLASS_BASEBALL_BAT = 34;

    private Interpreter tflite;
    private ByteBuffer inputBuffer;

    public YoloV8TFLiteDetector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);

        tflite = new Interpreter(loadModelFile(context), options);

        // Allocate Float32 buffer: 1 batch * 640 height * 640 width * 3 channels * 4 bytes
        inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        Log.d(TAG, "YOLOv8 TFLite pipeline initialized successfully!");
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Executes inference on the given bitmap and looks strictly for danger signatures.
     * @return true if a person, knife, or bat is verified with >50% confidence.
     */
    public boolean detectVisualThreat(Bitmap originalBitmap) {
        if (tflite == null) return false;

        // 1. Resize and normalize image
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, INPUT_SIZE, INPUT_SIZE, true);
        convertBitmapToByteBuffer(resizedBitmap);

        // 2. Define standard output array for YOLOv8
        float[][][] output = new float[1][NUM_ELEMENTS][NUM_ANCHORS];

        // 3. Run Inference
        long startTime = System.currentTimeMillis();
        tflite.run(inputBuffer, output);
        Log.d(TAG, "Inference completed in " + (System.currentTimeMillis() - startTime) + "ms");

        // 4. Parse the 84x8400 output tensor
        return verifyThreatSignatures(output);
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        inputBuffer.rewind();
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                final int val = intValues[pixel++];
                
                // Extract RGB and normalize to [0.0, 1.0] float representation
                float r = ((val >> 16) & 0xFF) / 255.0f;
                float g = ((val >> 8) & 0xFF) / 255.0f;
                float b = (val & 0xFF) / 255.0f;
                
                inputBuffer.putFloat(r);
                inputBuffer.putFloat(g);
                inputBuffer.putFloat(b);
            }
        }
    }

    private boolean verifyThreatSignatures(float[][][] output) {
        boolean personFound = false;
        boolean weaponFound = false;
        float highestConfidence = 0.0f;

        // Iterate through all 8400 predicted dense anchor points
        for (int i = 0; i < NUM_ANCHORS; i++) {
            // Index offset by 4 (0-3 are Box CX,CY,W,H)
            float personConfidence = output[0][CLASS_PERSON + 4][i];
            float knifeConfidence = output[0][CLASS_KNIFE + 4][i];
            float batConfidence = output[0][CLASS_BASEBALL_BAT + 4][i];

            if (personConfidence > highestConfidence) {
                highestConfidence = personConfidence;
            }

            if (personConfidence >= 0.50f) {
                personFound = true;
            }
            if (knifeConfidence >= 0.50f || batConfidence >= 0.50f) {
                weaponFound = true;
            }
        }
        
        Log.d(TAG, "Visual Scan - Highest Person Confidence: " + (highestConfidence * 100) + "%");

        // The logic gating of our MultiModal sensor fusion:
        // A verified 50%+ certainty of a Person or Weapon in the frame after an Audio Trigger = Threat Validated
        return personFound || weaponFound;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}
