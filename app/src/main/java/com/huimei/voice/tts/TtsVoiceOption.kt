package com.huimei.voice.tts

class TtsVoiceOption(private val displayName: String, private val speakerId: Int) {
    fun displayName(): String = displayName
    fun speakerId(): Int = speakerId
}
