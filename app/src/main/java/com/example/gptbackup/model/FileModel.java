package com.example.gptbackup.model;

public class FileModel {

    private String name;
    private String path;
    private long size;
    private String type;
    private int priority; // 0=Low, 1=Medium, 2=High

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
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }
    public String getType() { return type; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
