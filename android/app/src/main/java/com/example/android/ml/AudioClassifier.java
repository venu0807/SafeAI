package com.example.android.ml;

import android.content.Context;
import android.util.Log;

import com.example.android.models.ClassificationResult;

import org.tensorflow.lite.Interpreter;

import java.nio.MappedByteBuffer;
import java.util.Locale;

/**
 * TensorFlow Lite Audio Classifier
 * Runs MFCC+CNN model for distress detection
 */
public class AudioClassifier {

    private static final String TAG = "AudioClassifier";
    private static final String MODEL_PATH = "audio_mfcc_cnn.tflite";

    private static final int MAX_TIME_STEPS = 100;
    private static final int N_MFCC = 40;
    // ponytail: single threshold. Per-user calibration stored in SharedPrefs if needed later.
    private static final float DISTRESS_THRESHOLD = 0.45f;  // Matches ml/config.py THREAT_THRESHOLD

    private Interpreter tflite;
    private boolean isInitialized = false;

    /**
     * Initialize TensorFlow Lite interpreter
     */
    public boolean initialize(Context context) {
        try {
            Log.d(TAG, "Initializing TensorFlow Lite model...");

            // Load model using TFLiteHelper
            MappedByteBuffer modelBuffer = TFLiteHelper.loadModelFile(context, MODEL_PATH);

            // Configure interpreter options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            // Create interpreter
            tflite = new Interpreter(modelBuffer, options);
            isInitialized = true;
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing model: " + e.getMessage());
            return false;
        }
    }

    /**
     * Classify audio from raw PCM samples
     */
    public ClassificationResult classify(short[] audioSamples) {
        if (!isInitialized) {
            Log.e(TAG, "Model not initialized");
            return new ClassificationResult(0.5f, 0.5f, 0, 0.5f, -1);
        }

        try {
            long startTime = System.currentTimeMillis();

            // Step 1: Extract MFCC features
            float[][] mfcc = MFCCExtractor.extractMFCC(audioSamples);

            // Step 2: Prepare tensors
            float[][][] input = new float[1][MAX_TIME_STEPS][N_MFCC];
            input[0] = mfcc;
            float[][] output = new float[1][2];

            // Step 3: Run inference
            long inferenceStart = System.currentTimeMillis();
            tflite.run(input, output);
            long inferenceTime = System.currentTimeMillis() - inferenceStart;

            // Step 4: Parse results
            float normalProb = output[0][0];
            float distressProb = output[0][1];
            int predictedClass = (distressProb > DISTRESS_THRESHOLD) ? 1 : 0;
            // For binary classification, confidence measures how far from the decision boundary:
            // 0.0 = right at boundary (uncertain), 1.0 = far from boundary (certain)
            float confidence = Math.abs(distressProb - DISTRESS_THRESHOLD) / Math.max(DISTRESS_THRESHOLD, 1.0f - DISTRESS_THRESHOLD);
            long totalTime = System.currentTimeMillis() - startTime;

            Log.d(TAG, String.format(Locale.getDefault(), 
                "Classification: %s (%.1f%% confidence)", 
                predictedClass == 1 ? "DISTRESS" : "NORMAL", confidence * 100));

            return new ClassificationResult(normalProb, distressProb, predictedClass, confidence, totalTime);

        } catch (Exception e) {
            Log.e(TAG, "Error during classification: " + e.getMessage());
            return new ClassificationResult(0.5f, 0.5f, 0, 0.5f, -1);
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        isInitialized = false;
    }
}
