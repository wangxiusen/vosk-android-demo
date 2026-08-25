package com.huimei.voice.tts;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TtsVoiceCatalog {
    private static final List<TtsVoiceOption> CHINESE_VOICES = Collections.unmodifiableList(
            Arrays.asList(
                    new TtsVoiceOption("中文女声 · 北方", 4),
                    new TtsVoiceOption("中文女声 · 南方", 122)));
    private static final List<TtsVoiceOption> ENGLISH_VOICES = Collections.unmodifiableList(
            Arrays.asList(new TtsVoiceOption("美式女声 · LJSpeech", 0)));

    private TtsVoiceCatalog() {
    }

    public static List<TtsVoiceOption> voicesFor(TtsLanguage language) {
        return language == TtsLanguage.CHINESE ? CHINESE_VOICES : ENGLISH_VOICES;
    }
}
