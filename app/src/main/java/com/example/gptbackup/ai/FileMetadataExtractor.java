package com.example.gptbackup.ai;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import com.example.gptbackup.model.FileModel;

import java.io.File;

/**
 * Extracts metadata for ALL file types.
 * Populates an AIAnalysisResult with basic file info + media metadata.
 */
public class FileMetadataExtractor {

    private static final String TAG = "FileMetadataExtractor";

    private final Context context;

    public FileMetadataExtractor(Context context) {
        this.context = context;
    }

    /**
     * Extract metadata from a FileModel and populate the result.
     */
    public AIAnalysisResult extractMetadata(FileModel file) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.setFileCategory(file.getType());

        // Media duration + resolution for audio/video
        if (file.isVideo() || file.isAudio()) {
            extractMediaMetadata(file, result);
        }

        // Resolution for images
        if (file.isImage()) {
            extractImageResolution(file, result);
        }

        Log.d(TAG, "Metadata extracted: " + file.getName()
                + " type=" + file.getType()
                + " size=" + file.getSize()
                + " duration=" + result.getMediaDuration());

        return result;
    }

    private void extractMediaMetadata(FileModel file, AIAnalysisResult result) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (file.isFromMediaStore() && file.getContentUri() != null) {
                retriever.setDataSource(context, file.getContentUri());
            } else if (file.getPath() != null) {
                File localFile = new File(file.getPath());
                if (localFile.exists()) {
                    retriever.setDataSource(localFile.getAbsolutePath());
                } else {
                    return;
                }
            } else {
                return;
            }

            // Duration
            String durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                result.setMediaDuration(Long.parseLong(durationStr));
            }

            // Resolution (video only)
            if (file.isVideo()) {
                String width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (width != null && height != null) {
                    result.setResolution(width + "x" + height);
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Failed to extract media metadata: " + file.getName(), e);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    private void extractImageResolution(FileModel file, AIAnalysisResult result) {
        try {
            android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            if (file.isFromMediaStore() && file.getContentUri() != null) {
                java.io.InputStream is = context.getContentResolver()
                        .openInputStream(file.getContentUri());
                if (is != null) {
                    android.graphics.BitmapFactory.decodeStream(is, null, options);
                    is.close();
                }
            } else if (file.getPath() != null) {
                android.graphics.BitmapFactory.decodeFile(file.getPath(), options);
            }

            if (options.outWidth > 0 && options.outHeight > 0) {
                result.setResolution(options.outWidth + "x" + options.outHeight);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract image resolution: " + file.getName(), e);
        }
    }
}
