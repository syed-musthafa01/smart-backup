package com.example.gptbackup.ai;

import android.content.Context;
import android.content.SharedPreferences;

public class BehaviorTracker {

    private static final String PREF_NAME = "behavior_stats";
    private final SharedPreferences prefs;

    public BehaviorTracker(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void recordFileOpen(String path) {
        int count = prefs.getInt(path + "_open", 0);
        prefs.edit()
                .putInt(path + "_open", count + 1)
                .putLong(path + "_last", System.currentTimeMillis())
                .apply();
    }

    public void recordBackupSkip(String path) {
        int skip = prefs.getInt(path + "_skip", 0);
        prefs.edit().putInt(path + "_skip", skip + 1).apply();
    }

    public float getBehaviorScore(String path) {
        int openCount = prefs.getInt(path + "_open", 0);
        int skipCount = prefs.getInt(path + "_skip", 0);
        long lastOpen = prefs.getLong(path + "_last", 0);

        float score = openCount * 5f;
        score -= skipCount * 10f;

        if (System.currentTimeMillis() - lastOpen < 3L * 24 * 60 * 60 * 1000) {
            score += 20f;
        }

        return Math.max(0f, Math.min(score, 100f));
    }
}
