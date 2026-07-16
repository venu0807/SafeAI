package com.example.android.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Handles silent background image capture using CameraX upon SOS
 * initialization.
 */
public class CameraHelper {
    public interface PhotoCallback {
        void onPhotoCaptured(Bitmap bitmap);
        void onError(Exception e);
    }
    private static final String TAG = "CameraHelper";
    private final Context mContext;
    private final ExecutorService mCameraExecutor;
    private ImageCapture mImageCapture;
    private ProcessCameraProvider mCameraProvider;

    public CameraHelper(Context context) {
        this.mContext = context;
        this.mCameraExecutor = Executors.newSingleThreadExecutor();
    }

    public void startCameraAndCapture(LifecycleOwner lifecycleOwner, boolean useFrontCamera, PhotoCallback callback) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(mContext);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                mCameraProvider = cameraProvider;

                mImageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                int lensFacing = useFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, mImageCapture);

                takePhoto(callback);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(mContext));
    }

    private void takePhoto(PhotoCallback callback) {
        if (mImageCapture == null) {
            callback.onError(new RuntimeException("Camera not initialized"));
            return;
        }

        File outputDirectory = new File(mContext.getFilesDir(), "confidential_captures");
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                + ".jpg";
        File photoFile = new File(outputDirectory, fileName);

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        mImageCapture.takePicture(
                outputOptions,
                mCameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "Silent Capture Success: " + photoFile.getAbsolutePath());
                        Bitmap capturedBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

                        // Immediately delete the captured photo file — it's ephemeral evidence
                        // that should not persist on disk after YOLO processing.
                        if (photoFile.exists()) {
                            if (photoFile.delete()) {
                                Log.d(TAG, "Ephemeral capture file deleted: " + photoFile.getName());
                            } else {
                                Log.w(TAG, "Could not delete ephemeral capture file: " + photoFile.getName());
                            }
                        }

                        if (callback != null) callback.onPhotoCaptured(capturedBitmap);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Silent Capture Failed: " + exception.getMessage(), exception);
                        if (callback != null) callback.onError(exception);
                    }
                });
    }

    public void shutdown() {
        if (mCameraProvider != null) {
            try {
                mCameraProvider.unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Failed to unbind CameraX", e);
            }
        }
        mCameraExecutor.shutdown();
    }
}
