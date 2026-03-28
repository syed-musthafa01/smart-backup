package com.example.gptbackup.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks user behavior signals per file path to inform priority scoring.
 *
 * Signals tracked:
 *  - File opens         (+5 per open, up to +30)
 *  - File shares        (+12 per share, up to +30)
 *  - Manual backups     (+20, strong positive)
 *  - Backup skips       (-10 per skip)
 *  - File deletions     (-25, strong negative)
 *  - Recent open bonus  (+20 if opened in last 3 days)
 */
public class BehaviorTracker {

    private static final String PREF_NAME = "behavior_stats";

    // Key suffixes for per-file signals
    private static final String SUFFIX_OPEN    = "_open";
    private static final String SUFFIX_LAST    = "_last";
    private static final String SUFFIX_SKIP    = "_skip";
    private static final String SUFFIX_SHARE   = "_share";
    private static final String SUFFIX_DELETE  = "_delete";
    private static final String SUFFIX_BACKUP  = "_backup";

    private final SharedPreferences prefs;

    public BehaviorTracker(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // =================== Recording Events ===================

    public void recordFileOpen(String path) {
        if (path == null) return;
        int count = prefs.getInt(path + SUFFIX_OPEN, 0);
        prefs.edit()
                .putInt(path + SUFFIX_OPEN, count + 1)
                .putLong(path + SUFFIX_LAST, System.currentTimeMillis())
                .apply();
    }

    public void recordFileShare(String path) {
        if (path == null) return;
        int count = prefs.getInt(path + SUFFIX_SHARE, 0);
        prefs.edit().putInt(path + SUFFIX_SHARE, count + 1).apply();
    }

    public void recordFileDeletion(String path) {
        if (path == null) return;
        int count = prefs.getInt(path + SUFFIX_DELETE, 0);
        prefs.edit().putInt(path + SUFFIX_DELETE, count + 1).apply();
    }

    public void recordManualBackup(String path) {
        if (path == null) return;
        int count = prefs.getInt(path + SUFFIX_BACKUP, 0);
        prefs.edit().putInt(path + SUFFIX_BACKUP, count + 1).apply();
    }

    public void recordBackupSkip(String path) {
        if (path == null) return;
        int skip = prefs.getInt(path + SUFFIX_SKIP, 0);
        prefs.edit().putInt(path + SUFFIX_SKIP, skip + 1).apply();
    }

    // =================== Scoring ===================

    /**
     * Compute a behavior-based score (0–100) for a given file path.
     *
     * Positive signals: opens, shares, manual backups, recent activity
     * Negative signals: skips, deletions
     */
    public float getBehaviorScore(String path) {
        if (path == null) return 50f;

        int openCount   = prefs.getInt(path + SUFFIX_OPEN,   0);
        int shareCount  = prefs.getInt(path + SUFFIX_SHARE,  0);
        int backupCount = prefs.getInt(path + SUFFIX_BACKUP, 0);
        int skipCount   = prefs.getInt(path + SUFFIX_SKIP,   0);
        int deleteCount = prefs.getInt(path + SUFFIX_DELETE, 0);
        long lastOpen   = prefs.getLong(path + SUFFIX_LAST,  0);

        float score = 50f; // neutral baseline

        // Positive signals
        score += Math.min(openCount   * 5f,  30f);
        score += Math.min(shareCount  * 12f, 30f);
        score += Math.min(backupCount * 20f, 40f);

        // Recent open bonus
        long millisIn3Days = 3L * 24 * 60 * 60 * 1000;
        if (lastOpen > 0 && (System.currentTimeMillis() - lastOpen) < millisIn3Days) {
            score += 20f;
        }

        // Negative signals
        score -= Math.min(skipCount   * 10f, 40f);
        score -= Math.min(deleteCount * 25f, 50f);

        return Math.max(0f, Math.min(score, 100f));
    }
}
