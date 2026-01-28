package com.example.gptbackup.model;

public enum UploadState {
    IDLE,
    WAITING,
    UPLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}
