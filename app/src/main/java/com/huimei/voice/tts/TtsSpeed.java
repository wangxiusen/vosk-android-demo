package com.huimei.voice.tts;

public enum TtsSpeed {
    SLOW("慢速 0.8×", 0.8f),
    NORMAL("正常 1.0×", 1.0f),
    FAST("快速 1.2×", 1.2f);

    private final String displayName;
    private final float rate;

    TtsSpeed(String displayName, float rate) {
        this.displayName = displayName;
        this.rate = rate;
    }

    public String displayName() {
        return displayName;
    }

    public float rate() {
        return rate;
    }
}
