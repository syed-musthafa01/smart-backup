package com.example.gptbackup.backup;

import com.example.gptbackup.db.BackupFileEntity;
import com.example.gptbackup.model.FileModel;

public class BackupDecisionEngine {

    public static BackupDecision decide(FileModel file,
                                        BackupFileEntity record) {

        if (record == null || record.driveFileId == null) {
            return BackupDecision.UPLOAD_NEW;
        }

        if (file.getLastModified() > record.lastUploadedTime) {
            return BackupDecision.REUPLOAD_MODIFIED;
        }

        if (file.getSize() != record.uploadedFileSize) {
            return BackupDecision.REUPLOAD_MODIFIED;
        }

        return BackupDecision.SKIP_UNCHANGED;
    }
}
