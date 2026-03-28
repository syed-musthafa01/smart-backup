package com.example.gptbackup.ai;

import android.util.Log;

import com.example.gptbackup.model.FileModel;

/**
 * Analyzes document files (PDF, DOCX, XLSX, PPTX, TXT, CSV):
 * - Estimates page count from file size
 * - Considers file recency and size for priority
 */
public class DocumentAnalyzer {

    private static final String TAG = "DocumentAnalyzer";

    /**
     * Analyze a document file.
     */
    public void analyze(FileModel file, AIAnalysisResult result) {
        Log.d("AI_ANALYSIS_START", "DocumentAnalyzer: " + file.getName());

        // Estimate page count based on file size and type
        int estimatedPages = estimatePageCount(file);
        result.setPageCount(estimatedPages);

        // Documents are generally important — compute confidence
        float confidence = 50f; // base confidence for documents

        // Recency boost removed

        // Size-based boost (larger documents tend to be more important)
        if (file.getSize() > 1024 * 1024) confidence += 15f;       // > 1MB
        else if (file.getSize() > 100 * 1024) confidence += 10f;   // > 100KB

        // Multi-page boost
        if (estimatedPages > 10) confidence += 10f;

        result.setConfidenceScore(Math.min(confidence, 100f));
        result.setTextDetected(true); // Documents inherently contain text
        result.setAnalysisComplete(true);

        Log.d("AI_DOCUMENT_RESULT", "File=" + file.getName()
                + " pages≈" + estimatedPages
                + " confidence=" + result.getConfidenceScore());
    }

    private int estimatePageCount(FileModel file) {
        String ext = file.getExtension().toLowerCase();
        long sizeKB = file.getSize() / 1024;

        switch (ext) {
            case "pdf":
                // Average PDF page ≈ 50-100 KB
                return Math.max(1, (int) (sizeKB / 75));
            case "docx":
            case "doc":
                // Average DOCX page ≈ 15-30 KB
                return Math.max(1, (int) (sizeKB / 20));
            case "xlsx":
            case "xls":
                // Spreadsheets: estimate sheets not pages
                return Math.max(1, (int) (sizeKB / 50));
            case "pptx":
            case "ppt":
                // Average slide ≈ 100-200 KB
                return Math.max(1, (int) (sizeKB / 150));
            case "txt":
            case "csv":
                // Plain text ≈ 3 KB per page
                return Math.max(1, (int) (sizeKB / 3));
            default:
                return 1;
        }
    }
}
