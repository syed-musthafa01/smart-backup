package com.example.gptbackup.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.example.gptbackup.model.FileModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Analyzes images using Google ML Kit:
 * - Image Labeling (object/scene recognition)
 * - Text Recognition (OCR)
 * - Face Detection
 *
 * ML Kit detector instances are created ONCE and reused across all
 * analyze() calls to avoid reloading TFLite models for every image.
 * Call close() when the entire batch is done to release resources.
 *
 * All ML Kit calls are async but we use Tasks.await() since this
 * class is always called from a background thread.
 */
public class ImageAnalyzer {

    private static final String TAG = "ImageAnalyzer";
    private static final float LABEL_CONFIDENCE_THRESHOLD = 0.5f;

    /** File size threshold (2 MB) above which we use heavier downsampling */
    private static final long LARGE_IMAGE_THRESHOLD = 2 * 1024 * 1024;

    private final Context context;

    // Cached ML Kit detector instances — initialized once, reused for all files
    private final ImageLabeler labeler;
    private final TextRecognizer recognizer;
    private final FaceDetector faceDetector;

    public ImageAnalyzer(Context context) {
        this.context = context;

        // Initialize detectors once — avoids reloading ~12 TFLite models per image
        this.labeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(LABEL_CONFIDENCE_THRESHOLD)
                        .build());

        this.recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS);

        FaceDetectorOptions faceOpts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        this.faceDetector = FaceDetection.getClient(faceOpts);

        Log.d(TAG, "ML Kit detectors initialized (cached for reuse)");
    }

    /**
     * Run full multi-modal analysis on an image file.
     * MUST be called from a background thread.
     */
    public void analyze(FileModel file, AIAnalysisResult result) {
        Log.d("AI_ANALYSIS_START", "ImageAnalyzer: " + file.getName());

        Bitmap bitmap = loadBitmap(file);
        if (bitmap == null) {
            Log.w(TAG, "Could not load bitmap for: " + file.getName());
            result.setAnalysisComplete(true);
            return;
        }

        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);

        Task<List<ImageLabel>> labelTask = labeler.process(inputImage)
                .addOnSuccessListener(labels -> {
                    for (ImageLabel label : labels) {
                        result.addLabel(label.getText());
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Image labeling failed", e));

        Task<Text> textTask = recognizer.process(inputImage)
                .addOnSuccessListener(text -> {
                    String detected = text.getText();
                    result.setTextDetected(detected != null && !detected.trim().isEmpty());
                    if (detected != null) {
                        result.setExtractedText(detected);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Text recognition failed", e));

        Task<List<Face>> faceTask = faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> result.setFaceCount(faces.size()))
                .addOnFailureListener(e -> Log.w(TAG, "Face detection failed", e));

        try {
            Tasks.await(Tasks.whenAllComplete(labelTask, textTask, faceTask));
        } catch (Exception e) {
            Log.w(TAG, "Concurrent ML Kit tasks failed", e);
        }

        // Recycle bitmap to free memory immediately
        bitmap.recycle();

        // Compute overall confidence from labels
        if (!result.getLabels().isEmpty()) {
            result.setConfidenceScore(
                    Math.min(result.getLabels().size() * 15f, 100f));
        }

        result.setAnalysisComplete(true);

        Log.d("AI_IMAGE_RESULT", "File=" + file.getName()
                + " labels=" + result.getLabels().size()
                + " text=" + result.isTextDetected()
                + " faces=" + result.getFaceCount()
                + " confidence=" + result.getConfidenceScore());
    }

    /**
     * Analyze a Bitmap directly (used by VideoAnalyzer for frame analysis).
     */
    public void analyzeBitmap(Bitmap bitmap, AIAnalysisResult result) {
        if (bitmap == null) return;
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);

        Task<List<ImageLabel>> labelTask = labeler.process(inputImage)
                .addOnSuccessListener(labels -> {
                    for (ImageLabel label : labels) {
                        result.addLabel(label.getText());
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Image labeling failed", e));

        Task<List<Face>> faceTask = faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> result.setFaceCount(faces.size()))
                .addOnFailureListener(e -> Log.w(TAG, "Face detection failed", e));

        try {
            Tasks.await(Tasks.whenAllComplete(labelTask, faceTask));
        } catch (Exception e) {
            Log.w(TAG, "Concurrent ML Kit tasks failed", e);
        }

        result.setAnalysisComplete(true);
    }

    /**
     * Release all cached ML Kit detector resources.
     * Call this after the entire batch of files has been processed.
     */
    public void close() {
        try { labeler.close(); } catch (Exception e) { Log.w(TAG, "Error closing labeler", e); }
        try { recognizer.close(); } catch (Exception e) { Log.w(TAG, "Error closing recognizer", e); }
        try { faceDetector.close(); } catch (Exception e) { Log.w(TAG, "Error closing face detector", e); }
        Log.d(TAG, "ML Kit detectors closed");
    }



    // ================= Bitmap Loading =================

    private Bitmap loadBitmap(FileModel file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            
            // Extreme downsampling based on file size to dramatically speed up ML Analysis
            // and prevent "too much time" processing. ML Kit works perfectly on ~480p dimensions.
            long sizeMB = file.getSize() / (1024 * 1024);
            if (sizeMB >= 5) {
                options.inSampleSize = 16; // 8MB becomes tiny and almost instant
            } else if (sizeMB >= 2) {
                options.inSampleSize = 8;
            } else if (sizeMB >= 1) {
                options.inSampleSize = 4;
            } else {
                options.inSampleSize = 2;
            }

            if (file.isFromMediaStore() && file.getContentUri() != null) {
                InputStream is = context.getContentResolver()
                        .openInputStream(file.getContentUri());
                if (is != null) {
                    Bitmap bmp = BitmapFactory.decodeStream(is, null, options);
                    is.close();
                    return bmp;
                }
            } else if (file.getPath() != null) {
                File localFile = new File(file.getPath());
                if (localFile.exists()) {
                    return BitmapFactory.decodeFile(localFile.getAbsolutePath(), options);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Bitmap load failed: " + file.getName(), e);
        }
        return null;
    }
}
