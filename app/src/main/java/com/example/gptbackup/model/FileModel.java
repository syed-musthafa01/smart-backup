package com.example.gptbackup.model;

public class FileModel {

    private String name;
    private String path;
    private long size;
    private String type;
    private int priority; // 0=Low, 1=Medium, 2=High
    private long lastModified;
    private boolean isDirectory;

    // Category helps us route to a specific Drive folder (Images, Videos, Documents, Audio, AI_Priority)
    private String category;

    // Upload UI state
    private UploadState uploadState = UploadState.WAITING;
    private int uploadProgress = 0; // 0-100

    private boolean isBackedUp = false;

    public boolean isBackedUp() {
        return isBackedUp;
    }

    public void setBackedUp(boolean backedUp) {
        isBackedUp = backedUp;
    }
    public FileModel(String name, String path, long size, String type) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.type = type;
        this.priority = -1; // not assigned yet
        this.category = type;
        this.lastModified = 0L;
        this.isDirectory = false;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }
    public String getType() { return type; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public UploadState getUploadState() {
        return uploadState;
    }

    public void setUploadState(UploadState uploadState) {
        this.uploadState = uploadState;
    }

    public int getUploadProgress() {
        return uploadProgress;
    }

    public void setUploadProgress(int uploadProgress) {
        this.uploadProgress = uploadProgress;
    }
}
