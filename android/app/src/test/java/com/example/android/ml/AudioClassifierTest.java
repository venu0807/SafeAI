package com.example.android.ml;

import com.example.android.models.ClassificationResult;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for AudioClassifier.
 * These tests verify fallback behavior when the TFLite model is not available
 * (as it would be in a unit test environment without Android assets).
 */
public class AudioClassifierTest {

    @Test
    public void initialize_withoutModelFile_returnsFalse() {
        // In unit tests, Context is mocked and returns null/defaults,
        // so TFLiteHelper.loadModelFile will throw -> initialize returns false
        AudioClassifier classifier = new AudioClassifier();
        boolean result = classifier.initialize(null);
        assertFalse("Initialize should return false without a valid model file", result);
    }

    @Test
    public void classify_whenNotInitialized_returnsFallbackResult() {
        AudioClassifier classifier = new AudioClassifier();

        short[] audioData = new short[16000];
        ClassificationResult result = classifier.classify(audioData);

        assertNotNull("Result should not be null", result);
        assertEquals("Fallback normal probability should be 0.5", 0.5f, result.normalProbability, 0.001f);
        assertEquals("Fallback distress probability should be 0.5", 0.5f, result.distressProbability, 0.001f);
        assertEquals("Fallback predicted class should be 0 (normal)", 0, result.predictedClass);
        assertEquals("Fallback confidence should be 0.5", 0.5f, result.confidence, 0.001f);
        assertEquals("Fallback inference time should be -1", -1, result.inferenceTimeMs);
    }

    @Test
    public void classify_whenNotInitialized_returnsNotDistress() {
        AudioClassifier classifier = new AudioClassifier();

        short[] audioData = new short[16000];
        ClassificationResult result = classifier.classify(audioData);

        assertFalse("Uninitialized classifier should not indicate distress", result.isDistress());
    }

    @Test
    public void classify_withNullAudio_returnsFallbackResult() {
        AudioClassifier classifier = new AudioClassifier();

        ClassificationResult result = classifier.classify(null);

        assertNotNull("Result should not be null even with null input", result);
        assertEquals("Should return fallback result", 0.5f, result.confidence, 0.001f);
    }

    @Test
    public void close_whenNotInitialized_doesNotThrow() {
        AudioClassifier classifier = new AudioClassifier();

        // Should not throw any exception
        classifier.close();
    }

    @Test
    public void close_afterFailedInitialize_doesNotThrow() {
        AudioClassifier classifier = new AudioClassifier();
        classifier.initialize(null); // This fails silently

        // Should not throw any exception
        classifier.close();
    }
}
