package com.example.gptbackup.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.gptbackup.model.FileModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FilePriorityEngine — ML Kit Multi-Modal Analysis Pipeline
 *
 * New flow:
 * 1. FileMetadataExtractor → collect basic metadata
 * 2. Type-specific Analyzer (Image/Video/Audio/Document) → ML analysis
 * 3. PriorityPredictionEngine → rule-based priority from analysis
 * 4. PriorityFusionEngine → final fusion with behavior + survey
 */
public class FilePriorityEngine {

    private static final String TAG = "FilePriorityEngine";
    private static final String PREF_NAME = "backup_stats";
    private static final String KEY_TOTAL_BACKUPS = "total_backups";

    private final SharedPreferences prefs;
    private final Context context;

    // Analysis modules
    private final FileMetadataExtractor metadataExtractor;
    private final ImageAnalyzer imageAnalyzer;
    private final VideoAnalyzer videoAnalyzer;
    private final AudioAnalyzer audioAnalyzer;
    private final DocumentAnalyzer documentAnalyzer;
    private final PriorityPredictionEngine predictionEngine;

    public FilePriorityEngine(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        this.metadataExtractor = new FileMetadataExtractor(context);
        this.imageAnalyzer = new ImageAnalyzer(context);
        this.videoAnalyzer = new VideoAnalyzer(context, this.imageAnalyzer);
        this.audioAnalyzer = new AudioAnalyzer(context);
        this.documentAnalyzer = new DocumentAnalyzer();
        this.predictionEngine = new PriorityPredictionEngine(context);
    }

    /**
     * Assign AI-driven priorities to all files.
     * Runs the full multi-modal analysis pipeline.
     */
    public void assignPriorities(List<FileModel> files, Context context) {

        PriorityFusionEngine fusionEngine = new PriorityFusionEngine(context);

        int total = files.size();
        long startTime = System.currentTimeMillis();

        boolean isFirstTimeUser = isFirstTimeUser();

        Log.d(TAG, "Starting AI analysis for " + total + " files (First time user: " + isFirstTimeUser + ")");

        // 1. Filter out directories & Check Cache
        SharedPreferences cache = context.getSharedPreferences("priority_cache", Context.MODE_PRIVATE);
        List<FileModel> validFiles = new ArrayList<>();
        for (FileModel f : files) {
            if (f.isDirectory()) {
                f.setPriority(-1);
            } else {
                // Check if this EXACT file (by path and modified date) was already analyzed
                String cacheKey = f.getPath();
                String cachedVal = cacheKey != null ? cache.getString(cacheKey, null) : null;
                
                if (cachedVal != null) {
                    String[] parts = cachedVal.split(":");
                    if (parts.length == 3) {
                        try {
                            long cachedTime = Long.parseLong(parts[0]);
                            if (cachedTime == f.getLastModified()) {
                                // Cache Hit: Instant Priority!
                                f.setPriority(Integer.parseInt(parts[1]));
                                f.setAiPriorityLabel(parts[2]);
                                continue; // Skip ML pipeline completely
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                validFiles.add(f);
            }
        }

        // 2. Separate into fast-path (non-ML) and slow-path (ML)
        List<FileModel> fastPathFiles = new ArrayList<>();
        List<FileModel> imageFiles = new ArrayList<>();
        List<FileModel> videoFiles = new ArrayList<>();

        for (FileModel f : validFiles) {
            if (f.isImage()) imageFiles.add(f);
            else if (f.isVideo()) videoFiles.add(f);
            else fastPathFiles.add(f);
        }

        // 3. Analyze all files (No sampling limit!)
        // The user specifically requested all images be processed without an arbitrary cap.
        List<FileModel> slowPathFiles = new ArrayList<>();
        slowPathFiles.addAll(imageFiles);
        slowPathFiles.addAll(videoFiles);

        AtomicInteger processed = new AtomicInteger(0);

        // 4. Process Fast Path synchronously (microseconds)
        for (FileModel f : fastPathFiles) {
            processSingleFile(f, fusionEngine, false, isFirstTimeUser);
            processed.incrementAndGet();
        }

        // 5. Process Slow Path asynchronously using ThreadPool
        if (!slowPathFiles.isEmpty()) {
            int cores = Math.max(4, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(cores);
            CountDownLatch latch = new CountDownLatch(slowPathFiles.size());

            for (FileModel f : slowPathFiles) {
                executor.execute(() -> {
                    try {
                        processSingleFile(f, fusionEngine, true, isFirstTimeUser);
                        int p = processed.incrementAndGet();
                        if (p % 10 == 0) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            Log.d(TAG, "Processing: " + p + "/" + total
                                    + " (" + elapsed + "ms elapsed)");
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.w(TAG, "Analysis interrupted", e);
            } finally {
                executor.shutdown();
            }
        }

        // Release cached ML Kit detector resources
        imageAnalyzer.close();

        long totalTime = System.currentTimeMillis() - startTime;
        Log.d(TAG, "Priority assignment complete: " + processed.get() + " files analyzed in " + totalTime + "ms");
    }

    private void processSingleFile(FileModel file, PriorityFusionEngine fusionEngine, boolean fullAnalysis, boolean isFirstTimeUser) {
        AIAnalysisResult result = null;
        try {
            if (!fullAnalysis && file.isImage()) {
                // For sampled-out images, just use cold start heuristic to be fast
                int prio = coldStartPriority(file, null, isFirstTimeUser);
                file.setPriority(prio);
                String label = prio >= 70 ? "HIGH" : prio >= 40 ? "MEDIUM" : "LOW";
                file.setAiPriorityLabel(label);
                
                if (file.getPath() != null) {
                    SharedPreferences cache = context.getSharedPreferences("priority_cache", Context.MODE_PRIVATE);
                    cache.edit().putString(file.getPath(), file.getLastModified() + ":" + prio + ":" + label).apply();
                }
                return;
            }

            // Step 1: Extract metadata
            result = metadataExtractor.extractMetadata(file);

            // Step 2: Run type-specific analyzer
            runTypeSpecificAnalysis(file, result);

            // Step 3: Compute ML-based prediction score
            float mlScore = predictionEngine.predict(file, result, isFirstTimeUser);

            // Step 4: Fuse with behavior and user preferences
            int finalPriority = fusionEngine.calculateFinalPriority(file, result, mlScore, isFirstTimeUser);

            file.setPriority(finalPriority);

            // Set human-readable label
            String label = finalPriority >= 70 ? "HIGH" : finalPriority >= 40 ? "MEDIUM" : "LOW";
            file.setAiPriorityLabel(label);

            // Save final score to persistent cache
            if (file.getPath() != null) {
                SharedPreferences cache = context.getSharedPreferences("priority_cache", Context.MODE_PRIVATE);
                cache.edit().putString(file.getPath(), file.getLastModified() + ":" + finalPriority + ":" + label).apply();
            }

        } catch (Exception e) {
            Log.w(TAG, "Analysis failed for: " + file.getName(), e);
            // Fallback to cold-start heuristic
            int prio = coldStartPriority(file, result, isFirstTimeUser);
            file.setPriority(prio);
            String label = prio >= 70 ? "HIGH" : prio >= 40 ? "MEDIUM" : "LOW";
            file.setAiPriorityLabel(label);
            
            if (file.getPath() != null) {
                SharedPreferences cache = context.getSharedPreferences("priority_cache", Context.MODE_PRIVATE);
                cache.edit().putString(file.getPath(), file.getLastModified() + ":" + prio + ":" + label).apply();
            }
        }
    }

    /**
     * Route to the correct analyzer based on file type.
     */
    private void runTypeSpecificAnalysis(FileModel file, AIAnalysisResult result) {
        if (file.isImage()) {
            imageAnalyzer.analyze(file, result);
        } else if (file.isVideo()) {
            videoAnalyzer.analyze(file, result);
        } else if (file.isAudio()) {
            audioAnalyzer.analyze(file, result);
        } else if (file.isDocument()) {
            documentAnalyzer.analyze(file, result);
        }
        // For "other" files, metadata extraction alone is sufficient
    }

    /**
     * Fallback priority for when ML analysis fails or fast paths.
     */
    private int coldStartPriority(FileModel file, AIAnalysisResult result, boolean isFirstTimeUser) {
        // If it's a screenshot, hard cap it immediately
        if (ScreenshotDetector.isScreenshot(file, result)) {
            return 20; // LOW priority
        }

        float score = 0f;

        if (file.isImage()) {
            if (result != null && result.getFaceCount() > 0) score += 100f; // Ensure faces are maxed
            else score += 20f; // Base LOW for unanalyzed images
        }
        else if (file.isVideo()) score += 45f;
        else if (file.isDocument()) score += 60f;
        else if (file.isAudio()) score += 25f;

        score += Math.min(file.getAccessCount() * 2f, 15f);

        // Clamp minimum score bounds based on Fast Path rules
        if (file.isDocument()) {
            score = Math.max(score, 55f);
        }
        
        if (file.isImage()) {
            if (result != null && result.getFaceCount() > 0) {
                score = 100f; // Strongly prioritize faces
            } else {
                score = Math.min(score, 35f); // Random image must be strictly LOW (<= 35)
            }
        }

        return (int) Math.max(0f, Math.min(score, 100f));
    }

    public void incrementBackupCount() {
        int count = prefs.getInt(KEY_TOTAL_BACKUPS, 0);
        prefs.edit().putInt(KEY_TOTAL_BACKUPS, count + 1).apply();
    }

    public boolean isFirstTimeUser() {
        return prefs.getInt(KEY_TOTAL_BACKUPS, 0) == 0;
    }
}
