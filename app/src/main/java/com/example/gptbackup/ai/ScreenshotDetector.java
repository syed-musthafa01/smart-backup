package com.example.gptbackup.ai;

import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.example.gptbackup.model.FileModel;

/**
 * Lightweight utility to detect screenshots and classify folder context.
 * No ML, no I/O — purely path/name/resolution heuristics.
 */
public class ScreenshotDetector {

    // Known screenshot path segments (case-insensitive)
    private static final String[] SCREENSHOT_PATH_SEGMENTS = {
            "/screenshots/", "/screenshot/"
    };

    // Known screenshot filename prefixes (lowercase)
    private static final String[] SCREENSHOT_NAME_PREFIXES = {
            "screenshot", "screen_", "screen-", "capture_", "captura_"
    };

    // Known high-priority folder path segments → folder category
    private static final String[][] FOLDER_HINTS = {
            {"/screenshots/",  "screenshots"},
            {"/screenshot/",   "screenshots"},
            {"/camera/",       "camera"},
            {"/dcim/",         "camera"},
            {"/whatsapp/",     "whatsapp"},
            {"/downloads/",    "downloads"},
            {"/download/",     "downloads"},
            {"/documents/",    "documents"},
            {"/telegram/",     "telegram"},
            {"/instagram/",    "instagram"},
            {"/signal/",       "signal"},
    };

    /**
     * Returns true if this image file is likely a screenshot.
     * Checks: path segments, filename prefix, and resolution.
     */
    public static boolean isScreenshot(FileModel file, AIAnalysisResult analysis) {
        if (file == null) return false;

        String path = file.getPath() != null ? file.getPath().toLowerCase() : "";
        String name = file.getName() != null ? file.getName().toLowerCase() : "";

        // 1. Path contains a screenshots folder segment
        for (String seg : SCREENSHOT_PATH_SEGMENTS) {
            if (path.contains(seg)) return true;
        }

        // 2. Filename starts with a screenshot prefix
        for (String prefix : SCREENSHOT_NAME_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }

        // 3. Resolution matches typical mobile screen dimensions
        if (analysis != null) {
            String resolution = analysis.getResolution();
            if (resolution != null && isScreenResolution(resolution)) return true;
        }

        return false;
    }

    /**
     * Classify the folder context of a file.
     * Returns one of: "camera", "whatsapp", "screenshots", "downloads",
     *                 "documents", "telegram", "instagram", "signal", "other"
     */
    public static String getFolderCategory(FileModel file) {
        if (file == null || file.getPath() == null) return "other";
        String path = file.getPath().toLowerCase();
        for (String[] hint : FOLDER_HINTS) {
            if (path.contains(hint[0])) return hint[1];
        }
        return "other";
    }

    /**
     * Returns a folder-based priority delta (-25..+20) based on where the file lives.
     * Designed to be additive to the base ML score.
     */
    public static float getFolderScoreBonus(FileModel file) {
        switch (getFolderCategory(file)) {
            case "camera":     return 10f;   // Slight bump for camera
            case "whatsapp":   return 5f;    // Minor bump for whatsapp
            case "downloads":  return -15f;  // downloads are often expendable
            case "screenshots":return -20f;  // screenshots are very low value
            default:           return 0f;    // Neutral for generic folders
        }
    }

    /**
     * Get the resolution string from FileModel if available.
     * Returns null if not set.
     */
    private static boolean isScreenResolution(String resolution) {
        if (resolution == null || !resolution.contains("x")) return false;
        try {
            String[] parts = resolution.split("x");
            if (parts.length < 2) return false;
            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());

            // Common full-screen portrait resolutions (width < height)
            int minDim = Math.min(w, h);
            int maxDim = Math.max(w, h);

            // Portrait screen resolutions: 720p, 1080p, 1440p, 2k, common ratios
            boolean isCommonWidth  = (minDim == 720 || minDim == 1080 || minDim == 1440 || minDim == 1284 || minDim == 1179 || minDim == 393 || minDim == 412);
            boolean isCommonHeight = (maxDim >= 1280 && maxDim <= 3200);
            // Aspect ratio between 1.7 and 2.4 (typical phones)
            float ratio = (float) maxDim / minDim;
            boolean isPhoneRatio = ratio >= 1.7f && ratio <= 2.5f;

            return isCommonWidth && isCommonHeight && isPhoneRatio;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
