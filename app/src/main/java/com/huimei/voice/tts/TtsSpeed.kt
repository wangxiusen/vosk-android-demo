package com.huimei.voice.tts

enum class TtsSpeed(private val displayName: String, private val rate: Float) {
    SLOW("慢速 0.8×", 0.8f),
    NORMAL("正常 1.0×", 1.0f),
    FAST("快速 1.2×", 1.2f);

    fun displayName(): String = displayName
    fun rate(): Float = rate
}
