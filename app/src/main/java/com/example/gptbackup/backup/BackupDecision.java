package com.example.gptbackup.backup;

public enum BackupDecision {
    UPLOAD_NEW,
    REUPLOAD_MODIFIED,
    SKIP_UNCHANGED
}
