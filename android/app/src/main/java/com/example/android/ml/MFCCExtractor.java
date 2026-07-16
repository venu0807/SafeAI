package com.example.android.ml;

import android.util.Log;

/**
 * Optimized MFCC Extractor for Production
 * Uses Radix-2 FFT for efficient processing.
 * PRE-ALLOCATES buffers to prevent GC stutter during 24x7 monitoring.
 */
public class MFCCExtractor {

    private static final String TAG = "MFCCExtractor";

    private static final int SAMPLE_RATE = 16000;
    private static final int N_MFCC = 40;
    private static final int N_FFT = 2048;
    private static final int HOP_LENGTH = 512;
    private static final int MAX_TIME_STEPS = 100;
    private static final int N_MELS = 128;
    private static final int AUDIO_LENGTH = 16000;
    private static final int NUM_FRAMES = 1 + (AUDIO_LENGTH - N_FFT) / HOP_LENGTH;
    private static final int SPEC_SIZE = N_FFT / 2 + 1;

    private static final float PRE_EMPHASIS_COEFF = 0.97f;
    private static final double LOG_EPSILON = 1e-10;

    private static float[][] melFilterBank = null;
    private static float[] hammingWindow = null;

    // --- PRE-ALLOCATED BUFFERS TO AVOID GC STUTTER ---
    private static final float[] audioFloat = new float[AUDIO_LENGTH];
    private static final float[][] frames = new float[NUM_FRAMES][N_FFT];
    private static final float[][] powerSpectrum = new float[NUM_FRAMES][SPEC_SIZE];
    private static final float[][] melSpectrum = new float[NUM_FRAMES][N_MELS];
    private static final float[][] mfcc = new float[NUM_FRAMES][N_MFCC];
    private static final float[][] finalResult = new float[MAX_TIME_STEPS][N_MFCC];
    private static final float[] fftData = new float[N_FFT * 2];
    
    // Tracks the number of valid frames in the current extraction
    private static int currentNumFrames = 0;
    
    // Mutex for thread safety
    private static final Object lock = new Object();

    static {
        melFilterBank = createMelFilterBank();
        hammingWindow = createHammingWindow(N_FFT);
    }

    public static float[][] extractMFCC(short[] audioData) {
        synchronized (lock) {
            try {
                // Ensure audioData is not longer than expected
                int length = Math.min(audioData.length, AUDIO_LENGTH);
                
                convertAndNormalizeAndPreEmphasis(audioData, length);
                frameSignal(length);
                computePowerSpectrum();
                applyMelFiltersAndLogCompress();
                applyDCTAndNormalize();
                padOrTruncate();
                
                float[][] resultCopy = new float[MAX_TIME_STEPS][N_MFCC];
                for (int i = 0; i < MAX_TIME_STEPS; i++) {
                    System.arraycopy(finalResult[i], 0, resultCopy[i], 0, N_MFCC);
                }
                return resultCopy;
            } catch (Exception e) {
                Log.e(TAG, "MFCC extraction failed: " + e.getMessage());
                return new float[MAX_TIME_STEPS][N_MFCC];
            }
        }
    }

    private static void convertAndNormalizeAndPreEmphasis(short[] audioData, int length) {
        if (length == 0) return;
        
        // Combine conversion, normalization, and pre-emphasis in one pass to save time
        audioFloat[0] = (audioData[0] / 32768.0f);
        float prev = audioFloat[0];
        
        for (int i = 1; i < length; i++) {
            float current = audioData[i] / 32768.0f;
            audioFloat[i] = current - PRE_EMPHASIS_COEFF * prev;
            prev = current;
        }
        
        // Zero out any remaining buffer if audioData was shorter than AUDIO_LENGTH
        for(int i = length; i < AUDIO_LENGTH; i++) {
            audioFloat[i] = 0f;
        }
    }

    private static void frameSignal(int signalLength) {
        int numFrames = 1 + (signalLength - N_FFT) / HOP_LENGTH;
        if (numFrames < 0) numFrames = 0;
        currentNumFrames = Math.min(numFrames, NUM_FRAMES);

        for (int i = 0; i < currentNumFrames; i++) {
            int start = i * HOP_LENGTH;
            for (int j = 0; j < N_FFT; j++) {
                if (start + j < signalLength) {
                    frames[i][j] = audioFloat[start + j] * hammingWindow[j];
                } else {
                    frames[i][j] = 0f;
                }
            }
        }
    }

    private static void computePowerSpectrum() {
        for (int i = 0; i < currentNumFrames; i++) {
            for (int j = 0; j < N_FFT; j++) {
                fftData[j * 2] = frames[i][j];
                fftData[j * 2 + 1] = 0f;
            }
            
            applyFFT(fftData);

            for (int j = 0; j < SPEC_SIZE; j++) {
                float real = fftData[j * 2];
                float imag = fftData[j * 2 + 1];
                powerSpectrum[i][j] = (real * real + imag * imag) / N_FFT;
            }
        }
    }

    private static void applyFFT(float[] data) {
        int n = data.length / 2;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float tempReal = data[i * 2];
                float tempImag = data[i * 2 + 1];
                data[i * 2] = data[j * 2];
                data[i * 2 + 1] = data[j * 2 + 1];
                data[j * 2] = tempReal;
                data[j * 2 + 1] = tempImag;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len;
            float wlenReal = (float) Math.cos(ang);
            float wlenImag = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float wReal = 1, wImag = 0;
                for (int j = 0; j < len / 2; j++) {
                    float uReal = data[(i + j) * 2];
                    float uImag = data[(i + j) * 2 + 1];
                    float vReal = data[(i + j + len / 2) * 2] * wReal - data[(i + j + len / 2) * 2 + 1] * wImag;
                    float vImag = data[(i + j + len / 2) * 2] * wImag + data[(i + j + len / 2) * 2 + 1] * wReal;
                    data[(i + j) * 2] = uReal + vReal;
                    data[(i + j) * 2 + 1] = uImag + vImag;
                    data[(i + j + len / 2) * 2] = uReal - vReal;
                    data[(i + j + len / 2) * 2 + 1] = uImag - vImag;
                    float tmpReal = wReal * wlenReal - wImag * wlenImag;
                    wImag = wReal * wlenImag + wImag * wlenReal;
                    wReal = tmpReal;
                }
            }
        }
    }

    private static void applyMelFiltersAndLogCompress() {
        for (int i = 0; i < currentNumFrames; i++) {
            for (int j = 0; j < N_MELS; j++) {
                float sum = 0;
                for (int k = 0; k < SPEC_SIZE; k++) {
                    sum += powerSpectrum[i][k] * melFilterBank[j][k];
                }
                melSpectrum[i][j] = (float) Math.log(sum + LOG_EPSILON);
            }
        }
    }

    private static void applyDCTAndNormalize() {
        for (int i = 0; i < currentNumFrames; i++) {
            for (int j = 0; j < N_MFCC; j++) {
                double sum = 0;
                for (int k = 0; k < N_MELS; k++) {
                    sum += melSpectrum[i][k] * Math.cos(Math.PI * j * (k + 0.5) / N_MELS);
                }
                mfcc[i][j] = (float) (sum * Math.sqrt(2.0 / N_MELS));
            }
        }

        // Normalize
        for (int j = 0; j < N_MFCC; j++) {
            if (currentNumFrames == 0) break;
            
            float mean = 0;
            for (int i = 0; i < currentNumFrames; i++) {
                mean += mfcc[i][j];
            }
            mean /= currentNumFrames;
            float std = 0;
            for (int i = 0; i < currentNumFrames; i++) {
                std += Math.pow(mfcc[i][j] - mean, 2);
            }
            std = (float) Math.sqrt(std / currentNumFrames);
            if (std > 1e-6) {
                for (int i = 0; i < currentNumFrames; i++) {
                    mfcc[i][j] = (mfcc[i][j] - mean) / std;
                }
            }
        }
    }

    private static void padOrTruncate() {
        // Copy mfcc to finalResult.
        int copyLength = Math.min(currentNumFrames, MAX_TIME_STEPS);
        for (int i = 0; i < copyLength; i++) {
            System.arraycopy(mfcc[i], 0, finalResult[i], 0, N_MFCC);
        }
        
        // Zero pad the rest
        for (int i = copyLength; i < MAX_TIME_STEPS; i++) {
            for (int j = 0; j < N_MFCC; j++) {
                finalResult[i][j] = 0f;
            }
        }
    }

    private static float[] createHammingWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = (float) (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (size - 1)));
        }
        return window;
    }

    private static float[][] createMelFilterBank() {
        float[][] filters = new float[N_MELS][SPEC_SIZE];
        float fMin = 0;
        float fMax = SAMPLE_RATE / 2.0f;
        float melMin = hzToMel(fMin);
        float melMax = hzToMel(fMax);
        float[] melPoints = new float[N_MELS + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = melMin + (melMax - melMin) * i / (N_MELS + 1);
        }
        int[] bins = new int[melPoints.length];
        for (int i = 0; i < melPoints.length; i++) {
            bins[i] = (int) Math.floor((N_FFT + 1) * melToHz(melPoints[i]) / SAMPLE_RATE);
        }
        for (int i = 0; i < N_MELS; i++) {
            int denom1 = Math.max(1, bins[i + 1] - bins[i]);
            int denom2 = Math.max(1, bins[i + 2] - bins[i + 1]);
            for (int j = bins[i]; j < bins[i + 1]; j++) filters[i][j] = (float) (j - bins[i]) / denom1;
            for (int j = bins[i + 1]; j < bins[i + 2]; j++) filters[i][j] = (float) (bins[i + 2] - j) / denom2;
        }
        return filters;
    }

    private static float hzToMel(float hz) { return (float) (2595 * Math.log10(1 + hz / 700.0)); }
    private static float melToHz(float mel) { return (float) (700 * (Math.pow(10, mel / 2595.0) - 1)); }
}
