package com.example.gptbackup.model;

/**
 * Represents the upload state of a single file.
 * Used by UI to render status + by UploadManager to control flow.
 */
public enum UploadState {
    WAITING,
    UPLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

