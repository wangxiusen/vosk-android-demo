package com.huimei.voice.model;

public enum VoiceLanguage {
    CHINESE("中文"),
    ENGLISH("English");

    private final String displayName;

    VoiceLanguage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
