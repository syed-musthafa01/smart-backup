package com.example.gptbackup.model;

import android.net.Uri;

import com.example.gptbackup.backup.DriveRestUploader;

import java.util.concurrent.atomic.AtomicBoolean;

public class FileModel {

    private String name;
    private String path;
    private Uri contentUri;
    private boolean fromMediaStore = false;
    private long size;
    private String type;
    private boolean isDirectory;

    private long lastModified;
    private int accessCount;

    private boolean selected = false; // ✅ Changed to false as requested
    private int priority = -1;

    private UploadState uploadState = UploadState.IDLE;
    private int uploadProgress = 0;

    private String driveFileId;
    private long lastBackupTime;
    private long backedUpFileSize;

    public FileModel(String name, String path, long size, String type) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.type = type;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public boolean isImage() {
        return "image".equals(type);
    }

    public boolean isVideo() {
        return "video".equals(type);
    }

    public boolean isAudio() {
        return "audio".equals(type)
                || hasExtension("mp3", "wav", "aac", "m4a", "ogg", "flac");
    }

    public boolean isDocument() {
        return "document".equals(type)
                && hasExtension(
                "pdf", "doc", "docx",
                "xls", "xlsx",
                "ppt", "pptx",
                "txt", "csv"
        );
    }

    private boolean hasExtension(String... exts) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String ext : exts) {
            if (lower.endsWith("." + ext)) return true;
        }
        return false;
    }

    public String getExtension() {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public int getLastModifiedDays() {
        long diff = System.currentTimeMillis() - lastModified;
        return (int) (diff / (1000L * 60 * 60 * 24));
    }

    public int getAccessCount() { return accessCount; }
    public void incrementAccessCount() { accessCount++; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getPriority() { return priority; }
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public UploadState getUploadState() { return uploadState; }
    public void setUploadState(UploadState uploadState) {
        this.uploadState = uploadState;
    }

    public int getUploadProgress() { return uploadProgress; }
    public void setUploadProgress(int uploadProgress) {
        this.uploadProgress = uploadProgress;
    }

    public String getDriveFileId() { return driveFileId; }
    public void setDriveFileId(String driveFileId) {
        this.driveFileId = driveFileId;
    }

    public long getLastBackupTime() { return lastBackupTime; }
    public void setLastBackupTime(long lastBackupTime) {
        this.lastBackupTime = lastBackupTime;
    }

    public long getBackedUpFileSize() { return backedUpFileSize; }
    public void setBackedUpFileSize(long backedUpFileSize) {
        this.backedUpFileSize = backedUpFileSize;
    }

    public boolean isAppFile() {
        if (path == null) return false;

        String lowerPath = path.toLowerCase();

        if (lowerPath.endsWith(".apk")) return true;

        return lowerPath.contains("/android/data/")
                || lowerPath.contains("/android/obb/")
                || lowerPath.contains("/android/media/");
    }

    // ================= Upload control flags =================

    private boolean uploading = false;

    private boolean canceledByUser = false;

    // ✅ NEW: real upload handle (network-level control)
    private DriveRestUploader.UploadHandle uploadHandle;
    private final AtomicBoolean pausedByUser = new AtomicBoolean(false);

    // ---- Getters ----
    public boolean isUploading() {
        return uploading;
    }

    public boolean isPausedByUser() {
        return pausedByUser.get();
    }
    public boolean isCanceledByUser() {
        return canceledByUser;
    }

    public AtomicBoolean getPausedFlag() {
        return pausedByUser;
    }
    public Uri getContentUri() { return contentUri; }
    public void setContentUri(Uri contentUri) {
        this.contentUri = contentUri;
    }

    public boolean isFromMediaStore() {
        return fromMediaStore;
    }
    public void setFromMediaStore(boolean fromMediaStore) {
        this.fromMediaStore = fromMediaStore;
    }
    // ---- Setters / Controls ----

    public void setUploading(boolean uploading) {
        this.uploading = uploading;
        if (uploading) {
            this.canceledByUser = false;
            //this.pausedByUser = false;
            pausedByUser.set(false);
        }
    }
    public void pauseByUser() {
        pausedByUser.set(true);
        uploadState = UploadState.PAUSED;
    }

    public void resumeByUser() {
        pausedByUser.set(false);
        uploadState = UploadState.UPLOADING;
    }

    public void cancelByUser() {
        this.canceledByUser = true;
        this.uploadState = UploadState.CANCELED;

        // 🔴 HARD CANCEL (stops upload immediately)
        if (uploadHandle != null) {
            uploadHandle.cancel();
        }
    }

    // ---- Upload handle wiring ----
    public void setUploadHandle(DriveRestUploader.UploadHandle uploadHandle) {
        this.uploadHandle = uploadHandle;
    }

}
