package com.huimei.voice.voiceprint

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.huimei.voice.voiceprint.VoiceprintMatcher.average
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.math.max

class VoiceprintController internal constructor(
    repository: VoiceprintRepository,
    engine: VoiceprintEngine,
    recorder: VoiceprintRecorder,
    worker: ExecutorService,
    callbackExecutor: Executor,
    listener: VoiceprintListener
) : AutoCloseable {
    enum class State {
        NEW,
        LOADING_MODEL,
        IDLE,
        RECORDING,
        PROCESSING,
        CLOSED
    }

    private val lock = Any()
    private val repository: VoiceprintRepository
    private val engine: VoiceprintEngine
    private val recorder: VoiceprintRecorder
    private val worker: ExecutorService
    private val callbackExecutor: Executor
    private val listener: VoiceprintListener
    private val pendingEnrollment: MutableList<ShortArray> = ArrayList<ShortArray>()

    @kotlin.concurrent.Volatile
    var state: State? = State.NEW
        private set
    private var generation: Long = 0
    private var initialized = false
    private var closed = false
    private var pendingDisplayName: String? = null

    constructor(
        context: Context?,
        repository: VoiceprintRepository,
        engine: VoiceprintEngine,
        listener: VoiceprintListener
    ) : this(
        repository,
        engine,
        VoiceprintAudioRecorder(),
        Executors.newSingleThreadExecutor(),
        MainThreadExecutor(),
        listener
    )

    init {
        require(!(repository == null || engine == null || recorder == null || worker == null || callbackExecutor == null || listener == null)) { "Voiceprint controller dependencies are required" }
        this.repository = repository
        this.engine = engine
        this.recorder = recorder
        this.worker = worker
        this.callbackExecutor = callbackExecutor
        this.listener = listener
    }

    fun initialize(): Boolean {
        val token: Long
        synchronized(lock) {
            if (closed || state != State.NEW) {
                return false
            }
            token = ++generation
            state = State.LOADING_MODEL
        }
        post(token, Runnable { listener.onStateChanged(State.LOADING_MODEL) })
        worker.execute(Runnable {
            val startedAt = System.nanoTime()
            try {
                if (!isActive(token)) {
                    return@Runnable
                }
                engine.initialize()
                val dimension = engine.dimension()
                check(dimension > 0) { "声纹模型维度无效" }
                val profiles: MutableList<VoiceprintProfile> = repository.listProfiles()
                synchronized(lock) {
                    if (!isActiveLocked(token)) {
                        return@Runnable
                    }
                    initialized = true
                    state = State.IDLE
                }
                val loadMillis: Long = elapsedMillis(startedAt)
                post(token, Runnable { listener.onModelReady(loadMillis, dimension) })
                post(token, Runnable { listener.onProfilesChanged(profiles) })
                post(token, Runnable { listener.onStateChanged(State.IDLE) })
            } catch (exception: Exception) {
                finishError(token, exception, State.NEW)
            }
        })
        return true
    }

    fun recordEnrollment(displayName: String): Boolean {
        val checkedName: String
        try {
            checkedName = requireDisplayName(displayName)
        } catch (exception: IllegalArgumentException) {
            postCurrentError(exception.message)
            return false
        }
        synchronized(lock) {
            if (!canStartLocked()) {
                return false
            }
            if (pendingEnrollment.size >= REQUIRED_ENROLLMENT_SAMPLES) {
                postCurrentError("已采集 3 条音频，请完成录入或取消后重录")
                return false
            }
            val currentName = pendingDisplayName
            if (currentName != null
                && (currentName.lowercase()
                        != checkedName.lowercase())
            ) {
                postCurrentError("本轮录入姓名不能变更")
                return false
            }
        }
        val token = beginOperation(State.RECORDING)
        if (token < 0) {
            return false
        }
        worker.execute(Runnable {
            try {
                requireActive(token)
                val pcm = recorder.capture()
                requireActive(token)
                val quality =
                    VoiceprintAudioQuality.analyze(pcm!!, VoiceprintAudioRecorder.SAMPLE_RATE)
                if (!quality.isAccepted) {
                    post(token, Runnable { listener.onAudioRejected(quality) })
                    post(
                        token,
                        Runnable { listener.onLog("录入音频未通过质量检查：" + quality.reason) })
                    finishIdle(token)
                    return@Runnable
                }
                val count: Int
                synchronized(lock) {
                    if (!isActiveLocked(token)) {
                        return@Runnable
                    }
                    pendingDisplayName = checkedName
                    pendingEnrollment.add(pcm.clone())
                    count = pendingEnrollment.size
                }
                val acceptedCount = count
                post(token, Runnable {
                    listener.onEnrollmentProgress(
                        acceptedCount, REQUIRED_ENROLLMENT_SAMPLES, quality
                    )
                })
                post(token, Runnable {
                    listener.onLog(
                        "录入样本 " + acceptedCount + "/" + REQUIRED_ENROLLMENT_SAMPLES + " 合格"
                    )
                })
                finishIdle(token)
            } catch (ignored: CancellationException) {
                // Cancellation advances the generation, so this operation has no callbacks.
            } catch (exception: Exception) {
                finishError(token, exception, State.IDLE)
            }
        })
        return true
    }

    fun finishEnrollment(): Boolean {
        val displayName: String?
        val samples: MutableList<ShortArray> = ArrayList()
        synchronized(lock) {
            if (!canStartLocked()) {
                return false
            }
            if (pendingDisplayName == null
                || pendingEnrollment.size != REQUIRED_ENROLLMENT_SAMPLES
            ) {
                postCurrentError("请先完成 3 条合格录音")
                return false
            }
            displayName = pendingDisplayName
            for (sample in pendingEnrollment) {
                samples.add(sample.clone())
            }
        }
        val token = beginOperation(State.PROCESSING)
        if (token < 0) {
            return false
        }
        worker.execute(Runnable {
            try {
                val profile = repository.saveEnrollment(
                    displayName!!, samples, VoiceprintAudioRecorder.SAMPLE_RATE
                )
                val vectors: MutableList<FloatArray?> = ArrayList<FloatArray?>()
                for (sample in samples) {
                    requireActive(token)
                    vectors.add(engine.extract(sample, VoiceprintAudioRecorder.SAMPLE_RATE))
                }
                val averaged = average(vectors)
                repository.saveEmbedding(
                    profile.id, engine.type()!!, engine.modelId()!!, averaged
                )
                val profiles: MutableList<VoiceprintProfile> = repository.listProfiles()
                synchronized(lock) {
                    if (!isActiveLocked(token)) {
                        return@Runnable
                    }
                    pendingEnrollment.clear()
                    pendingDisplayName = null
                }
                post(token, Runnable { listener.onProfilesChanged(profiles) })
                post(token, Runnable { listener.onLog("已完成声纹录入：" + profile.displayName) })
                finishIdle(token)
            } catch (ignored: CancellationException) {
                // Cancellation advances the generation, so this operation has no callbacks.
            } catch (exception: Exception) {
                finishError(token, exception, State.IDLE)
            }
        })
        return true
    }

    fun identify(): Boolean {
        val token = beginOperation(State.RECORDING)
        if (token < 0) {
            return false
        }
        worker.execute(Runnable { runMatch(token, null) })
        return true
    }

    fun verify(profileId: String?): Boolean {
        if (profileId == null || profileId.trim { it <= ' ' }.isEmpty()) {
            postCurrentError("请选择需要验证的人员")
            return false
        }
        val token = beginOperation(State.RECORDING)
        if (token < 0) {
            return false
        }
        worker.execute(Runnable { runMatch(token, profileId) })
        return true
    }

    fun deleteProfile(profileId: String?): Boolean {
        if (profileId == null || profileId.trim { it <= ' ' }.isEmpty()) {
            postCurrentError("请选择需要删除的人员")
            return false
        }
        val token = beginOperation(State.PROCESSING)
        if (token < 0) {
            return false
        }
        worker.execute(Runnable {
            try {
                repository.deleteProfile(profileId)
                val profiles: MutableList<VoiceprintProfile> = repository.listProfiles()
                post(token, Runnable { listener.onProfilesChanged(profiles) })
                post(token, Runnable { listener.onLog("已删除本地声纹资料") })
                finishIdle(token)
            } catch (exception: Exception) {
                finishError(token, exception, State.IDLE)
            }
        })
        return true
    }

    fun clearAll(): Boolean {
        val token = beginOperation(State.PROCESSING)
        if (token < 0) {
            return false
        }
        worker.execute(Runnable {
            try {
                repository.clearAll()
                synchronized(lock) {
                    pendingEnrollment.clear()
                    pendingDisplayName = null
                }
                val profiles: MutableList<VoiceprintProfile> = repository.listProfiles()
                post(token, Runnable { listener.onProfilesChanged(profiles) })
                post(token, Runnable { listener.onLog("已清空全部本地声纹资料") })
                finishIdle(token)
            } catch (exception: Exception) {
                finishError(token, exception, State.IDLE)
            }
        })
        return true
    }

    fun cancelActiveOperation() {
        val token: Long
        val nextState: State
        synchronized(lock) {
            if (closed) {
                return
            }
            token = ++generation
            nextState = if (initialized) State.IDLE else State.NEW
            state = nextState
        }
        recorder.cancel()
        post(token, Runnable { listener.onStateChanged(nextState) })
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            generation++
            state = State.CLOSED
        }
        recorder.cancel()
        worker.shutdownNow()
        engine.close()
    }

    private fun runMatch(token: Long, requestedProfileId: String?) {
        var inferenceStartedAt = 0L
        try {
            var profiles: MutableList<VoiceprintProfile> = repository.listProfiles()
            if (profiles.isEmpty()) {
                throw UserVisibleException("请先录入至少一位人员的声纹")
            }
            if (requestedProfileId != null) {
                val selected: VoiceprintProfile = findProfile(profiles, requestedProfileId)
                profiles = ArrayList<VoiceprintProfile>()
                profiles.add(selected)
            }
            requireActive(token)
            val pcm = recorder.capture()
            requireActive(token)
            val quality =
                VoiceprintAudioQuality.analyze(pcm!!, VoiceprintAudioRecorder.SAMPLE_RATE)
            if (!quality.isAccepted) {
                post(token, Runnable { listener.onAudioRejected(quality) })
                finishIdle(token)
                return
            }
            transition(token, State.PROCESSING)
            inferenceStartedAt = System.nanoTime()
            val enrolled: MutableMap<VoiceprintProfile?, FloatArray?> =
                LinkedHashMap<VoiceprintProfile?, FloatArray?>()
            for (profile in profiles) {
                requireActive(token)
                enrolled.put(profile, loadOrRegenerateEmbedding(profile, token))
            }
            val probe = engine.extract(pcm, VoiceprintAudioRecorder.SAMPLE_RATE)
            val match = VoiceprintMatcher.search(
                enrolled, probe!!, engine.type()!!.initialThreshold()
            )
            val inferenceMillis: Long = elapsedMillis(inferenceStartedAt)
            post(token, Runnable { listener.onMatch(match, inferenceMillis) })
            post(token, Runnable {
                listener.onLog(
                    if (match.isAccepted)
                        "识别通过：" + match.profile!!.displayName
                    else
                        "未匹配到已录入人员"
                )
            })
            finishIdle(token)
        } catch (ignored: CancellationException) {
            // Cancellation advances the generation, so this operation has no callbacks.
        } catch (exception: Exception) {
            finishError(token, exception, State.IDLE)
        }
    }

    @kotlin.Throws(Exception::class)
    private fun loadOrRegenerateEmbedding(profile: VoiceprintProfile, token: Long): FloatArray? {
        val cached = repository.loadEmbedding(
            profile.id, engine.type()!!, engine.modelId()!!, engine.dimension()
        )
        if (cached.isPresent()) {
            return cached.get()
        }
        val audio: MutableList<WavIO.Audio> = repository.loadEnrollment(profile.id)
        val vectors: MutableList<FloatArray?> = ArrayList<FloatArray?>()
        for (sample in audio) {
            requireActive(token)
            vectors.add(engine.extract(sample.getSamples(), sample.sampleRate))
        }
        val regenerated = average(vectors)
        repository.saveEmbedding(
            profile.id, engine.type()!!, engine.modelId()!!, regenerated
        )
        return regenerated
    }

    private fun beginOperation(operationState: State?): Long {
        val token: Long
        synchronized(lock) {
            if (!canStartLocked()) {
                return -1L
            }
            token = ++generation
            state = operationState
        }
        post(token, Runnable { listener.onStateChanged(operationState) })
        return token
    }

    private fun canStartLocked(): Boolean {
        return !closed && initialized && state == State.IDLE
    }

    private fun transition(token: Long, nextState: State?) {
        synchronized(lock) {
            if (!isActiveLocked(token)) {
                throw CancellationException()
            }
            state = nextState
        }
        post(token, Runnable { listener.onStateChanged(nextState) })
    }

    private fun finishIdle(token: Long) {
        synchronized(lock) {
            if (!isActiveLocked(token)) {
                return
            }
            state = State.IDLE
        }
        post(token, Runnable { listener.onStateChanged(State.IDLE) })
    }

    private fun finishError(token: Long, exception: Exception, recoveryState: State?) {
        var message = exception.message
        if (message == null || message.trim { it <= ' ' }.isEmpty()) {
            message = exception.javaClass.getSimpleName()
        }
        synchronized(lock) {
            if (!isActiveLocked(token)) {
                return
            }
            state = recoveryState
        }
        val finalMessage = message
        post(token, Runnable { listener.onError(finalMessage) })
        post(token, Runnable { listener.onLog("操作失败：" + finalMessage) })
        post(token, Runnable { listener.onStateChanged(recoveryState) })
    }

    private fun postCurrentError(message: String?) {
        val token: Long
        synchronized(lock) {
            if (closed) {
                return
            }
            token = generation
        }
        post(token, Runnable { listener.onError(message) })
    }

    private fun post(token: Long, callback: Runnable) {
        callbackExecutor.execute(Runnable {
            if (isActive(token)) {
                callback.run()
            }
        })
    }

    private fun requireActive(token: Long) {
        if (!isActive(token)) {
            throw CancellationException()
        }
    }

    private fun isActive(token: Long): Boolean {
        synchronized(lock) {
            return isActiveLocked(token)
        }
    }

    private fun isActiveLocked(token: Long): Boolean {
        return !closed && generation == token
    }

    private class MainThreadExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    private class UserVisibleException(message: String?) : Exception(message)
    companion object {
        const val REQUIRED_ENROLLMENT_SAMPLES: Int = 3

        @kotlin.Throws(IOException::class)
        private fun findProfile(
            profiles: MutableList<VoiceprintProfile>,
            profileId: String?
        ): VoiceprintProfile {
            for (profile in profiles) {
                if (profile.id == profileId) {
                    return profile
                }
            }
            throw IOException("所选人员不存在")
        }

        private fun requireDisplayName(displayName: String): String {
            require(!(displayName == null || displayName.trim { it <= ' ' }
                .isEmpty())) { "请输入人员姓名" }
            return displayName.trim { it <= ' ' }
        }

        private fun elapsedMillis(startedAtNanos: Long): Long {
            return max(0L, (System.nanoTime() - startedAtNanos) / 1000000L)
        }
    }
}
