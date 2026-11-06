package com.huimei.voice.tts

enum class TtsLanguage(private val sampleText: String) {
    CHINESE("潓美医疗提醒您，请根据设备说明安全使用氧气机。"),
    ENGLISH("Huimei Medical reminds you to use the oxygen concentrator safely.");

    fun sampleText(): String = sampleText
}
