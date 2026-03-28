package com.example.gptbackup.ai;

import android.content.Context;
import android.util.Log;

import com.example.gptbackup.model.FileModel;

import java.util.List;

/**
 * Rule-based prediction engine that determines backup priority score (0–100)
 * from ML analysis results, file metadata, folder context, and user preferences.
 *
 * Priority bands:
 *   HIGH:   70–100
 *   MEDIUM: 40–69
 *   LOW:    0–39
 *
 * Key improvements over previous version:
 *  - Screenshots are always capped at LOW (hard penalty)
 *  - Camera/WhatsApp folders boost priority
 *  - Text detection only boosts score when NOT a screenshot
 *  - Face detection gives strong boost (personal photos)
 *  - Folder context adjusts all file types
 *  - User onboarding preferences modulate the base scores
 */
public class PriorityPredictionEngine {

    private static final String TAG = "PriorityPrediction";

    private final UserPreferenceManager userPrefs;

    public PriorityPredictionEngine(Context context) {
        this.userPrefs = new UserPreferenceManager(context);
    }

    /**
     * Predict priority score (0-100) based on AI analysis and file metadata.
     */
    public float predict(FileModel file, AIAnalysisResult analysis, boolean isFirstTimeUser) {
        float score;

        String category = analysis.getFileCategory();
        if (category == null || category.isEmpty()) category = file.getType();
        if (category == null) category = "other";

        switch (category) {
            case "image":    score = predictImage(file, analysis); break;
            case "video":    score = predictVideo(file, analysis); break;
            case "audio":    score = predictAudio(file, analysis); break;
            case "document": score = predictDocument(file, analysis); break;
            default:         score = predictGeneral(file, analysis); break;
        }

        // ---- Global modifiers (apply to all types) ----

        // Only apply access bonuses if the file already has some intrinsic value (score > 20f)
        // to prevent random junk files (e.g. onions, QR codes) from being artificially inflated.
        boolean isHardCapped = (score <= 20f);

        if (!isHardCapped) {
            // Access frequency bonus
            if (isFirstTimeUser && file.getAccessCount() >= 5) {
                score += 10f; // Frequently accessed files -> +10 bonus
            }
            score += Math.min(file.getAccessCount() * 4f, 18f);
        }

        // File-Type Priority Floor
        if (!isHardCapped) {
            if ("document".equals(category) || "document".equals(file.getType())) {
                score = Math.max(score, 55f);
            }
            if ("video".equals(category) || "video".equals(file.getType())) {
                long durationSec = analysis.getMediaDuration() / 1000;
                boolean isPersonalVideo = durationSec > 30 || analysis.getFaceCount() > 0;
                if (isPersonalVideo) score = Math.max(score, 60f);
                else score = Math.max(score, 45f);
            }
            if ("audio".equals(category) || "audio".equals(file.getType())) {
                score = Math.max(score, 45f);
            }
            if ("image".equals(category) || "image".equals(file.getType())) {
                if (analysis.getFaceCount() > 0) {
                    score = 100f; // Highest priority for human faces
                }
            }
        }

        float finalScore = Math.max(0f, Math.min(score, 100f));

        String priorityLabel = finalScore >= 70 ? "HIGH"
                : finalScore >= 40 ? "MEDIUM" : "LOW";

        Log.d("AI_PRIORITY_RESULT", "File=" + file.getName()
                + " folder=" + ScreenshotDetector.getFolderCategory(file)
                + " screenshot=" + ScreenshotDetector.isScreenshot(file, analysis)
                + " score=" + finalScore
                + " priority=" + priorityLabel);

        return finalScore;
    }

    // =================== Image ===================

    private float predictImage(FileModel file, AIAnalysisResult analysis) {
        String text = analysis.getExtractedText() != null ? analysis.getExtractedText().toLowerCase() : "";
        String filename = file.getName() != null ? file.getName().toLowerCase() : "";
        List<String> labels = analysis.getLabels();

        boolean isVerifiedFace = false;
        if (analysis.getFaceCount() > 0) {
            String[] faceKeywords = {"face", "person", "human", "people", "man", "woman", "child", "girl", "boy", "portrait", "selfie"};
            for (String label : labels) {
                String l = label.toLowerCase();
                for (String kw : faceKeywords) {
                    if (l.contains(kw)) { isVerifiedFace = true; break; }
                }
                if (isVerifiedFace) break;
            }
        }

        boolean isExplicitJunk = false;
        String[] junkKeywords = {"qr code", "barcode", "food", "vegetable", "fruit", "onion", "meme"};
        for (String label : labels) {
            String l = label.toLowerCase();
            for (String kw : junkKeywords) {
                if (l.contains(kw)) { isExplicitJunk = true; break; }
            }
            if (isExplicitJunk) break;
        }

        boolean isHighValueDoc = false;
        boolean isMediumValueDoc = false;
        boolean isLowValueDoc = false;

        // High value keywords/labels
        String[] highKeywords = {"passport", "license", "visa", "tax", "invoice", "receipt", "contract", "agreement", "confidential", "certificate", "degree", "diploma", "resume", "cv", "ticket", "boarding pass", "aadhar", "voter id", "pan card", "official", "marksheet", "bonafide", "transcript"};
        for (String kw : highKeywords) {
            if (text.contains(kw) || filename.contains(kw)) { isHighValueDoc = true; break; }
        }
        for (String label : labels) {
            String l = label.toLowerCase();
            if (l.contains("identity document") || l.contains("passport") || l.contains("driving license") || l.contains("receipt") || l.contains("invoice")) {
                isHighValueDoc = true; break;
            }
        }

        // Medium value keywords/labels
        String[] mediumKeywords = {"project", "report", "meeting", "minutes", "assignment", "draft", "notes", "summary", "schedule", "presentation", "slides", "study", "material", "homework", "syllabus"};
        if (!isHighValueDoc) {
            for (String kw : mediumKeywords) {
                if (text.contains(kw) || filename.contains(kw)) { isMediumValueDoc = true; break; }
            }
            for (String label : labels) {
                String l = label.toLowerCase();
                if (l.contains("document") || l.contains("paper") || l.contains("text") || l.contains("presentation")) {
                    isMediumValueDoc = true; break;
                }
            }
        }

        // Low value keywords (override medium but not high)
        String[] lowKeywords = {"circular", "announcement", "notice", "newsletter", "meme", "joke"};
        if (!isHighValueDoc && !isMediumValueDoc) {
            for (String kw : lowKeywords) {
                if (text.contains(kw) || filename.contains(kw)) { isLowValueDoc = true; break; }
            }
        }

        boolean isScreenshot = ScreenshotDetector.isScreenshot(file, analysis);

        // Score Calculation
        float score = 20f; // Strict base LOW for uninteresting stuff

        if (isVerifiedFace) {
            return 100f; // Highest priority for human faces images (Age-agnostic)
        }
        
        if (isExplicitJunk || isScreenshot) {
            return 20f; // Hard cap junk/screenshots
        }

        if (isHighValueDoc) {
            score = 80f; // HIGH for important document images
        } else if (isMediumValueDoc) {
            score = 50f; // Base MEDIUM
        } else if (isLowValueDoc) {
            score = 20f; // Hard LOW
        } else {
            // Normal photo logic (like QR codes, food, generic)
            score = 20f; 
        }

        // Duplicate / Junk filtering proxy
        if (filename.contains("copy") || filename.contains("(1)") || filename.contains("(2)") || filename.contains("duplicate")) {
            score -= 15f;
        } else if (analysis.getConfidenceScore() > 0f && analysis.getConfidenceScore() < 60f) {
            score -= 10f; // low quality/blur penalty
        } 

        // ONLY apply folder and confidence bonuses to meaningful documents, NOT to random images
        if (isHighValueDoc || isMediumValueDoc) {
            score += ScreenshotDetector.getFolderScoreBonus(file);
            if (analysis.getConfidenceScore() > 90f) score += 10f;
        }

        return score;
    }

    // =================== Video ===================

    private float predictVideo(FileModel file, AIAnalysisResult analysis) {
        float score = 30f; // base for video

        long durationSec = analysis.getMediaDuration() / 1000;

        // Duration: longer videos are more valuable (memories, not random clips)
        if (durationSec > 300)       score += 28f; // > 5 min
        else if (durationSec > 60)   score += 18f; // > 1 min
        else if (durationSec > 10)   score += 8f;  // > 10 sec

        // First frame had faces (personal video)
        if (analysis.getFaceCount() > 0) score += 20f;

        // High resolution video
        String res = analysis.getResolution();
        if (res != null && !res.isEmpty()) {
            try {
                int width = Integer.parseInt(res.split("x")[0].trim());
                if (width >= 1920)     score += 10f;
                else if (width >= 1280) score += 5f;
            } catch (Exception ignored) {}
        }

        // Folder context
        score += ScreenshotDetector.getFolderScoreBonus(file);

        return score;
    }

    // =================== Audio ===================

    private float predictAudio(FileModel file, AIAnalysisResult analysis) {
        float score = 22f; // base for audio

        long durationSec = analysis.getMediaDuration() / 1000;

        // Duration: longer = recordings or meaningful content
        if (durationSec > 300) score += 20f; // > 5 min
        else if (durationSec > 60) score += 12f;
        else if (durationSec > 10) score += 5f;

        // Folder context
        score += ScreenshotDetector.getFolderScoreBonus(file);

        return score;
    }

    // =================== Document ===================

    private float predictDocument(FileModel file, AIAnalysisResult analysis) {
        float score = 45f; // Base

        String filename = file.getName() != null ? file.getName().toLowerCase() : "";

        boolean isHighValueDoc = false;
        boolean isMediumValueDoc = false;
        boolean isLowValueDoc = false;

        String[] highKeywords = {"passport", "license", "visa", "tax", "invoice", "receipt", "contract", "agreement", "confidential", "certificate", "degree", "diploma", "resume", "cv", "ticket", "boarding pass", "aadhar", "voter id", "pan card", "official", "marksheet", "bonafide", "transcript"};
        for (String kw : highKeywords) {
            if (filename.contains(kw)) { isHighValueDoc = true; break; }
        }

        String[] mediumKeywords = {"project", "report", "meeting", "minutes", "assignment", "draft", "notes", "summary", "schedule", "presentation", "slides", "study", "material", "homework", "syllabus"};
        if (!isHighValueDoc) {
            for (String kw : mediumKeywords) {
                if (filename.contains(kw)) { isMediumValueDoc = true; break; }
            }
        }

        String[] lowKeywords = {"circular", "announcement", "notice", "newsletter", "meme", "joke"};
        if (!isHighValueDoc && !isMediumValueDoc) {
            for (String kw : lowKeywords) {
                if (filename.contains(kw)) { isLowValueDoc = true; break; }
            }
        }

        if (isHighValueDoc) {
            score = 85f;
        } else if (isMediumValueDoc) {
            score = 60f;
        } else if (isLowValueDoc) {
            score = 25f;
        } else {
            score = 45f; // Base document score
        }

        // Multi-page documents are more important
        int pages = analysis.getPageCount();
        if (pages > 10)      score += 15f;
        else if (pages > 3)  score += 8f;

        // Folder context
        score += ScreenshotDetector.getFolderScoreBonus(file);

        return score;
    }

    // =================== Generic ===================

    private float predictGeneral(FileModel file, AIAnalysisResult analysis) {
        float score = 12f; // low base for unknown types

        return score;
    }
}

