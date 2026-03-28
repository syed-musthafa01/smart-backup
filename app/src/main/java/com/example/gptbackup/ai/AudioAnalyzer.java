package com.example.gptbackup.ai;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.example.gptbackup.model.FileModel;

import java.io.File;

/**
 * Analyzes audio files:
 * - Extracts duration and bitrate via MediaMetadataRetriever
 * - Considers file size and recency for priority scoring
 */
public class AudioAnalyzer {

    private static final String TAG = "AudioAnalyzer";

    private final Context context;

    public AudioAnalyzer(Context context) {
        this.context = context;
    }

    /**
     * Analyze an audio file. MUST be called from a background thread.
     */
    public void analyze(FileModel file, AIAnalysisResult result) {
        Log.d("AI_ANALYSIS_START", "AudioAnalyzer: " + file.getName());
        
        // Note: Duration is already extracted by FileMetadataExtractor 
        // and populated in result.getMediaDuration(). No need for redundant MediaMetadataRetriever.

        // Compute a basic confidence based on size (recency removed)
        float confidence = 0f;

        if (file.getSize() > 5 * 1024 * 1024) confidence += 20f; // > 5MB
        if (result.getMediaDuration() > 60_000) confidence += 15f; // > 1 min

        result.setConfidenceScore(Math.min(confidence, 100f));
        result.setAnalysisComplete(true);

        Log.d("AI_AUDIO_RESULT", "File=" + file.getName()
                + " duration=" + result.getMediaDuration() + "ms"
                + " confidence=" + result.getConfidenceScore());
    }
}
