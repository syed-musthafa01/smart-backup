package com.example.gptbackup.model;

public class FileModel {

    // ========== BASIC FILE INFO ==========
    private String name;
    private String path;
    private long size;
    private String type; // image / video / audio / document / other
    private boolean isDirectory;

    // ========== LOCAL FILE METADATA ==========
    private long lastModified;
    private int accessCount;

    // ========== UI STATE ==========
    private boolean selected = true;
    private int priority = -1;

    // ========== UPLOAD STATE ==========
    private UploadState uploadState = UploadState.IDLE;
    private int uploadProgress = 0;

    // ========== BACKUP INTELLIGENCE ==========
    private String driveFileId;      // null = never uploaded
    private long lastBackupTime;     // millis
    private long backedUpFileSize;   // bytes

    // ========== CONSTRUCTOR ==========
    public FileModel(String name, String path, long size, String type) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.type = type;
    }

    // ========== BASIC GETTERS ==========
    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // ========== DIRECTORY ==========
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    // ========== TYPE HELPERS ==========
    public boolean isImage() { return "image".equals(type); }
    public boolean isVideo() { return "video".equals(type); }
    public boolean isAudio() { return "audio".equals(type); }
    public boolean isDocument() { return "document".equals(type); }

    public String getExtension() {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // ========== TIME & USAGE ==========
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

    // ========== SELECTION ==========
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    // ========== PRIORITY ==========
    public int getPriority() { return priority; }
    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ========== UPLOAD STATE ==========
    public UploadState getUploadState() { return uploadState; }
    public void setUploadState(UploadState uploadState) {
        this.uploadState = uploadState;
    }

    public int getUploadProgress() { return uploadProgress; }
    public void setUploadProgress(int uploadProgress) {
        this.uploadProgress = uploadProgress;
    }

    // ========== BACKUP INTELLIGENCE ==========
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
}
