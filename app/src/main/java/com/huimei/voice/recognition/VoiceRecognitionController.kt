package com.huimei.voice.recognition

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.huimei.voice.model.ListeningState
import com.huimei.voice.model.RecognitionAction
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.RecognitionResultParser.parseText
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoiceRecognitionController(context: Context, private val listener: VoiceRecognitionListener) :
    AutoCloseable {
    private val context: Context?
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeSource: TimeSource = TimeSource { SystemClock.elapsedRealtime() }

    private var language: VoiceLanguage = VoiceLanguage.CHINESE
    private var catalog = CommandCatalog.forLanguage(VoiceLanguage.CHINESE)
    private val stateMachine = WakeCommandStateMachine(catalog, timeSource)
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var generation: Long = 0
    var isRunning: Boolean = false
        private set
    private var promptPlaybackPaused = false
    private var closed = false

    private val countdownTask: Runnable = object : Runnable {
        override fun run() {
            if (closed || !this@VoiceRecognitionController.isRunning || promptPlaybackPaused) {
                return
            }
            val action = stateMachine.pollTimeout()
            if (action.type == RecognitionAction.Type.TIMED_OUT) {
                listener.onDiagnostic("唤醒窗口已超时")
                listener.onStatus("休眠监听中")
                listener.onWakeWindowChanged(false, 0L)
                return
            }
            if (stateMachine.state() == ListeningState.AWAKE) {
                listener.onWakeWindowChanged(true, stateMachine.remainingMillis())
                mainHandler.postDelayed(this, COUNTDOWN_INTERVAL_MILLIS)
            }
        }
    }

    init {
        this.context = context.getApplicationContext()
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    fun start(requestedLanguage: VoiceLanguage) {
        if (closed) {
            return
        }
        this.isRunning = true
        language = requestedLanguage
        catalog = CommandCatalog.forLanguage(requestedLanguage)
        stateMachine.reset(catalog)
        listener.onWakeWindowChanged(false, 0L)
        val requestGeneration = ++generation
        releaseRecognitionResources()
        listener.onStatus("模型加载中")
        listener.onDiagnostic("正在加载" + requestedLanguage.displayName + "模型")

        StorageService.unpack(
            context,
            catalog.assetModelPath(),
            "cache-" + catalog.assetModelPath(),
            StorageService.Callback { loadedModel: Model? ->
                onModelLoaded(
                    requestGeneration,
                    requestedLanguage,
                    loadedModel!!
                )
            },
            StorageService.Callback { error: IOException? ->
                onModelLoadFailed(
                    requestGeneration,
                    error!!
                )
            })
    }

    fun switchLanguage(requestedLanguage: VoiceLanguage) {
        listener.onDiagnostic("切换语言为 " + requestedLanguage.displayName)
        start(requestedLanguage)
    }

    private fun onModelLoaded(
        requestGeneration: Long,
        requestedLanguage: VoiceLanguage,
        loadedModel: Model
    ) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            loadedModel.close()
            return
        }
        model = loadedModel
        try {
            recognizer = Recognizer(model, SAMPLE_RATE, catalog.grammarJson())
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            if (!speechService!!.startListening(
                    createRecognitionListener(
                        requestGeneration,
                        requestedLanguage
                    )
                )
            ) {
                throw IOException("识别服务已经在运行")
            }
            listener.onStatus("休眠监听中")
            listener.onDiagnostic(requestedLanguage.displayName + "模型已加载，开始监听")
        } catch (error: IOException) {
            releaseRecognitionResources()
            this.isRunning = false
            listener.onStatus("启动失败")
            listener.onDiagnostic("无法启动离线识别：" + safeMessage(error))
        }
    }

    private fun onModelLoadFailed(requestGeneration: Long, error: IOException) {
        if (requestGeneration != generation || closed) {
            return
        }
        this.isRunning = false
        releaseRecognitionResources()
        listener.onStatus("模型加载失败")
        listener.onDiagnostic("模型加载失败：" + safeMessage(error))
    }

    private fun createRecognitionListener(
        requestGeneration: Long,
        requestedLanguage: VoiceLanguage?
    ): RecognitionListener {
        return object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                // Partial hypotheses are intentionally not consumed. Commands are
                // acted on only after Vosk has detected an utterance boundary.
            }

            override fun onResult(hypothesis: String?) {
                processResult(requestGeneration, requestedLanguage, hypothesis)
            }

            override fun onFinalResult(hypothesis: String?) {
                processResult(requestGeneration, requestedLanguage, hypothesis)
            }

            override fun onError(error: Exception) {
                if (!isCurrent(requestGeneration, requestedLanguage)) {
                    return
                }
                this@VoiceRecognitionController.isRunning = false
                releaseRecognitionResources()
                listener.onStatus("识别错误")
                listener.onDiagnostic("录音或识别错误：" + safeMessage(error))
            }

            override fun onTimeout() {
                if (isCurrent(requestGeneration, requestedLanguage)) {
                    listener.onDiagnostic("录音服务超时")
                }
            }
        }
    }

    private fun processResult(
        requestGeneration: Long,
        requestedLanguage: VoiceLanguage?,
        json: String?
    ) {
        if (!isCurrent(requestGeneration, requestedLanguage) || promptPlaybackPaused) {
            return
        }
        val text = parseText(json)
        if (!text.isPresent()) {
            return
        }
        val action = stateMachine.accept(text.get())
        when (action.type) {
            RecognitionAction.Type.WOKE_UP -> {
                listener.onStatus("已唤醒，请说命令")
                listener.onWakeUp(language, action)
                mainHandler.removeCallbacks(countdownTask)
                if (!promptPlaybackPaused) {
                    listener.onWakeWindowChanged(true, stateMachine.remainingMillis())
                    mainHandler.postDelayed(countdownTask, COUNTDOWN_INTERVAL_MILLIS)
                }
            }

            RecognitionAction.Type.COMMAND -> {
                mainHandler.removeCallbacks(countdownTask)
                listener.onCommand(language, action)
                listener.onWakeWindowChanged(false, 0L)
                if (!promptPlaybackPaused) {
                    listener.onStatus("休眠监听中")
                }
            }

            RecognitionAction.Type.TIMED_OUT -> {
                mainHandler.removeCallbacks(countdownTask)
                listener.onDiagnostic("唤醒窗口已超时")
                listener.onWakeWindowChanged(false, 0L)
                listener.onStatus("休眠监听中")
            }

            RecognitionAction.Type.NONE -> {}
            else -> {}
        }
    }

    fun pauseForPrompt(): Boolean {
        if (closed || !this.isRunning || speechService == null || promptPlaybackPaused) {
            return false
        }
        promptPlaybackPaused = true
        mainHandler.removeCallbacks(countdownTask)
        speechService!!.setPause(true)
        listener.onStatus("正在播放提示音")
        return true
    }

    fun resumeAfterPrompt() {
        if (!promptPlaybackPaused) {
            return
        }
        promptPlaybackPaused = false
        if (closed || !this.isRunning || speechService == null) {
            return
        }
        speechService!!.setPause(false)
        if (stateMachine.restartWakeWindow()) {
            listener.onStatus("已唤醒，请说命令")
            listener.onWakeWindowChanged(true, stateMachine.remainingMillis())
            mainHandler.postDelayed(countdownTask, COUNTDOWN_INTERVAL_MILLIS)
        } else {
            listener.onWakeWindowChanged(false, 0L)
            listener.onStatus("休眠监听中")
        }
    }

    fun stop() {
        if (closed) {
            return
        }
        this.isRunning = false
        generation++
        mainHandler.removeCallbacks(countdownTask)
        stateMachine.reset(catalog)
        releaseRecognitionResources()
        listener.onWakeWindowChanged(false, 0L)
        listener.onStatus("已停止")
        listener.onDiagnostic("已停止监听")
    }

    private fun isCurrent(requestGeneration: Long, requestedLanguage: VoiceLanguage?): Boolean {
        return !closed && this.isRunning
                && generation == requestGeneration && language == requestedLanguage
    }

    private fun releaseRecognitionResources() {
        mainHandler.removeCallbacks(countdownTask)
        promptPlaybackPaused = false
        if (speechService != null) {
            speechService!!.stop()
            speechService!!.shutdown()
            speechService = null
        }
        if (recognizer != null) {
            recognizer!!.close()
            recognizer = null
        }
        if (model != null) {
            model!!.close()
            model = null
        }
    }

    fun currentLanguage(): VoiceLanguage? {
        return language
    }

    fun currentWakePhrase(): String? {
        return catalog.wakePhrase()
    }

    fun currentModelPath(): String? {
        return catalog.assetModelPath()
    }

    override fun close() {
        if (closed) {
            return
        }
        this.isRunning = false
        closed = true
        generation++
        mainHandler.removeCallbacks(countdownTask)
        releaseRecognitionResources()
    }

    companion object {
        private const val SAMPLE_RATE = 16000.0f
        private const val COUNTDOWN_INTERVAL_MILLIS = 250L

        private fun safeMessage(error: Exception): String? {
            return if (error.message == null) error.javaClass.getSimpleName() else error.message
        }
    }
}
