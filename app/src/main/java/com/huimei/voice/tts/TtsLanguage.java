package com.huimei.voice.tts;

public enum TtsLanguage {
    CHINESE("潓美医疗提醒您，请根据设备说明安全使用氧气机。"),
    ENGLISH("Huimei Medical reminds you to use the oxygen concentrator safely.");

    private final String sampleText;

    TtsLanguage(String sampleText) {
        this.sampleText = sampleText;
    }

    public String sampleText() {
        return sampleText;
    }
}
