package com.huimei.voice.tts

class TtsVoiceCatalog private constructor() {
    companion object {
        private val chineseVoices = listOf(
            TtsVoiceOption("中文女声 01 · 北方（SSB0016）", 4),
            TtsVoiceOption("中文女声 02 · 南方（SSB1125）", 122),
            TtsVoiceOption("中文女声 03 · 北方（SSB0005）", 0),
            TtsVoiceOption("中文女声 04 · 南方（SSB0009）", 1),
            TtsVoiceOption("中文女声 05 · 北方（SSB0145）", 16),
            TtsVoiceOption("中文女声 06 · 南方（SSB0197）", 19),
            TtsVoiceOption("中文女声 07 · 北方（SSB0534）", 57),
            TtsVoiceOption("中文女声 08 · 南方（SSB0915）", 104),
            TtsVoiceOption("中文女声 09 · 北方（SSB1055）", 113),
            TtsVoiceOption("中文女声 10 · 南方（SSB1575）", 147)
        )
        private val englishVoices = listOf(TtsVoiceOption("美式女声 · LJSpeech", 0))

        @JvmStatic
        fun voicesFor(language: TtsLanguage): List<TtsVoiceOption> =
            if (language == TtsLanguage.CHINESE) chineseVoices else englishVoices
    }
}
