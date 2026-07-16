package com.example.android.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Helper class for TensorFlow Lite model loading
 */
public class TFLiteHelper {

    /**
     * Load TFLite model from assets as MappedByteBuffer
     */
    public static MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        fileDescriptor.close();
        return buffer;
    }
}
