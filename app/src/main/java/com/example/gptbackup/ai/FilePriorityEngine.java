package com.example.gptbackup.ai;


import android.content.Context;

import com.example.gptbackup.model.FileModel;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.util.List;

public class FilePriorityEngine {

    private Interpreter interpreter;

    public FilePriorityEngine(Context context) {
        try {
            interpreter = TFLiteModelLoader.loadModel(context, "priority_model.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void assignPriorities(List<FileModel> files) {

        for (FileModel file : files) {

            float[] input = FeatureExtractor.extract(file);

            float[][] modelInput = new float[1][input.length];
            modelInput[0] = input;

            float[][] output = new float[1][3]; // 3 classes

            interpreter.run(modelInput, output);

            int priority = argMax(output[0]);
            file.setPriority(priority);
        }
    }

    private int argMax(float[] probs) {
        int index = 0;
        float max = probs[0];

        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > max) {
                max = probs[i];
                index = i;
            }
        }
        return index;
    }
}
