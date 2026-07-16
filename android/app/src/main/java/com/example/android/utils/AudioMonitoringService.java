package com.example.android.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Records short audio clips during confirmed threat incidents,
 * encrypts them, and saves them to the incident vault for later review.
 *
 * File format: incidence_<timestamp>.enc
 * Internal format: [4-byte chunk size (LE int32)][encrypted chunk data] repeated
 */
public class AudioMonitoringService {

    private static final String TAG = "AudioMonitoringService";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORD_DURATION_MS = 30_000; // 30 seconds
    private static final int BUFFER_SIZE = 4096;

    private final Context context;
    private final EncryptionHelper encryptionHelper;
    private volatile boolean isRecording = false;
    private AudioRecord audioRecord;
    private Thread recordingThread;

    public AudioMonitoringService(Context context) {
        this.context = context;
        this.encryptionHelper = new EncryptionHelper();
    }

    /**
     * Start recording incident audio on a background thread.
     * The recording will automatically stop after {@link #RECORD_DURATION_MS} ms
     * and save the encrypted file to the incident vault.
     */
    public void startIncidentRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording an incident. Ignoring duplicate request.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted — cannot record incident.");
            return;
        }

        isRecording = true;

        recordingThread = new Thread(() -> {
            try {
                recordAndSave();
            } catch (Exception e) {
                Log.e(TAG, "Incident recording failed", e);
            } finally {
                isRecording = false;
            }
        }, "IncidentRecorder");
        recordingThread.start();

        // Auto-stop after the recording duration
        new Handler(Looper.getMainLooper()).postDelayed(this::stopRecording, RECORD_DURATION_MS);
    }

    /**
     * Stop any ongoing recording.
     */
    public void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }
    }

    private void recordAndSave() {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufferSize, BUFFER_SIZE);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize for incident recording.");
            isRecording = false;
            return;
        }

        // Prepare output file: incident_<timestamp>.enc
        long timestamp = System.currentTimeMillis();
        File outputDir = context.getFilesDir();
        File outputFile = new File(outputDir, "incident_" + timestamp + ".enc");

        audioRecord.startRecording();
        Log.d(TAG, "Incident recording started -> " + outputFile.getName());

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] pcmBuffer = new byte[BUFFER_SIZE];
            int totalSamples = 0;
            int maxSamples = (SAMPLE_RATE * 2) * (RECORD_DURATION_MS / 1000); // 16-bit = 2 bytes/sample

            while (isRecording && totalSamples < maxSamples) {
                int bytesRead = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                if (bytesRead > 0) {
                    totalSamples += bytesRead;

                    // Encrypt the chunk and write with size prefix
                    // Copy only the valid portion of the buffer
                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(pcmBuffer, 0, chunk, 0, bytesRead);
                    byte[] encrypted = encryptionHelper.encrypt(chunk);
                    byte[] sizePrefix = ByteBuffer.allocate(4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(encrypted.length)
                            .array();
                    fos.write(sizePrefix);
                    fos.write(encrypted);
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord error during incident recording.");
                    break;
                }
            }

            fos.flush();
            Log.i(TAG, "Incident recording saved: " + outputFile.getAbsolutePath()
                    + " (" + totalSamples + " bytes raw)");

        } catch (Exception e) {
            Log.e(TAG, "Failed to save incident recording", e);
        } finally {
            stopRecording();
        }
    }

    /**
     * Cleans up resources. Call when the service is destroyed.
     */
    public void destroy() {
        stopRecording();
        if (recordingThread != null) {
            try {
                recordingThread.join(500);
            } catch (InterruptedException ignored) {
            }
            recordingThread = null;
        }
    }
}
