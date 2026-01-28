package com.example.gptbackup.ai;

import com.example.gptbackup.model.FileModel;

public class FeatureExtractor {

    public static float[] extract(FileModel file) {

        float sizeMB = file.getSize() / (1024f * 1024f);

        float image = file.isImage() ? 1f : 0f;
        float video = file.isVideo() ? 1f : 0f;
        float audio = file.isAudio() ? 1f : 0f;
        float doc = file.isDocument() ? 1f : 0f;

        return new float[]{
                sizeMB,
                image,
                video,
                audio,
                doc
        };
    }
}
