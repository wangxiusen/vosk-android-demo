package com.huimei.voice.model

enum class VoiceLanguage(displayName: String) {
    CHINESE("中文"),
    ENGLISH("English");

    val displayName: String?

    init {
        this.displayName = displayName
    }
}
