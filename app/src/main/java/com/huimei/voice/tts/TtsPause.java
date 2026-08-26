package com.huimei.voice.tts;

public enum TtsPause {
    SHORT("短停顿 0.5×", 0.5f),
    NATURAL("标准停顿 1.0×", 1.0f),
    LONG("长停顿 1.5×", 1.5f);

    private final String displayName;
    private final float silenceScale;

    TtsPause(String displayName, float silenceScale) {
        this.displayName = displayName;
        this.silenceScale = silenceScale;
    }

    public String displayName() {
        return displayName;
    }

    public float silenceScale() {
        return silenceScale;
    }
}
