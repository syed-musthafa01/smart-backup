package com.example.gptbackup.ai;

import com.example.gptbackup.model.FileModel;

/**
 * Enhanced feature extractor that produces a richer feature vector
 * from file metadata and AI analysis results.
 */
public class FeatureExtractor {

    /**
     * Extract basic features from a FileModel (used for cold-start scoring).
     */
    public static float[] extract(FileModel file) {

        float sizeMB = file.getSize() / (1024f * 1024f);

        float image = file.isImage() ? 1f : 0f;
        float video = file.isVideo() ? 1f : 0f;
        float audio = file.isAudio() ? 1f : 0f;
        float doc = file.isDocument() ? 1f : 0f;

        float recency = 0f; // Time score completely removed
        float accessFreq = Math.min(file.getAccessCount() / 10f, 1f);

        return new float[]{
                sizeMB,
                image,
                video,
                audio,
                doc,
                recency,
                accessFreq
        };
    }

    /**
     * Extract enhanced features combining file metadata + AI analysis.
     */
    public static float[] extractWithAnalysis(FileModel file,
                                               AIAnalysisResult analysis) {
        float[] base = extract(file);

        float labelCount = analysis.getLabels().size() / 10f;
        float hasFaces = analysis.getFaceCount() > 0 ? 1f : 0f;
        float hasText = analysis.isTextDetected() ? 1f : 0f;
        float confidence = analysis.getConfidenceScore() / 100f;
        float durationMin = analysis.getMediaDuration() / 60_000f;
        float pageCount = analysis.getPageCount() / 50f;

        return new float[]{
                base[0], base[1], base[2], base[3], base[4],
                base[5], base[6],
                labelCount,
                hasFaces,
                hasText,
                confidence,
                Math.min(durationMin, 1f),
                Math.min(pageCount, 1f)
        };
    }
}
