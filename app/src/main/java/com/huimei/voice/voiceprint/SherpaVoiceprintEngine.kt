package com.huimei.voice.voiceprint

import android.content.Context
import android.content.res.AssetManager
import com.huimei.voice.voiceprint.VoiceprintMatcher.normalize
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

class SherpaVoiceprintEngine(context: Context) : VoiceprintEngine {
    private val assets: AssetManager?
    private var extractor: SpeakerEmbeddingExtractor? = null

    init {
        requireNotNull(context) { "Context is required" }
        val applicationContext = context.getApplicationContext()
        assets = (if (applicationContext == null) context else applicationContext).getAssets()
    }

    override fun type(): VoiceprintEngineType? {
        return VoiceprintEngineType.SHERPA
    }

    override fun modelId(): String? {
        return type()!!.modelId()
    }

    @kotlin.jvm.Synchronized
    override fun dimension(): Int {
        return if (extractor == null) 0 else extractor!!.dim()
    }

    @kotlin.jvm.Synchronized
    override fun initialize() {
        if (extractor != null) {
            return
        }
        val config = SpeakerEmbeddingExtractorConfig(
            VoiceprintModelManifest.SHERPA_MODEL_ASSET,
            2,
            false,
            "cpu"
        )
        val created = SpeakerEmbeddingExtractor(assets, config)
        if (created.dim() <= 0) {
            created.release()
            throw IllegalStateException("Sherpa speaker model has an invalid dimension")
        }
        extractor = created
    }

    @kotlin.jvm.Synchronized
    override fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray {
        checkNotNull(extractor) { "Sherpa voiceprint engine is not initialized" }
        validateAudio(pcm16, sampleRate)
        val samples = FloatArray(pcm16.size)
        for (i in pcm16.indices) {
            samples[i] = pcm16[i] / 32768f
        }

        val stream = extractor!!.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            require(extractor!!.isReady(stream)) { "Audio is too short for a Sherpa speaker embedding" }
            val embedding = extractor!!.compute(stream)
            check(embedding.size == extractor!!.dim()) { "Sherpa returned an unexpected embedding dimension" }
            return normalize(embedding)
        } finally {
            stream.release()
        }
    }

    @kotlin.jvm.Synchronized
    override fun close() {
        if (extractor != null) {
            extractor!!.release()
            extractor = null
        }
    }

    companion object {
        private const val MODEL_SAMPLE_RATE = 16000

        private fun validateAudio(pcm16: ShortArray, sampleRate: Int) {
            require(!(pcm16 == null || pcm16.size == 0)) { "PCM audio is required" }
            require(sampleRate == MODEL_SAMPLE_RATE) { "Sherpa speaker model requires 16 kHz audio" }
        }
    }
}
