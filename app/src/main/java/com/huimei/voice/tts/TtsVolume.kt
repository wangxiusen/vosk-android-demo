package com.huimei.voice.tts

enum class TtsVolume(private val displayName: String, private val targetRmsDbfs: Float) {
    STANDARD("标准音量", -20.0f),
    ENHANCED("增强音量", -18.0f),
    STRONG("强音量", -16.0f);

    fun displayName(): String = displayName
    fun targetRmsDbfs(): Float = targetRmsDbfs
}
