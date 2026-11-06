package com.huimei.voice.tts

enum class TtsPause(private val displayName: String, private val silenceScale: Float) {
    SHORT("短停顿 0.5×", 0.5f),
    NATURAL("标准停顿 1.0×", 1.0f),
    LONG("长停顿 1.5×", 1.5f);

    fun displayName(): String = displayName
    fun silenceScale(): Float = silenceScale
}
