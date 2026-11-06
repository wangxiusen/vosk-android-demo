package com.huimei.voice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.huimei.voice.avatar.AudioEnergyMouthMapper.shapeFor
import com.huimei.voice.avatar.MouthShape
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Boolean
import kotlin.FloatArray
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Throwable
import kotlin.math.max
import kotlin.math.min

class OfflineTtsPlayer(context: Context, private val listener: Listener) : AutoCloseable {
    interface Listener {
        fun onReady(numberOfSpeakers: Int)

        fun onStarted()

        fun onMouthShape(shape: MouthShape?)

        fun onCompleted()

        fun onStopped()

        fun onError(message: String?)
    }

    private val context: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modelWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val playbackWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean()
    private val playbackGeneration = AtomicInteger()
    private val modelGeneration = AtomicInteger()

    @kotlin.concurrent.Volatile
    private var tts: OfflineTts? = null

    @kotlin.concurrent.Volatile
    private var loadedLanguage: TtsLanguage? = null

    @kotlin.concurrent.Volatile
    private var activeSession: PlaybackSession? = null

    @kotlin.concurrent.Volatile
    private var audioTrack: AudioTrack? = null

    init {
        this.context = context.getApplicationContext()
    }

    @kotlin.jvm.JvmOverloads
    fun initialize(language: TtsLanguage? = TtsLanguage.CHINESE) {
        loadLanguage(language)
    }

    fun loadLanguage(language: TtsLanguage?) {
        if (closed.get() || language == null) {
            return
        }
        val loadGeneration = modelGeneration.incrementAndGet()
        cancelPlayback(false)
        loadedLanguage = null
        val requestedAtMillis = SystemClock.elapsedRealtime()
        Log.i(
            PERFORMANCE_TAG,
            ("model_load_request generation=" + loadGeneration
                    + " language=" + language.name)
        )
        modelWorker.execute(Runnable {
            loadModel(
                loadGeneration,
                language,
                requestedAtMillis
            )
        })
    }

    fun speak(
        text: String?,
        voice: TtsVoiceOption,
        speed: TtsSpeed,
        pause: TtsPause,
        volume: TtsVolume
    ): Boolean {
        val currentTts = tts
        val currentLanguage = loadedLanguage
        if (closed.get()
            || currentTts == null || currentLanguage == null || text == null || text.trim { it <= ' ' }
                .isEmpty()
        ) {
            return false
        }

        cancelPlayback(false)
        val generation = playbackGeneration.incrementAndGet()
        val normalizedText: String = text.trim { it <= ' ' }
        val session = PlaybackSession(
            generation,
            normalizedText,
            voice.speakerId(),
            speed.rate(),
            pause.silenceScale(),
            volume.targetRmsDbfs(),
            currentTts.sampleRate(),
            SystemClock.elapsedRealtime()
        )
        activeSession = session

        Log.i(
            PERFORMANCE_TAG,
            ("request generation=" + generation
                    + " language=" + currentLanguage.name
                    + " chars=" + normalizedText.length
                    + " punctuation=" + countPunctuation(normalizedText)
                    + " speaker=" + voice.speakerId()
                    + " speed=" + speed.rate()
                    + " silence_scale=" + pause.silenceScale()
                    + " target_rms_dbfs=" + volume.targetRmsDbfs())
        )
        modelWorker.execute(Runnable { synthesize(session, currentTts) })
        playbackWorker.execute(Runnable { play(session) })
        return true
    }

    fun stop() {
        cancelPlayback(true)
    }

    private fun loadModel(
        generation: Int,
        language: TtsLanguage,
        requestedAtMillis: Long
    ) {
        if (!isCurrentModelLoad(generation)) {
            return
        }
        Log.i(
            PERFORMANCE_TAG,
            ("model_load_start generation=" + generation
                    + " language=" + language.name)
        )
        var created: OfflineTts? = null
        try {
            val previous = tts
            tts = null
            if (previous != null) {
                previous.release()
            }

            var dataDirectory: String? = ""
            if (language == TtsLanguage.ENGLISH) {
                dataDirectory = AssetDirectoryCopier.copyOnce(
                    context,
                    LightweightTtsConfigFactory.ENGLISH_DATA_DIRECTORY
                )
            }
            created = OfflineTts(
                context.getAssets(),
                LightweightTtsConfigFactory.create(language, dataDirectory)
            )
            if (!isCurrentModelLoad(generation)) {
                created.release()
                return
            }
            tts = created
            loadedLanguage = language
            Log.i(
                PERFORMANCE_TAG,
                ("model_load_end generation=" + generation
                        + " language=" + language.name
                        + " load_ms=" + elapsedSince(requestedAtMillis)
                        + " sample_rate=" + created.sampleRate()
                        + " speakers=" + created.numSpeakers())
            )
            val readyModel: OfflineTts? = created
            post(Runnable {
                if (isCurrentModelLoad(generation)) {
                    listener.onReady(readyModel!!.numSpeakers())
                }
            })
        } catch (error: Throwable) {
            if (created != null && created != tts) {
                created.release()
            }
            if (isCurrentModelLoad(generation)) {
                postError("TTS 模型初始化失败：" + safeMessage(error))
            }
        }
    }

    private fun synthesize(session: PlaybackSession, currentTts: OfflineTts) {
        if (!isCurrent(session)) {
            return
        }
        Log.i(
            PERFORMANCE_TAG,
            ("worker_start generation=" + session.generation
                    + " after_request_ms=" + elapsedSince(session.requestedAtMillis))
        )
        try {
            val config = GenerationConfig()
            config.sid = session.speakerId
            config.speed = session.speed
            config.silenceScale = session.silenceScale

            Log.i(
                PERFORMANCE_TAG,
                ("generate_start generation=" + session.generation
                        + " after_request_ms="
                        + elapsedSince(session.requestedAtMillis))
            )
            val generated = currentTts.generateWithConfigAndCallback(
                session.text,
                config,
                StreamingCallback(session)
            )
            val generatedSamples = generated.samples
            val generationMillis: Long = elapsedSince(session.requestedAtMillis)
            val audioMillis: Long = samplesToMillis(
                generatedSamples.size,
                session.sampleRate
            )
            Log.i(
                PERFORMANCE_TAG,
                ("generate_end generation=" + session.generation
                        + " after_request_ms=" + generationMillis
                        + " audio_ms=" + audioMillis
                        + " rtf=" + formatRtf(generationMillis, audioMillis)
                        + " longest_quiet_ms=" + longestQuietRunMillis(
                    generatedSamples,
                    session.samplesPerFrame
                ))
            )
            if (isCurrent(session) && session.enqueuedSamples.get() == 0) {
                session.fail("没有生成可播放的音频")
            }
        } catch (error: Throwable) {
            if (isCurrent(session)) {
                session.fail(safeMessage(error))
            }
        } finally {
            session.queue.finish()
        }
    }

    private fun play(session: PlaybackSession) {
        var track: AudioTrack? = null
        try {
            var chunkIndex = 0
            while (isCurrent(session)) {
                val waitStartedAtMillis = SystemClock.elapsedRealtime()
                val samples = session.queue.take()
                val waitMillis: Long = elapsedSince(waitStartedAtMillis)
                if (samples == null) {
                    return
                }
                if (samples == PcmChunkQueue.END_OF_STREAM) {
                    break
                }
                chunkIndex++
                val analysisStartedAtMillis = SystemClock.elapsedRealtime()
                val loudness =
                    PcmLoudnessNormalizer.analyze(samples, session.targetRmsDbfs)
                val analysisMillis: Long = elapsedSince(analysisStartedAtMillis)
                val endGain = loudness.gain()
                val startGain = if (session.previousGain.isNaN())
                    endGain
                else min(session.previousGain, endGain)
                Log.i(
                    PERFORMANCE_TAG,
                    ("playback_chunk generation=" + session.generation
                            + " index=" + chunkIndex
                            + " queue_wait_ms=" + waitMillis
                            + " audio_ms=" + samplesToMillis(
                        samples.size,
                        session.sampleRate
                    ))
                )
                Log.i(
                    PERFORMANCE_TAG,
                    ("loudness generation=" + session.generation
                            + " index=" + chunkIndex
                            + " analysis_ms=" + analysisMillis
                            + " active=" + loudness.hasActiveAudio()
                            + " input_active_rms_dbfs="
                            + formatDb(loudness.inputActiveRmsDbfs())
                            + " input_peak_dbfs="
                            + formatDb(loudness.inputPeakDbfs())
                            + " target_rms_dbfs="
                            + formatDb(session.targetRmsDbfs)
                            + " gain_db=" + formatDb(loudness.gainDb())
                            + " peak_limited=" + loudness.isPeakLimited()
                            + " output_active_rms_dbfs="
                            + formatDb(loudness.outputActiveRmsDbfs())
                            + " output_peak_dbfs="
                            + formatDb(loudness.outputPeakDbfs()))
                )
                if (track == null) {
                    track = createAudioTrack(session.sampleRate)
                    audioTrack = track
                    val initialLength: Int = min(
                        session.samplesPerFrame,
                        samples.size
                    )
                    writeFrame(
                        session,
                        track,
                        samples,
                        0,
                        initialLength,
                        startGain,
                        endGain
                    )
                    if (!isCurrent(session)) {
                        return
                    }
                    track.play()
                    Log.i(
                        PERFORMANCE_TAG,
                        ("playback_started generation=" + session.generation
                                + " after_request_ms="
                                + elapsedSince(session.requestedAtMillis))
                    )
                    postIfCurrent(session, Runnable { listener.onStarted() })
                    streamSamples(
                        session,
                        track,
                        samples,
                        initialLength,
                        endGain,
                        endGain
                    )
                } else {
                    streamSamples(session, track, samples, 0, startGain, endGain)
                }
                session.previousGain = endGain
            }

            if (!isCurrent(session)) {
                return
            }
            if (session.failureMessage != null) {
                postIfCurrent(session, Runnable {
                    listener.onMouthShape(MouthShape.CLOSED)
                    listener.onError("TTS 播放失败：" + session.failureMessage)
                })
                return
            }
            if (track == null || session.writtenSamples.get() == 0) {
                postIfCurrent(session, Runnable { listener.onError("TTS 播放失败：没有可播放音频") })
                return
            }

            waitForPlaybackToDrain(
                session,
                track,
                session.writtenSamples.get(),
                session.sampleRate
            )
            if (isCurrent(session)) {
                Log.i(
                    PERFORMANCE_TAG,
                    ("playback_end generation=" + session.generation
                            + " after_request_ms="
                            + elapsedSince(session.requestedAtMillis)
                            + " underruns=" + underrunCount(track))
                )
                postIfCurrent(session, Runnable {
                    listener.onMouthShape(MouthShape.CLOSED)
                    listener.onCompleted()
                })
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (isCurrent(session)) {
                postIfCurrent(session, Runnable {
                    listener.onMouthShape(MouthShape.CLOSED)
                    listener.onError("TTS 播放失败：" + safeMessage(error))
                })
            }
        } finally {
            if (track != null) {
                releaseTrack(track)
            }
        }
    }

    private fun streamSamples(
        session: PlaybackSession,
        track: AudioTrack,
        samples: FloatArray,
        startOffset: Int,
        startGain: kotlin.Float,
        endGain: kotlin.Float
    ) {
        var firstFrame = true
        var offset = startOffset
        while (offset < samples.size
        ) {
            if (!isCurrent(session)) {
                return
            }
            val length: Int = min(session.samplesPerFrame, samples.size - offset)
            writeFrame(
                session,
                track,
                samples,
                offset,
                length,
                if (firstFrame) startGain else endGain,
                endGain
            )
            firstFrame = false
            offset += session.samplesPerFrame
        }
    }

    private fun writeFrame(
        session: PlaybackSession,
        track: AudioTrack,
        samples: FloatArray,
        offset: Int,
        length: Int,
        startGain: kotlin.Float,
        endGain: kotlin.Float
    ) {
        val shape = shapeFor(samples, offset, length)
        if (shape != session.lastShape) {
            session.lastShape = shape
            postIfCurrent(session, Runnable { listener.onMouthShape(shape) })
        }
        PcmLoudnessNormalizer.copyWithGain(
            samples,
            offset,
            length,
            startGain,
            endGain,
            session.outputFrame
        )
        val written = track.write(
            session.outputFrame,
            0,
            length,
            AudioTrack.WRITE_BLOCKING
        )
        check(written >= 0) { "AudioTrack 写入失败：" + written }
        if (session.writtenSamples.getAndAdd(written) == 0) {
            Log.i(
                PERFORMANCE_TAG,
                ("first_pcm_written generation=" + session.generation
                        + " after_request_ms="
                        + elapsedSince(session.requestedAtMillis))
            )
        }
    }

    private inner class StreamingCallback(private val session: PlaybackSession) : (FloatArray) -> Int {
        private val queue: PcmChunkQueue = session.queue
        private var callbackCount = 0
        private var previousCallbackEndedAtMillis: Long = 0

        override fun invoke(samples: FloatArray): Int {
            if (!isCurrent(session)) {
                return 0
            }
            val callbackIndex = ++callbackCount
            val callbackStartedAtMillis = SystemClock.elapsedRealtime()
            val gapMillis = if (previousCallbackEndedAtMillis == 0L)
                -1L
            else
                callbackStartedAtMillis - previousCallbackEndedAtMillis
            Log.i(
                PERFORMANCE_TAG,
                ("callback_start generation=" + session.generation
                        + " index=" + callbackIndex
                        + " after_request_ms="
                        + elapsedSince(session.requestedAtMillis)
                        + " gap_ms=" + gapMillis
                        + " audio_ms=" + samplesToMillis(
                    samples.size,
                    session.sampleRate
                ))
            )
            if (!queue.offer(samples)) {
                session.fail("PCM 缓冲区已满，请缩短口播文字")
                return 0
            }
            if (session.enqueuedSamples.getAndAdd(samples.size) == 0) {
                Log.i(
                    PERFORMANCE_TAG,
                    ("first_pcm_enqueued generation=" + session.generation
                            + " after_request_ms="
                            + elapsedSince(session.requestedAtMillis))
                )
            }
            previousCallbackEndedAtMillis = SystemClock.elapsedRealtime()
            Log.i(
                PERFORMANCE_TAG,
                ("callback_end generation=" + session.generation
                        + " index=" + callbackIndex
                        + " callback_ms="
                        + (previousCallbackEndedAtMillis - callbackStartedAtMillis))
            )
            return 1
        }
    }

    private fun cancelPlayback(notifyStopped: Boolean) {
        val canceledGeneration = playbackGeneration.incrementAndGet()
        val session = activeSession
        activeSession = null
        if (session != null) {
            session.queue.cancel()
        }
        val track = audioTrack
        if (track != null) {
            try {
                track.pause()
                track.flush()
            } catch (ignored: IllegalStateException) {
                // The playback worker owns final release.
            }
        }
        if (notifyStopped) {
            post(Runnable {
                if (!closed.get() && playbackGeneration.get() == canceledGeneration) {
                    listener.onMouthShape(MouthShape.CLOSED)
                    listener.onStopped()
                }
            })
        }
    }

    private fun isCurrent(session: PlaybackSession): Boolean {
        return !closed.get() && activeSession == session && playbackGeneration.get() == session.generation
    }

    private fun isCurrentModelLoad(generation: Int): Boolean {
        return !closed.get() && modelGeneration.get() == generation
    }

    private fun postIfCurrent(session: PlaybackSession, action: Runnable) {
        post(Runnable {
            if (isCurrent(session)) {
                action.run()
            }
        })
    }

    private fun postError(message: String?) {
        post(Runnable {
            if (!closed.get()) {
                listener.onError(message)
            }
        })
    }

    private fun post(action: Runnable) {
        mainHandler.post(action)
    }

    private fun waitForPlaybackToDrain(
        session: PlaybackSession,
        track: AudioTrack,
        writtenSamples: Int,
        sampleRate: Int
    ) {
        val timeout: Long = (SystemClock.elapsedRealtime()
                + samplesToMillis(writtenSamples, sampleRate)
                + 2000L)
        while (isCurrent(session)
            && Integer.toUnsignedLong(track.getPlaybackHeadPosition()) < writtenSamples && SystemClock.elapsedRealtime() < timeout
        ) {
            SystemClock.sleep(10L)
        }
    }

    private fun releaseTrack(track: AudioTrack) {
        if (audioTrack === track) {
            audioTrack = null
        }
        try {
            track.stop()
        } catch (ignored: IllegalStateException) {
            // A canceled track may already be stopped.
        }
        track.release()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        modelGeneration.incrementAndGet()
        playbackGeneration.incrementAndGet()
        val session = activeSession
        activeSession = null
        if (session != null) {
            session.queue.cancel()
        }
        val track = audioTrack
        if (track != null) {
            try {
                track.pause()
                track.flush()
            } catch (ignored: IllegalStateException) {
                // The playback worker owns final release.
            }
        }
        modelWorker.execute(Runnable {
            val current = tts
            tts = null
            loadedLanguage = null
            if (current != null) {
                current.release()
            }
        })
        modelWorker.shutdown()
        playbackWorker.shutdown()
    }

    private class PlaybackSession(
        val generation: Int,
        val text: String,
        val speakerId: Int,
        val speed: kotlin.Float,
        val silenceScale: kotlin.Float,
        val targetRmsDbfs: kotlin.Float,
        val sampleRate: Int,
        requestedAtMillis: Long
    ) {
        val samplesPerFrame: Int
        val requestedAtMillis: Long
        val queue = PcmChunkQueue(PCM_QUEUE_CAPACITY)
        val enqueuedSamples = AtomicInteger()
        val writtenSamples = AtomicInteger()
        val outputFrame: FloatArray

        @kotlin.concurrent.Volatile
        var failureMessage: String? = null

        @kotlin.concurrent.Volatile
        var lastShape: MouthShape? = null
        var previousGain = kotlin.Float.Companion.NaN

        init {
            this.samplesPerFrame = max(
                1,
                sampleRate * FRAME_DURATION_MILLIS / 1000
            )
            this.outputFrame = FloatArray(samplesPerFrame)
            this.requestedAtMillis = requestedAtMillis
        }

        fun fail(message: String?) {
            if (failureMessage == null) {
                failureMessage = message
            }
        }
    }

    companion object {
        private const val PERFORMANCE_TAG = "HuimeiTtsPerf"
        private const val FRAME_DURATION_MILLIS = 20
        private const val PCM_QUEUE_CAPACITY = 64

        private fun createAudioTrack(sampleRate: Int): AudioTrack {
            val minimumBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            check(minimumBuffer > 0) { "AudioTrack 不支持采样率 " + sampleRate }
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(sampleRate)
                .build()
            val track = AudioTrack(
                attributes,
                format,
                minimumBuffer,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("AudioTrack 初始化失败")
            }
            return track
        }

        private fun elapsedSince(startedAtMillis: Long): Long {
            return SystemClock.elapsedRealtime() - startedAtMillis
        }

        private fun samplesToMillis(sampleCount: Int, sampleRate: Int): Long {
            return sampleCount * 1000L / sampleRate
        }

        private fun formatRtf(generationMillis: Long, audioMillis: Long): String {
            if (audioMillis <= 0L) {
                return "n/a"
            }
            return String.format(
                Locale.US, "%.3f",
                generationMillis / audioMillis.toDouble()
            )
        }

        private fun formatDb(value: kotlin.Float): String {
            return String.format(Locale.US, "%.2f", value)
        }

        private fun countPunctuation(text: String): Int {
            var count = 0
            for (index in 0..<text.length) {
                val character = text.get(index)
                if (character == '，' || character == '。' || character == '！' || character == '？' || character == ',' || character == '.' || character == '!' || character == '?') {
                    count++
                }
            }
            return count
        }

        private fun longestQuietRunMillis(samples: FloatArray, samplesPerFrame: Int): Long {
            var longestFrames = 0
            var currentFrames = 0
            var offset = 0
            while (offset < samples.size) {
                val length: Int = min(samplesPerFrame, samples.size - offset)
                if (shapeFor(samples, offset, length)
                    == MouthShape.CLOSED
                ) {
                    currentFrames++
                    longestFrames = max(longestFrames, currentFrames)
                } else {
                    currentFrames = 0
                }
                offset += samplesPerFrame
            }
            return (longestFrames * FRAME_DURATION_MILLIS).toLong()
        }

        private fun underrunCount(track: AudioTrack): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                track.getUnderrunCount()
            else
                -1
        }

        private fun safeMessage(error: Throwable): String {
            val message = error.message
            return if (message == null || message.trim { it <= ' ' }.isEmpty())
                error.javaClass.getSimpleName()
            else
                message
        }
    }
}
