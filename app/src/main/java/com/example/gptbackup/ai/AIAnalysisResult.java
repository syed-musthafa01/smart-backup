package com.example.gptbackup.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified container for multi-modal AI analysis results.
 * Populated by type-specific analyzers (Image, Video, Audio, Document).
 */
public class AIAnalysisResult {

    // ---- Image Analysis ----
    private List<String> labels = new ArrayList<>();
    private boolean textDetected = false;
    private int faceCount = 0;
    private float confidenceScore = 0f;

    // ---- Media Metadata ----
    private long mediaDuration = 0;       // milliseconds
    private String resolution = "";       // e.g. "1920x1080"
    private int pageCount = 0;            // for documents

    // ---- General ----
    private String fileCategory = "other"; // image, video, audio, document, other
    private boolean analysisComplete = false;
    private String extractedText = ""; // Raw OCR or document text

    // ================= Extracted Text =================
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }


    // ================= Labels =================
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public void addLabel(String label) { this.labels.add(label); }

    // ================= Text Detection =================
    public boolean isTextDetected() { return textDetected; }
    public void setTextDetected(boolean textDetected) { this.textDetected = textDetected; }

    // ================= Face Detection =================
    public int getFaceCount() { return faceCount; }
    public void setFaceCount(int faceCount) { this.faceCount = faceCount; }

    // ================= Confidence =================
    public float getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(float confidenceScore) { this.confidenceScore = confidenceScore; }

    // ================= Media Duration =================
    public long getMediaDuration() { return mediaDuration; }
    public void setMediaDuration(long mediaDuration) { this.mediaDuration = mediaDuration; }

    // ================= Resolution =================
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    // ================= Page Count =================
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    // ================= File Category =================
    public String getFileCategory() { return fileCategory; }
    public void setFileCategory(String fileCategory) { this.fileCategory = fileCategory; }

    // ================= Analysis Status =================
    public boolean isAnalysisComplete() { return analysisComplete; }
    public void setAnalysisComplete(boolean analysisComplete) { this.analysisComplete = analysisComplete; }

    @Override
    public String toString() {
        return "AIAnalysisResult{" +
                "labels=" + labels +
                ", textDetected=" + textDetected +
                ", faceCount=" + faceCount +
                ", confidence=" + confidenceScore +
                ", duration=" + mediaDuration +
                ", resolution='" + resolution + '\'' +
                ", pageCount=" + pageCount +
                ", category='" + fileCategory + '\'' +
                '}';
    }
}
