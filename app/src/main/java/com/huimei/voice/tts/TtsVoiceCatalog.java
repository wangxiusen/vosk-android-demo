package com.huimei.voice.tts;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TtsVoiceCatalog {
    private static final List<TtsVoiceOption> CHINESE_VOICES = Collections.unmodifiableList(
            Arrays.asList(
                    new TtsVoiceOption("中文女声 01 · 北方（SSB0016）", 4),
                    new TtsVoiceOption("中文女声 02 · 南方（SSB1125）", 122),
                    new TtsVoiceOption("中文女声 03 · 北方（SSB0005）", 0),
                    new TtsVoiceOption("中文女声 04 · 南方（SSB0009）", 1),
                    new TtsVoiceOption("中文女声 05 · 北方（SSB0145）", 16),
                    new TtsVoiceOption("中文女声 06 · 南方（SSB0197）", 19),
                    new TtsVoiceOption("中文女声 07 · 北方（SSB0534）", 57),
                    new TtsVoiceOption("中文女声 08 · 南方（SSB0915）", 104),
                    new TtsVoiceOption("中文女声 09 · 北方（SSB1055）", 113),
                    new TtsVoiceOption("中文女声 10 · 南方（SSB1575）", 147)));
    private static final List<TtsVoiceOption> ENGLISH_VOICES = Collections.unmodifiableList(
            Arrays.asList(new TtsVoiceOption("美式女声 · LJSpeech", 0)));

    private TtsVoiceCatalog() {
    }

    public static List<TtsVoiceOption> voicesFor(TtsLanguage language) {
        return language == TtsLanguage.CHINESE ? CHINESE_VOICES : ENGLISH_VOICES;
    }
}
