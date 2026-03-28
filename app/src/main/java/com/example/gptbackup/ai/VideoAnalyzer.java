package com.example.gptbackup.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.example.gptbackup.model.FileModel;

import java.io.File;

/**
 * Analyzes video files:
 * - Extracts duration, resolution, size, creation date via MediaMetadataRetriever
 * - Optionally analyzes the first frame using ImageAnalyzer
 */
public class VideoAnalyzer {

    private static final String TAG = "VideoAnalyzer";

    private final Context context;
    private final ImageAnalyzer imageAnalyzer;

    public VideoAnalyzer(Context context, ImageAnalyzer sharedImageAnalyzer) {
        this.context = context;
        this.imageAnalyzer = sharedImageAnalyzer;
    }

    /**
     * Analyze a video file. MUST be called from a background thread.
     */
    public void analyze(FileModel file, AIAnalysisResult result) {
        Log.d("AI_ANALYSIS_START", "VideoAnalyzer: " + file.getName());

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (file.isFromMediaStore() && file.getContentUri() != null) {
                retriever.setDataSource(context, file.getContentUri());
            } else if (file.getPath() != null) {
                File localFile = new File(file.getPath());
                if (!localFile.exists()) {
                    Log.w(TAG, "Video file not found: " + file.getPath());
                    result.setAnalysisComplete(true);
                    return;
                }
                retriever.setDataSource(localFile.getAbsolutePath());
            } else {
                result.setAnalysisComplete(true);
                return;
            }

            // Resolution and Duration are already extracted by FileMetadataExtractor.
            // We only need the retriever for the first frame analysis.

            // First frame analysis (optional, lightweight)
            analyzeFirstFrame(retriever, result);

        } catch (Exception e) {
            Log.w(TAG, "Video analysis failed: " + file.getName(), e);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }

        result.setAnalysisComplete(true);

        Log.d("AI_VIDEO_RESULT", "File=" + file.getName()
                + " duration=" + result.getMediaDuration() + "ms"
                + " resolution=" + result.getResolution()
                + " frameLabels=" + result.getLabels().size());
    }

    /**
     * Extract and analyze the first frame of the video.
     */
    private void analyzeFirstFrame(MediaMetadataRetriever retriever,
                                    AIAnalysisResult result) {
        try {
            Bitmap frame = retriever.getFrameAtTime(
                    0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame != null) {
                imageAnalyzer.analyzeBitmap(frame, result);
                frame.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "First frame analysis failed", e);
        }
    }
}
