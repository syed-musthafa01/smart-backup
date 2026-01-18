package com.example.gptbackup.ai;

import android.content.Context;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TFLiteModelLoader {

    public static Interpreter loadModel(Context context, String modelName) throws IOException {

        FileInputStream inputStream =
                new FileInputStream(context.getAssets().openFd(modelName).getFileDescriptor());

        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = context.getAssets().openFd(modelName).getStartOffset();
        long declaredLength = context.getAssets().openFd(modelName).getDeclaredLength();

        MappedByteBuffer buffer =
                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        return new Interpreter(buffer);
    }
}
