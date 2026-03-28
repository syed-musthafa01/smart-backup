package com.example.gptbackup.ai;

import android.content.Context;

import com.example.gptbackup.model.FileModel;

/**
 * Fuses four independent priority signals into a final score (0–100):
 *
 *  1. ML Prediction (50%)    — content analysis from PriorityPredictionEngine
 *  2. Behavior Score (25%)   — from BehaviorTracker (opens, shares, backups…)
 *  3. Type Preference (15%)  — from UserPreferenceManager (onboarding answers)
 *  4. Folder Context (10%)   — from ScreenshotDetector.getFolderScoreBonus()
 *
 * Priority Bands:
 *   HIGH   >= 70
 *   MEDIUM >= 40
 *   LOW    <  40
 */
public class PriorityFusionEngine {

    // Core ML analysis carries the most weight
    private static final float ML_WEIGHT       = 0.70f;
    // Behavioral signals (opens, shares, manual backups, skips, deletes)
    private static final float BEHAVIOR_WEIGHT = 0.20f;
    // Folder context (camera, whatsapp, screenshots, downloads)
    private static final float FOLDER_WEIGHT   = 0.10f;

    private final BehaviorTracker behaviorTracker;
    private final UserPreferenceManager preferenceManager;

    public PriorityFusionEngine(Context context) {
        behaviorTracker   = new BehaviorTracker(context);
        preferenceManager = new UserPreferenceManager(context);
    }

    /**
     * Calculates the final priority score (0–100) by fusing all four signals.
     */
    public int calculateFinalPriority(FileModel file, AIAnalysisResult analysis, float mlScore, boolean isFirstTimeUser) {

        float userPreferenceWeight = preferenceManager.getCategoryWeight(file.getType());
        float decay = preferenceManager.getSurveyDecayFactor();

        // Signal 1: ML prediction score
        float mlComponent = mlScore * ML_WEIGHT;

        // Signal 2: Behavior score
        float behaviorScore = behaviorTracker.getBehaviorScore(file.getPath());
        float behaviorComponent = behaviorScore * BEHAVIOR_WEIGHT;

        // Signal 3: Folder context bonus
        float folderBonus = ScreenshotDetector.getFolderScoreBonus(file);
        float folderNormalised = Math.max(0f, Math.min(50f + folderBonus * 2f, 100f));
        float folderComponent = folderNormalised * FOLDER_WEIGHT;

        float baseScore = mlComponent + behaviorComponent + folderComponent;

        // 4. Preference Boost Layer
        // finalScore = contentScore * userPreferenceWeight
        // Smoothly decay user preference impact over time
        float effectiveMultiplier = 1.0f + ((userPreferenceWeight - 1.0f) * decay);
        float finalScore = baseScore * effectiveMultiplier;

        // Final Safeguard Rule: Document keyword detected, Face detected, Group photo
        boolean hasFace = analysis != null && analysis.getFaceCount() > 0;
        boolean hasDocKeyword = false;
        
        if (analysis != null && analysis.getExtractedText() != null) {
            String text = analysis.getExtractedText().toLowerCase();
            String name = file.getName() != null ? file.getName().toLowerCase() : "";
            String[] keywords = {"passport", "license", "visa", "tax", "invoice", "receipt", "contract", "certificate", "resume"};
            for (String kw : keywords) {
                if (text.contains(kw) || name.contains(kw)) {
                    hasDocKeyword = true;
                    break;
                }
            }
        }

        if (hasFace) {
            finalScore = 100f;
        } else if (hasDocKeyword) {
            finalScore = Math.max(finalScore, 50f);
        } else if (mlScore <= 20f && "image".equals(file.getType())) {
            // Strict safeguard: If ML determined this is a LOW priority junk image (like an onion or QR),
            // do not allow user preferences or access counts to inflate it into MEDIUM.
            finalScore = Math.min(finalScore, 35f);
        }

        return (int) Math.max(0f, Math.min(finalScore, 100f));
    }
}
