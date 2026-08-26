package com.huimei.voice.tts;

public enum TtsVolume {
    STANDARD("标准音量", -20.0f),
    ENHANCED("增强音量", -18.0f),
    STRONG("强音量", -16.0f);

    private final String displayName;
    private final float targetRmsDbfs;

    TtsVolume(String displayName, float targetRmsDbfs) {
        this.displayName = displayName;
        this.targetRmsDbfs = targetRmsDbfs;
    }

    public String displayName() {
        return displayName;
    }

    public float targetRmsDbfs() {
        return targetRmsDbfs;
    }
}
