package com.example.gptbackup.model;

public class FileModel {

    private String name;
    private String path;
    private long size;

    // logical category: image / video / audio / document / other
    private String type;

    private boolean isDirectory;
    private long lastModified;
    private int accessCount;

    private int priority = -1;

    private UploadState uploadState = UploadState.IDLE;
    private int uploadProgress = 0;

    // ---------------- CONSTRUCTOR ----------------
    public FileModel(String name, String path, long size, String type) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.type = type;
    }

    // ---------------- BASIC GETTERS ----------------
    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }

    // 🔴 FIX for DriveRestUploader
    public String getType() { return type; }

    public void setCategory(String type) { this.type = type; }

    // ---------------- DIRECTORY ----------------
    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }

    // ---------------- FILE TYPE HELPERS ----------------
    public boolean isImage() { return "image".equals(type); }
    public boolean isVideo() { return "video".equals(type); }

    // 🔴 FIX for FeatureExtractor
    public boolean isAudio() { return "audio".equals(type); }

    public boolean isDocument() { return "document".equals(type); }

    // 🔴 FIX for FileAdapter
    public String getExtension() {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1);
    }

    // ---------------- TIME & USAGE ----------------
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public int getLastModifiedDays() {
        long diff = System.currentTimeMillis() - lastModified;
        return (int) (diff / (1000L * 60 * 60 * 24));
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void incrementAccessCount() {
        accessCount++;
    }

    // ---------------- PRIORITY ----------------
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    // ---------------- UPLOAD STATE ----------------
    public UploadState getUploadState() { return uploadState; }
    public void setUploadState(UploadState uploadState) {
        this.uploadState = uploadState;
    }

    public int getUploadProgress() { return uploadProgress; }
    public void setUploadProgress(int uploadProgress) {
        this.uploadProgress = uploadProgress;
    }
}
