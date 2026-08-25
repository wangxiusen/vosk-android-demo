package com.huimei.voice.tts;

public final class TtsVoiceOption {
    private final String displayName;
    private final int speakerId;

    TtsVoiceOption(String displayName, int speakerId) {
        this.displayName = displayName;
        this.speakerId = speakerId;
    }

    public String displayName() {
        return displayName;
    }

    public int speakerId() {
        return speakerId;
    }
}
