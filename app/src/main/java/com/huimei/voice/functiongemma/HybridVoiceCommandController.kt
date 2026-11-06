package com.huimei.voice.functiongemma

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.CommandCatalog
import com.huimei.voice.recognition.RecognitionResultParser
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class HybridVoiceCommandController(
    context: Context,
    private val listener: HybridVoiceCommandListener
) : AutoCloseable {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = HybridVoiceSession { SystemClock.elapsedRealtime() }

    private var language = VoiceLanguage.CHINESE
    private var catalog = CommandCatalog.forLanguage(language)
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var generation = 0L
    private var desiredRunning = false
    private var closed = false

    private val countdownTask = object : Runnable {
        override fun run() {
            if (closed || !desiredRunning || session.state() != HybridVoiceState.DICTATING) return
            val result = session.pollTimeout()
            if (result.action() == HybridVoiceSession.Action.TIMED_OUT) {
                listener.onWakeTimeout(language)
                listener.onDiagnostic("唤醒窗口已超时")
                startSleepingRecognizer(generation, language)
                return
            }
            listener.onWake(language, session.remainingMillis())
            mainHandler.postDelayed(this, COUNTDOWN_INTERVAL_MILLIS)
        }
    }

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    fun start(requestedLanguage: VoiceLanguage) = runOnMain { startInternal(requestedLanguage) }

    fun switchLanguage(requestedLanguage: VoiceLanguage) = runOnMain {
        listener.onDiagnostic("切换语言为 ${requestedLanguage.displayName}")
        startInternal(requestedLanguage)
    }

    fun completeInference(utteranceId: Long) = runOnMain {
        if (session.completeInference(utteranceId) && isCurrent(generation, language)) {
            startSleepingRecognizer(generation, language)
        }
    }

    fun stop() = runOnMain { stopInternal() }

    override fun close() = runOnMain {
        if (closed) return@runOnMain
        desiredRunning = false
        closed = true
        generation++
        session.close()
        releaseAllResources()
    }

    private fun startInternal(requestedLanguage: VoiceLanguage) {
        if (closed) return
        language = requestedLanguage
        catalog = CommandCatalog.forLanguage(language)
        desiredRunning = true
        val requestGeneration = ++generation
        session.start(language)
        releaseAllResources()
        listener.onStateChanged(HybridVoiceState.SLEEPING, "模型加载中")
        listener.onDiagnostic("正在加载${language.displayName} Vosk 模型")

        val modelPath = requireNotNull(catalog.assetModelPath())
        StorageService.unpack(
            context,
            modelPath,
            "cache-$modelPath",
            { loadedModel -> mainHandler.post { onModelLoaded(requestGeneration, requestedLanguage, loadedModel) } },
            { error -> mainHandler.post { onModelLoadFailed(requestGeneration, requestedLanguage, error) } }
        )
    }

    private fun onModelLoaded(requestGeneration: Long, requestedLanguage: VoiceLanguage, loadedModel: Model) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            loadedModel.close()
            return
        }
        model = loadedModel
        startSleepingRecognizer(requestGeneration, requestedLanguage)
    }

    private fun onModelLoadFailed(requestGeneration: Long, requestedLanguage: VoiceLanguage, error: IOException) {
        if (isCurrent(requestGeneration, requestedLanguage)) fail("模型加载失败：${safeMessage(error)}")
    }

    private fun startSleepingRecognizer(requestGeneration: Long, requestedLanguage: VoiceLanguage) {
        if (!isCurrent(requestGeneration, requestedLanguage)) return
        val currentModel = model ?: return
        releaseAudioResources()
        try {
            recognizer = Recognizer(currentModel, SAMPLE_RATE, catalog.grammarJson())
            startSpeechService(requestGeneration, requestedLanguage)
            listener.onStateChanged(HybridVoiceState.SLEEPING, "休眠监听中")
        } catch (error: IOException) {
            fail("无法启动离线识别：${safeMessage(error)}")
        }
    }

    private fun startDictationRecognizer(requestGeneration: Long, requestedLanguage: VoiceLanguage) {
        if (!isCurrent(requestGeneration, requestedLanguage)) return
        val currentModel = model ?: return
        releaseAudioResources()
        try {
            recognizer = Recognizer(currentModel, SAMPLE_RATE)
            startSpeechService(requestGeneration, requestedLanguage)
            listener.onStateChanged(HybridVoiceState.DICTATING, "已唤醒，请说指令")
            listener.onWake(language, session.remainingMillis())
            mainHandler.postDelayed(countdownTask, COUNTDOWN_INTERVAL_MILLIS)
        } catch (error: IOException) {
            fail("无法启动自由听写：${safeMessage(error)}")
        }
    }

    @Throws(IOException::class)
    private fun startSpeechService(requestGeneration: Long, requestedLanguage: VoiceLanguage) {
        speechService = SpeechService(recognizer, SAMPLE_RATE)
        if (speechService?.startListening(createRecognitionListener(requestGeneration, requestedLanguage)) != true) {
            throw IOException("识别服务已经在运行")
        }
    }

    private fun createRecognitionListener(requestGeneration: Long, requestedLanguage: VoiceLanguage) =
        object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                // Only final utterances are commands.
            }

            override fun onResult(hypothesis: String) {
                mainHandler.post { processResult(requestGeneration, requestedLanguage, hypothesis) }
            }

            override fun onFinalResult(hypothesis: String) {
                mainHandler.post { processResult(requestGeneration, requestedLanguage, hypothesis) }
            }

            override fun onError(error: Exception) {
                mainHandler.post {
                    if (isCurrent(requestGeneration, requestedLanguage)) {
                        fail("录音或识别错误：${safeMessage(error)}")
                    }
                }
            }

            override fun onTimeout() {
                mainHandler.post {
                    if (isCurrent(requestGeneration, requestedLanguage)) listener.onDiagnostic("录音服务超时")
                }
            }
        }

    private fun processResult(requestGeneration: Long, requestedLanguage: VoiceLanguage, json: String) {
        if (!isCurrent(requestGeneration, requestedLanguage)) return
        val text = RecognitionResultParser.parseText(json)
        if (!text.isPresent) return
        val result = session.accept(text.get())
        when (result.action()) {
            HybridVoiceSession.Action.WOKE -> {
                mainHandler.removeCallbacks(countdownTask)
                startDictationRecognizer(requestGeneration, requestedLanguage)
            }
            HybridVoiceSession.Action.COMMAND -> {
                mainHandler.removeCallbacks(countdownTask)
                releaseAudioResources()
                listener.onStateChanged(HybridVoiceState.INFERENCING, "指令理解中")
                listener.onTranscript(requestedLanguage, result.transcript(), result.utteranceId())
            }
            HybridVoiceSession.Action.TIMED_OUT -> {
                mainHandler.removeCallbacks(countdownTask)
                listener.onWakeTimeout(requestedLanguage)
                startSleepingRecognizer(requestGeneration, requestedLanguage)
            }
            HybridVoiceSession.Action.NONE -> Unit
        }
    }

    private fun stopInternal() {
        if (closed) return
        desiredRunning = false
        generation++
        session.stop()
        releaseAllResources()
        listener.onStateChanged(HybridVoiceState.STOPPED, "已停止")
        listener.onDiagnostic("已停止监听")
    }

    private fun fail(message: String) {
        desiredRunning = false
        generation++
        session.stop()
        releaseAllResources()
        listener.onStateChanged(HybridVoiceState.STOPPED, "识别失败")
        listener.onError(message)
    }

    private fun isCurrent(requestGeneration: Long, requestedLanguage: VoiceLanguage): Boolean =
        !closed && desiredRunning && generation == requestGeneration && language == requestedLanguage

    private fun releaseAudioResources() {
        mainHandler.removeCallbacks(countdownTask)
        speechService?.let {
            it.stop()
            it.shutdown()
            speechService = null
        }
        recognizer?.close()
        recognizer = null
    }

    private fun releaseAllResources() {
        releaseAudioResources()
        model?.close()
        model = null
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000.0f
        private const val COUNTDOWN_INTERVAL_MILLIS = 250L

        private fun safeMessage(error: Exception): String = error.message ?: error.javaClass.simpleName
    }
}
