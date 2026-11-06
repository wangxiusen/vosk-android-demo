package com.huimei.voice.voiceprint

interface VoiceprintEngine : AutoCloseable {
    fun type(): VoiceprintEngineType?

    fun modelId(): String?

    fun dimension(): Int

    @kotlin.Throws(Exception::class)
    fun initialize()

    @kotlin.Throws(Exception::class)
    fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray

    override fun close()
}
