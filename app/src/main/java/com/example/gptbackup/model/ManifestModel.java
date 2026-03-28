package com.example.gptbackup.model;

import java.util.ArrayList;
import java.util.List;

public class ManifestModel {
    private List<ManifestFile> files;

    public ManifestModel() {
        this.files = new ArrayList<>();
    }

    public List<ManifestFile> getFiles() {
        if (files == null) files = new ArrayList<>();
        return files;
    }

    public void setFiles(List<ManifestFile> files) {
        this.files = files;
    }
}
