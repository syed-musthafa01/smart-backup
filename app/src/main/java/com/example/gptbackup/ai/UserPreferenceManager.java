package com.example.gptbackup.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages onboarding preferences and per-category user preferences.
 *
 * Onboarding keys:
 *  - onboarding_done          : boolean
 *  - pref_photos              : int (0-100 importance score)
 *  - pref_videos              : int (0-100 importance score)
 *  - pref_documents           : int (0-100 importance score)
 *  - pref_screenshots         : boolean (backup screenshots?)
 *  - pref_camera_high         : boolean (camera = HIGH priority?)
 *  - pref_text_docs           : boolean (invoices/certificates = HIGH?)
 *  - pref_frequency           : String ("daily", "weekly", "monthly")
 */
public class UserPreferenceManager {

    private static final String PREF_NAME = "user_preferences";

    // Onboarding keys
    public static final String KEY_ONBOARDING_DONE      = "onboarding_done";
    private static final String KEY_MOST_IMPORTANT      = "most_important_category";
    private static final String KEY_PREFER_PHOTOS       = "pref_photos";
    private static final String KEY_PREFER_VIDEOS       = "pref_videos";
    private static final String KEY_PREFER_DOCUMENTS    = "pref_documents";
    private static final String KEY_BACKUP_SCREENSHOTS  = "pref_screenshots";
    private static final String KEY_CAMERA_HIGH         = "pref_camera_high";
    private static final String KEY_TEXT_DOCS           = "pref_text_docs";
    private static final String KEY_FREQUENCY           = "pref_frequency";
    private static final String KEY_SURVEY_TIME         = "survey_time";

    private final SharedPreferences prefs;

    public UserPreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // =================== Onboarding Status ===================

    public static boolean isOnboardingComplete(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return p.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingComplete(boolean done) {
        prefs.edit()
                .putBoolean(KEY_ONBOARDING_DONE, done)
                .putLong(KEY_SURVEY_TIME, System.currentTimeMillis())
                .apply();
    }

    // =================== Type Preference Scores (0–100) ===================

    public void setMostImportantCategory(String category) {
        prefs.edit().putString(KEY_MOST_IMPORTANT, category).apply();
    }

    /**
     * Get the user's preference weight for a given file type category (1.0 to 1.5).
     */
    public float getCategoryWeight(String fileType) {
        String mostImportant = prefs.getString(KEY_MOST_IMPORTANT, "everything");
        
        if ("everything".equalsIgnoreCase(mostImportant)) {
            return 1.2f;
        }

        if (fileType == null) return 1.0f;

        switch (fileType.toLowerCase()) {
            case "image":
            case "photo":
                return "photos".equalsIgnoreCase(mostImportant) ? 1.5f : 0.9f;
            case "video":
                return "videos".equalsIgnoreCase(mostImportant) ? 1.5f : 0.9f;
            case "document":
                return "documents".equalsIgnoreCase(mostImportant) ? 1.5f : 0.9f;
            case "audio":
                return "audio".equalsIgnoreCase(mostImportant) ? 1.5f : 0.9f;
            default:
                return 1.0f;
        }
    }

    public void setPhotoPreference(int score) {
        prefs.edit().putInt(KEY_PREFER_PHOTOS, score).apply();
    }
    public int getPhotoPreference() {
        return prefs.getInt(KEY_PREFER_PHOTOS, 70); // default: high
    }

    public void setVideoPreference(int score) {
        prefs.edit().putInt(KEY_PREFER_VIDEOS, score).apply();
    }
    public int getVideoPreference() {
        return prefs.getInt(KEY_PREFER_VIDEOS, 60);
    }

    public void setDocumentPreference(int score) {
        prefs.edit().putInt(KEY_PREFER_DOCUMENTS, score).apply();
    }
    public int getDocumentPreference() {
        return prefs.getInt(KEY_PREFER_DOCUMENTS, 60);
    }

    /**
     * Get the user's preference score for a given file type category.
     * Falls back to 50 for audio/other.
     */
    public int getTypePreferenceScore(String fileType) {
        if (fileType == null) return 50;
        switch (fileType.toLowerCase()) {
            case "image": return getPhotoPreference();
            case "video": return getVideoPreference();
            case "document": return getDocumentPreference();
            case "audio": return prefs.getInt("pref_audio", 40);
            default: return 50;
        }
    }

    // =================== Feature Toggles ===================

    public void setBackupScreenshots(boolean backup) {
        prefs.edit().putBoolean(KEY_BACKUP_SCREENSHOTS, backup).apply();
    }
    public boolean shouldBackupScreenshots() {
        return prefs.getBoolean(KEY_BACKUP_SCREENSHOTS, false); // default: no
    }

    public void setCameraHighPriority(boolean high) {
        prefs.edit().putBoolean(KEY_CAMERA_HIGH, high).apply();
    }
    public boolean isCameraHighPriority() {
        return prefs.getBoolean(KEY_CAMERA_HIGH, true); // default: yes
    }

    public void setPrioritizeTextDocuments(boolean prioritize) {
        prefs.edit().putBoolean(KEY_TEXT_DOCS, prioritize).apply();
    }
    public boolean shouldPrioritizeTextDocuments() {
        return prefs.getBoolean(KEY_TEXT_DOCS, true); // default: yes
    }

    // =================== Backup Frequency ===================

    public void setBackupFrequency(String frequency) {
        prefs.edit().putString(KEY_FREQUENCY, frequency).apply();
    }
    public String getBackupFrequency() {
        return prefs.getString(KEY_FREQUENCY, "weekly");
    }

    // =================== Legacy / Survey Compatibility ===================

    /** @deprecated Use getTypePreferenceScore() instead. */
    @Deprecated
    public void setCategoryPreference(String category, int score) {
        prefs.edit()
                .putInt("cat_" + category, score)
                .putLong(KEY_SURVEY_TIME, System.currentTimeMillis())
                .apply();
    }

    /** @deprecated Use getTypePreferenceScore() instead. */
    @Deprecated
    public int getCategoryPreference(String category) {
        return prefs.getInt("cat_" + category, 50);
    }

    public float getSurveyDecayFactor() {
        long time = prefs.getLong(KEY_SURVEY_TIME, 0);
        if (time == 0) return 1f; // no decay if never set

        long days = (System.currentTimeMillis() - time) / (1000L * 60 * 60 * 24);
        if (days > 60) return 0.3f;
        if (days > 30) return 0.6f;
        if (days > 14) return 0.8f;
        return 1f;
    }
}
