package com.example.gptbackup.ai;


import com.example.gptbackup.model.FileModel;

public class FeatureExtractor {

    public static float[] extract(FileModel file) {

        float sizeMB = file.getSize() / (1024f * 1024f);

        float typeImage = file.getType().equals("image") ? 1f : 0f;
        float typeVideo = file.getType().equals("video") ? 1f : 0f;
        float typeAudio = file.getType().equals("audio") ? 1f : 0f;
        float typeDoc = file.getType().equals("document") ? 1f : 0f;

        return new float[]{
                sizeMB,
                typeImage,
                typeVideo,
                typeAudio,
                typeDoc
        };
    }
}
