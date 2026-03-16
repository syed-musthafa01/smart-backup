package com.example.gptbackup.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREF_NAME = "gpt_backup_settings";
    private static volatile SettingsManager instance;
    private final SharedPreferences prefs;

    // Keys
    private static final String KEY_AUTO_BACKUP = "auto_backup";
    private static final String KEY_BACKUP_HIGH_PRIORITY_ONLY = "backup_high_priority_only";
    private static final String KEY_ALLOW_BACKGROUND_UPLOAD = "allow_background_upload";
    private static final String KEY_SHOW_UPLOAD_NOTIFICATION = "show_upload_notification";
    private static final String KEY_MIN_PRIORITY = "min_priority";
    private static final String KEY_MAX_FILE_LIMIT = "max_file_limit";
    private static final String KEY_INCLUDE_IMAGES = "include_images";
    private static final String KEY_INCLUDE_DOCUMENTS = "include_documents";
    private static final String KEY_INCLUDE_VIDEOS = "include_videos";
    private static final String KEY_UPLOAD_ON_WIFI_ONLY = "upload_on_wifi_only";
    private static final String KEY_ALLOW_MOBILE_DATA = "allow_mobile_data";
    private static final String KEY_UPLOAD_ONLY_WHILE_CHARGING = "upload_only_while_charging";
    private static final String KEY_ENABLE_SMART_OPTIMIZATION = "enable_smart_optimization";
    private static final String KEY_UNLIMITED_CONCURRENT_UPLOADS = "unlimited_concurrent_uploads";


    private SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SettingsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SettingsManager.class) {
                if (instance == null) {
                    instance = new SettingsManager(context);
                }
            }
        }
        return instance;
    }

    // Backup Behavior
    public void setAutoBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply();
    }

    public boolean isAutoBackupEnabled() {
        return prefs.getBoolean(KEY_AUTO_BACKUP, false);
    }

    public void setBackupHighPriorityOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_BACKUP_HIGH_PRIORITY_ONLY, enabled).apply();
    }

    public boolean isBackupHighPriorityOnly() {
        return prefs.getBoolean(KEY_BACKUP_HIGH_PRIORITY_ONLY, false);
    }

    public void setAllowBackgroundUpload(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALLOW_BACKGROUND_UPLOAD, enabled).apply();
    }

    public boolean isAllowBackgroundUpload() {
        return prefs.getBoolean(KEY_ALLOW_BACKGROUND_UPLOAD, false);
    }

    public void setShowUploadNotification(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOW_UPLOAD_NOTIFICATION, enabled).apply();
    }

    public boolean isShowUploadNotification() {
        return prefs.getBoolean(KEY_SHOW_UPLOAD_NOTIFICATION, true);
    }

    // File Filtering Preferences
    public void setMinPriority(int priority) {
        prefs.edit().putInt(KEY_MIN_PRIORITY, priority).apply();
    }

    public int getMinPriority() {
        return prefs.getInt(KEY_MIN_PRIORITY, 0);
    }

    public void setMaxFileLimit(int limit) {
        prefs.edit().putInt(KEY_MAX_FILE_LIMIT, limit).apply();
    }

    public int getMaxFileLimit() {
        return prefs.getInt(KEY_MAX_FILE_LIMIT, 200);
    }

    public void setIncludeImages(boolean enabled) {
        prefs.edit().putBoolean(KEY_INCLUDE_IMAGES, enabled).apply();
    }

    public boolean isIncludeImages() {
        return prefs.getBoolean(KEY_INCLUDE_IMAGES, true);
    }

    public void setIncludeDocuments(boolean enabled) {
        prefs.edit().putBoolean(KEY_INCLUDE_DOCUMENTS, enabled).apply();
    }

    public boolean isIncludeDocuments() {
        return prefs.getBoolean(KEY_INCLUDE_DOCUMENTS, true);
    }

    public void setIncludeVideos(boolean enabled) {
        prefs.edit().putBoolean(KEY_INCLUDE_VIDEOS, enabled).apply();
    }

    public boolean isIncludeVideos() {
        return prefs.getBoolean(KEY_INCLUDE_VIDEOS, true);
    }

    // Network Settings
    public void setUploadOnWifiOnly(boolean enabled) {
        prefs.edit().putBoolean(KEY_UPLOAD_ON_WIFI_ONLY, enabled).apply();
    }

    public boolean isUploadOnWifiOnly() {
        return prefs.getBoolean(KEY_UPLOAD_ON_WIFI_ONLY, true);
    }

    public void setAllowMobileData(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALLOW_MOBILE_DATA, enabled).apply();
    }

    public boolean isAllowMobileData() {
        return prefs.getBoolean(KEY_ALLOW_MOBILE_DATA, false);
    }

    public void setUploadOnlyWhileCharging(boolean enabled) {
        prefs.edit().putBoolean(KEY_UPLOAD_ONLY_WHILE_CHARGING, enabled).apply();
    }

    public boolean isUploadOnlyWhileCharging() {
        return prefs.getBoolean(KEY_UPLOAD_ONLY_WHILE_CHARGING, false);
    }

    // Performance Settings
    public void setEnableSmartOptimization(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLE_SMART_OPTIMIZATION, enabled).apply();
    }

    public boolean isEnableSmartOptimization() {
        return prefs.getBoolean(KEY_ENABLE_SMART_OPTIMIZATION, true);
    }

    public void setUnlimitedConcurrentUploads(boolean enabled) {
        prefs.edit().putBoolean(KEY_UNLIMITED_CONCURRENT_UPLOADS, enabled).apply();
    }

    public boolean isUnlimitedConcurrentUploads() {
        return prefs.getBoolean(KEY_UNLIMITED_CONCURRENT_UPLOADS, false);
    }
}
