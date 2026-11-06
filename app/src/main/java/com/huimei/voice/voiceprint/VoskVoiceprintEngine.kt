package com.huimei.voice.voiceprint

import android.content.Context
import com.huimei.voice.voiceprint.VoiceprintMatcher.normalize
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import java.util.Arrays
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.math.min

class VoskVoiceprintEngine(context: Context?) : VoiceprintEngine {
    private val installer: VoiceprintModelInstaller
    private var recognitionModel: Model? = null
    private var speakerModel: SpeakerModel? = null

    init {
        installer = VoiceprintModelInstaller(context)
    }

    override fun type(): VoiceprintEngineType? {
        return VoiceprintEngineType.VOSK
    }

    override fun modelId(): String? {
        return type()!!.modelId()
    }

    override fun dimension(): Int {
        return EMBEDDING_DIMENSION
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(Exception::class)
    override fun initialize() {
        if (recognitionModel != null && speakerModel != null) {
            return
        }
        close()
        val recognitionDirectory = installer.installChineseRecognitionModel()
        val speakerDirectory = installer.installVoskSpeakerModel()
        var createdRecognition: Model? = null
        var createdSpeaker: SpeakerModel? = null
        try {
            createdRecognition = Model(recognitionDirectory.getAbsolutePath())
            createdSpeaker = SpeakerModel(speakerDirectory.getAbsolutePath())
            recognitionModel = createdRecognition
            speakerModel = createdSpeaker
        } catch (exception: Exception) {
            if (createdSpeaker != null) {
                createdSpeaker.close()
            }
            if (createdRecognition != null) {
                createdRecognition.close()
            }
            throw exception
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(Exception::class)
    override fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray {
        check(!(recognitionModel == null || speakerModel == null)) { "Vosk voiceprint engine is not initialized" }
        require(!(pcm16 == null || pcm16.size == 0)) { "PCM audio is required" }
        require(sampleRate == MODEL_SAMPLE_RATE) { "Vosk speaker model requires 16 kHz audio" }

        Recognizer(
            recognitionModel, MODEL_SAMPLE_RATE.toFloat(), speakerModel
        ).use { recognizer ->
            val results: MutableList<String> = ArrayList()
            var offset = 0
            while (offset < pcm16.size) {
                val end: Int = min(pcm16.size, offset + PCM_CHUNK_SAMPLES)
                val chunk = Arrays.copyOfRange(pcm16, offset, end)
                if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                    results.add(recognizer.getResult())
                }
                offset += PCM_CHUNK_SAMPLES
            }
            results.add(recognizer.getFinalResult())
            val embedding = VoskSpeakerVectorParser.parseBest(
                results, MINIMUM_SPEAKER_FRAMES
            )
            check(embedding.size == EMBEDDING_DIMENSION) { "Vosk returned an unexpected embedding dimension" }
            return normalize(embedding)
        }
    }

    @kotlin.jvm.Synchronized
    override fun close() {
        if (speakerModel != null) {
            speakerModel!!.close()
            speakerModel = null
        }
        if (recognitionModel != null) {
            recognitionModel!!.close()
            recognitionModel = null
        }
    }

    companion object {
        private const val MODEL_SAMPLE_RATE = 16000
        private const val EMBEDDING_DIMENSION = 128
        const val MINIMUM_SPEAKER_FRAMES: Int = 50
        private const val PCM_CHUNK_SAMPLES = 4096
    }
}
