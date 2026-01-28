package com.example.gptbackup.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.gptbackup.model.FileModel;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.util.List;

public class FilePriorityEngine {

    private static final String TAG = "FilePriorityEngine";
    private static final String PREF_NAME = "backup_stats";
    private static final String KEY_TOTAL_BACKUPS = "total_backups";
    private static final int COLD_START_THRESHOLD = 20;

    private Interpreter interpreter;
    private final SharedPreferences prefs;

    public FilePriorityEngine(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        try {
            interpreter = TFLiteModelLoader.loadModel(context, "priority_model.tflite");
        } catch (IOException e) {
            Log.e(TAG, "TFLite model load failed", e);
            interpreter = null;
        }
    }

    public void assignPriorities(List<FileModel> files, Context context) {

        PriorityFusionEngine fusionEngine =
                new PriorityFusionEngine(context);

        for (FileModel file : files) {

            if (file.isDirectory()) {
                file.setPriority(-1);
                continue;
            }

            float mlScore;

            if (interpreter == null || isColdStartUser()) {
                mlScore = coldStartScore(file);
            } else {
                float[] input = FeatureExtractor.extract(file);
                float[][] output = new float[1][1];
                interpreter.run(new float[][]{input}, output);
                mlScore = output[0][0];
            }

            int finalPriority =
                    fusionEngine.calculateFinalPriority(file, mlScore);

            file.setPriority(finalPriority);
        }
    }

    private float coldStartScore(FileModel file) {
        float score = 0f;

        if (file.isImage()) score += 40f;
        if (file.isVideo()) score += 35f;
        if (file.isDocument()) score += 20f;

        score += Math.max(0, 30 - file.getLastModifiedDays());
        score += Math.min(file.getAccessCount() * 2f, 20f);

        return Math.min(score, 100f);
    }

    private boolean isColdStartUser() {
        return prefs.getInt(KEY_TOTAL_BACKUPS, 0) < COLD_START_THRESHOLD;
    }

    public void incrementBackupCount() {
        int count = prefs.getInt(KEY_TOTAL_BACKUPS, 0);
        prefs.edit().putInt(KEY_TOTAL_BACKUPS, count + 1).apply();
    }
}
