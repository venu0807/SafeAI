package com.example.android.ml;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for MFCCExtractor.
 * Includes regression tests for zero-allocation static buffer optimizations.
 */
public class MFCCExtractorTest {

    private static final int SAMPLE_RATE = 16000;
    private static final int EXPECTED_TIME_STEPS = 100;
    private static final int EXPECTED_N_MFCC = 40;

    @Test
    public void extractMFCC_withSilentAudio_returnsCorrectShape() {
        short[] silentAudio = new short[SAMPLE_RATE]; // 1 second of silence
        float[][] result = MFCCExtractor.extractMFCC(silentAudio);
        assertNotNull("Result should not be null", result);
        assertEquals("Should have " + EXPECTED_TIME_STEPS + " time steps",
                EXPECTED_TIME_STEPS, result.length);
        assertEquals("Each time step should have " + EXPECTED_N_MFCC + " MFCC coefficients",
                EXPECTED_N_MFCC, result[0].length);
    }

    @Test
    public void extractMFCC_withSilentAudio_returnsFiniteValues() {
        short[] silentAudio = new short[SAMPLE_RATE];
        float[][] result = MFCCExtractor.extractMFCC(silentAudio);
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length; j++) {
                assertTrue("Value at [" + i + "][" + j + "] should be finite: " + result[i][j],
                        Float.isFinite(result[i][j]));
            }
        }
    }

    @Test
    public void extractMFCC_withSineWave_returnsNonZeroValues() {
        short[] sineAudio = generateSineWave(440, 1, 0.5); // 440Hz sine wave, 1 second, 50% amplitude
        short[] silentAudio = new short[sineAudio.length];

        // Deep copy the first result to prevent the static buffer from overwriting it!
        float[][] sineResultRef = MFCCExtractor.extractMFCC(sineAudio);
        float[][] sineResult = cloneArray(sineResultRef);
        
        float[][] silentResult = MFCCExtractor.extractMFCC(silentAudio);

        // Sine wave should produce different MFCC values than silence
        boolean differs = false;
        for (int i = 0; i < EXPECTED_TIME_STEPS && !differs; i++) {
            for (int j = 0; j < EXPECTED_N_MFCC && !differs; j++) {
                if (Math.abs(sineResult[i][j] - silentResult[i][j]) > 0.01f) {
                    differs = true;
                }
            }
        }
        assertTrue("Sine wave MFCC should differ from silence MFCC", differs);
    }

    @Test
    public void extractMFCC_withVeryShortAudio_returnsPaddedResult() {
        short[] shortAudio = new short[256]; // Too short for a single frame
        float[][] result = MFCCExtractor.extractMFCC(shortAudio);
        assertNotNull("Result should not be null for short audio", result);
        assertEquals("Should still return " + EXPECTED_TIME_STEPS + " time steps (zero-padded)",
                EXPECTED_TIME_STEPS, result.length);
    }

    @Test
    public void extractMFCC_withLongAudio_truncatesToMaxTimeSteps() {
        short[] longAudio = new short[SAMPLE_RATE * 30]; // 30 seconds of audio
        float[][] result = MFCCExtractor.extractMFCC(longAudio);
        assertNotNull("Result should not be null", result);
        assertEquals("Should be truncated to " + EXPECTED_TIME_STEPS + " time steps",
                EXPECTED_TIME_STEPS, result.length);
    }

    @Test
    public void extractMFCC_withDifferentFrequencies_producesDifferentFeatures() {
        short[] lowFreq = generateSineWave(200, 1, 0.5);
        short[] highFreq = generateSineWave(2000, 1, 0.5);

        float[][] lowResultRef = MFCCExtractor.extractMFCC(lowFreq);
        float[][] lowResult = cloneArray(lowResultRef);
        
        float[][] highResult = MFCCExtractor.extractMFCC(highFreq);

        boolean differs = false;
        for (int i = 0; i < Math.min(10, EXPECTED_TIME_STEPS) && !differs; i++) {
            for (int j = 0; j < Math.min(13, EXPECTED_N_MFCC) && !differs; j++) {
                if (Math.abs(lowResult[i][j] - highResult[i][j]) > 0.1f) {
                    differs = true;
                }
            }
        }
        assertTrue("Different frequencies should produce different MFCC features", differs);
    }

    // --- REGRESSION TESTS FOR ZERO-ALLOCATION STATIC BUFFERS ---

    @Test
    public void extractMFCC_multipleSequentialCalls_doesNotLeakState() {
        // 1. Run loud sine wave and keep a copy
        short[] loudAudio = generateSineWave(1000, 1, 1.0);
        float[][] loudResultRef = MFCCExtractor.extractMFCC(loudAudio);
        float[][] loudResult = cloneArray(loudResultRef);

        // 2. Run silence immediately after. If state leaks, the static buffer
        // won't be fully cleared, and the silence result will have non-zero artifacts.
        short[] silentAudio = new short[SAMPLE_RATE];
        float[][] silentResult = MFCCExtractor.extractMFCC(silentAudio);
        
        // Ensure that the silence result is fundamentally different from the loud result.
        boolean differs = false;
        for (int i = 0; i < EXPECTED_TIME_STEPS && !differs; i++) {
            for (int j = 0; j < EXPECTED_N_MFCC && !differs; j++) {
                if (Math.abs(loudResult[i][j] - silentResult[i][j]) > 0.5f) {
                    differs = true;
                }
            }
        }
        assertTrue("Silent extraction after loud extraction must not leak state", differs);
    }

    @Test
    public void extractMFCC_shortAudioFollowedByLongAudio_clearsBuffer() {
        // Run a short audio clip
        short[] shortAudio = new short[512];
        for (int i=0; i<512; i++) shortAudio[i] = Short.MAX_VALUE;
        float[][] shortResultRef = MFCCExtractor.extractMFCC(shortAudio);
        float[][] shortResult = cloneArray(shortResultRef);

        // Run full audio
        short[] longAudio = generateSineWave(500, 1, 0.8);
        MFCCExtractor.extractMFCC(longAudio);

        // Re-run short audio. If zero padding fails, the end of the short audio
        // buffer will still contain the longAudio's data.
        float[][] secondShortResult = MFCCExtractor.extractMFCC(shortAudio);
        
        // Assert identical to first run
        for (int i = 0; i < EXPECTED_TIME_STEPS; i++) {
            for (int j = 0; j < EXPECTED_N_MFCC; j++) {
                assertEquals("Subsequent short extractions should identical. Buffer leaked!", 
                    shortResult[i][j], secondShortResult[i][j], 0.001f);
            }
        }
    }

    // --- Helpers ---

    private short[] generateSineWave(double frequencyHz, int durationSeconds, double amplitude) {
        int numSamples = SAMPLE_RATE * durationSeconds;
        short[] samples = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * frequencyHz / SAMPLE_RATE;
            samples[i] = (short) (Math.sin(angle) * Short.MAX_VALUE * amplitude);
        }
        return samples;
    }

    private float[][] cloneArray(float[][] src) {
        float[][] dest = new float[src.length][];
        for (int i = 0; i < src.length; i++) {
            dest[i] = new float[src[i].length];
            System.arraycopy(src[i], 0, dest[i], 0, src[i].length);
        }
        return dest;
    }
}
