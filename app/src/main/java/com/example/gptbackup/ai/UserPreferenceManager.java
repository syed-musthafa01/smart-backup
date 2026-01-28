package com.example.gptbackup.ai;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferenceManager {

    private static final String PREF_NAME = "user_preferences";
    private final SharedPreferences prefs;

    public UserPreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setCategoryPreference(String category, int score) {
        prefs.edit()
                .putInt("cat_" + category, score)
                .putLong("survey_time", System.currentTimeMillis())
                .apply();
    }

    public int getCategoryPreference(String category) {
        return prefs.getInt("cat_" + category, 50);
    }

    public float getSurveyDecayFactor() {
        long time = prefs.getLong("survey_time", 0);
        if (time == 0) return 0f;

        long days =
                (System.currentTimeMillis() - time)
                        / (1000L * 60 * 60 * 24);

        if (days > 30) return 0.2f;
        if (days > 14) return 0.5f;
        return 1f;
    }
}
