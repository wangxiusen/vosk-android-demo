package com.huimei.voice.face

import android.os.Handler
import android.os.Looper
import java.util.Optional
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.math.max

class FaceRecognitionController internal constructor(
    repository: FaceRepository,
    engine: FaceEngine,
    worker: ExecutorService,
    callbackExecutor: Executor,
    listener: FaceRecognitionListener
) : AutoCloseable {
    enum class State {
        NEW,
        LOADING,
        IDLE,
        ENROLLING,
        RECOGNIZING,
        CLOSED
    }

    private val lock = Any()
    private val repository: FaceRepository
    private val engine: FaceEngine
    private val worker: ExecutorService
    private val callbackExecutor: Executor
    private val listener: FaceRecognitionListener
    private val stabilizer = FaceRecognitionStabilizer(3)
    private val enrollmentVectors: MutableList<FloatArray> = ArrayList<FloatArray>()

    @kotlin.concurrent.Volatile
    var state: State? = State.NEW
        private set
    private var generation: Long = 0
    private var initialized = false
    private var closed = false
    private var frameInFlight = false
    private var lastFrameTimestamp = Long.Companion.MIN_VALUE
    private var enrollmentName: String? = null
    private var enrollmentPortrait: ByteArray? = null
    private var profiles: MutableList<FaceProfile> = ArrayList<FaceProfile>()
    private var embeddings: MutableMap<String?, FloatArray?> = LinkedHashMap<String?, FloatArray?>()

    constructor(
        repository: FaceRepository,
        engine: FaceEngine,
        listener: FaceRecognitionListener
    ) : this(
        repository,
        engine,
        Executors.newSingleThreadExecutor(),
        MainThreadExecutor(),
        listener
    )

    init {
        require(!(repository == null || engine == null || worker == null || callbackExecutor == null || listener == null)) { "Face controller dependencies are required" }
        this.repository = repository
        this.engine = engine
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
            state = State.LOADING
        }
        post(token, Runnable { listener.onStateChanged(State.LOADING) })
        worker.execute(Runnable {
            val startedAt = System.nanoTime()
            try {
                requireActive(token)
                engine.initialize()
                val dimension = engine.dimension()
                check(dimension > 0) { "人脸模型维度无效" }
                val loaded = loadCache(dimension)
                synchronized(lock) {
                    requireActiveLocked(token)
                    profiles = loaded.profiles
                    embeddings = loaded.embeddings
                    initialized = true
                    state = State.IDLE
                }
                post(token, Runnable { listener.onModelReady(elapsedMillis(startedAt), dimension) })
                postProfiles(token, loaded.profiles)
                post(token, Runnable { listener.onStateChanged(State.IDLE) })
            } catch (ignored: CancellationException) {
                // Generation changed; stale callbacks are intentionally suppressed.
            } catch (exception: Exception) {
                finishError(token, exception, State.NEW)
            }
        })
        return true
    }

    fun startEnrollment(displayName: String): Boolean {
        val checkedName: String?
        try {
            checkedName = requireDisplayName(displayName)
        } catch (exception: IllegalArgumentException) {
            postCurrentError(exception.message)
            return false
        }
        val token: Long
        synchronized(lock) {
            if (!canStartLocked()) {
                return false
            }
            token = ++generation
            state = State.ENROLLING
            resetFrameFlowLocked()
            enrollmentVectors.clear()
            enrollmentName = checkedName
            enrollmentPortrait = null
            stabilizer.reset()
        }
        post(token, Runnable { listener.onStateChanged(State.ENROLLING) })
        post(token, Runnable { listener.onEnrollmentProgress(0, REQUIRED_ENROLLMENT_SAMPLES) })
        post(token, Runnable { listener.onLog("开始录入人脸：" + checkedName) })
        return true
    }

    fun startRecognition(): Boolean {
        val token: Long
        synchronized(lock) {
            if (!canStartLocked()) {
                return false
            }
            if (profiles.isEmpty()) {
                postCurrentError("请先录入至少一位人员的人脸")
                return false
            }
            token = ++generation
            state = State.RECOGNIZING
            resetFrameFlowLocked()
            stabilizer.reset()
        }
        post(token, Runnable { listener.onStateChanged(State.RECOGNIZING) })
        post(token, Runnable { listener.onLog("开始实时人脸识别") })
        return true
    }

    fun acceptFrame(frame: FaceFrame, timestampMillis: Long): Boolean {
        requireNotNull(frame) { "Face frame is required" }
        val token: Long
        val mode: State?
        synchronized(lock) {
            if (closed || frameInFlight
                || (state != State.ENROLLING && state != State.RECOGNIZING)
            ) {
                return false
            }
            if (lastFrameTimestamp != Long.Companion.MIN_VALUE
                && timestampMillis - lastFrameTimestamp < MIN_FRAME_INTERVAL_MILLIS
            ) {
                return false
            }
            token = generation
            mode = state
            lastFrameTimestamp = timestampMillis
            frameInFlight = true
        }
        worker.execute(Runnable { analyzeFrame(token, mode, frame) })
        return true
    }

    fun stop() {
        val token: Long
        synchronized(lock) {
            if (closed) {
                return
            }
            token = ++generation
            state = if (initialized) State.IDLE else State.NEW
            enrollmentVectors.clear()
            enrollmentName = null
            enrollmentPortrait = null
            resetFrameFlowLocked()
            stabilizer.reset()
        }
        val nextState = state
        post(token, Runnable { listener.onStateChanged(nextState) })
    }

    fun deleteProfile(profileId: String?): Boolean {
        if (profileId == null || profileId.trim { it <= ' ' }.isEmpty()) {
            postCurrentError("请选择需要删除的人员")
            return false
        }
        val token = beginStorageOperation()
        if (token < 0L) {
            return false
        }
        worker.execute(Runnable {
            try {
                repository.deleteProfile(profileId)
                val loaded = loadCache(engine.dimension())
                applyCache(token, loaded)
                post(token, Runnable { listener.onLog("已删除本地人脸资料") })
                finishIdle(token)
            } catch (exception: Exception) {
                finishError(token, exception, State.IDLE)
            }
        })
        return true
    }

    fun clearAll(): Boolean {
        val token = beginStorageOperation()
        if (token < 0L) {
            return false
        }
        worker.execute(Runnable {
            try {
                repository.clearAll()
                val loaded = loadCache(engine.dimension())
                applyCache(token, loaded)
                post(token, Runnable { listener.onLog("已清空全部本地人脸资料") })
                finishIdle(token)
            } catch (exception: Exception) {
                finishError(token, exception, State.IDLE)
            }
        })
        return true
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            generation++
            state = State.CLOSED
            resetFrameFlowLocked()
            enrollmentVectors.clear()
        }
        worker.shutdownNow()
        engine.close()
    }

    private fun analyzeFrame(token: Long, mode: State?, frame: FaceFrame) {
        try {
            requireActive(token)
            val observation = engine.analyze(frame)
            requireActive(token)
            if (!observation!!.isQualityAccepted) {
                if (mode == State.RECOGNIZING) {
                    stabilizer.reset()
                }
                post(token, Runnable { listener.onFrameRejected(observation.qualityReason()) })
                return
            }
            check(observation.embedding()!!.size == engine.dimension()) { "人脸特征维度与模型不一致" }
            if (mode == State.ENROLLING) {
                handleEnrollment(token, observation)
            } else {
                handleRecognition(token, observation)
            }
        } catch (ignored: CancellationException) {
            // Generation changed; stale callbacks are intentionally suppressed.
        } catch (exception: Exception) {
            finishError(token, exception, State.IDLE)
        } finally {
            synchronized(lock) {
                if (isActiveLocked(token)) {
                    frameInFlight = false
                }
            }
        }
    }

    @kotlin.Throws(Exception::class)
    private fun handleEnrollment(token: Long, observation: FaceObservation) {
        val acceptedCount: Int
        val displayName: String?
        val completedVectors: MutableList<FloatArray>?
        val portrait: ByteArray?
        synchronized(lock) {
            requireActiveLocked(token)
            enrollmentVectors.add(observation.embedding()!!)
            enrollmentPortrait = observation.portraitJpeg()
            acceptedCount = enrollmentVectors.size
            displayName = enrollmentName
            if (acceptedCount == REQUIRED_ENROLLMENT_SAMPLES) {
                completedVectors = cloneVectors(enrollmentVectors)
                portrait = enrollmentPortrait!!.clone()
            } else {
                completedVectors = null
                portrait = null
            }
        }
        post(token, Runnable {
            listener.onEnrollmentProgress(
                acceptedCount, REQUIRED_ENROLLMENT_SAMPLES
            )
        })
        if (completedVectors == null) {
            return
        }

        val averaged = FaceMatcher.averageNormalized(completedVectors)
        val profile = repository.saveEnrollment(displayName!!, averaged, portrait!!)
        val loaded = loadCache(engine.dimension())
        synchronized(lock) {
            requireActiveLocked(token)
            profiles = loaded.profiles
            embeddings = loaded.embeddings
            enrollmentVectors.clear()
            enrollmentName = null
            enrollmentPortrait = null
            state = State.IDLE
        }
        postProfiles(token, loaded.profiles)
        post(token, Runnable { listener.onLog("已完成人脸录入：" + profile.displayName) })
        post(token, Runnable { listener.onStateChanged(State.IDLE) })
    }

    private fun handleRecognition(token: Long, observation: FaceObservation) {
        val profileSnapshot: MutableList<FaceProfile>
        val embeddingSnapshot: MutableMap<String?, FloatArray?>?
        synchronized(lock) {
            requireActiveLocked(token)
            profileSnapshot = ArrayList<FaceProfile>(profiles)
            embeddingSnapshot = cloneEmbeddingMap(embeddings)
        }
        val match = FaceMatcher.best(
            observation.embedding()!!, profileSnapshot, embeddingSnapshot!!,
            INITIAL_MATCH_THRESHOLD
        )
        post(token, Runnable { listener.onCandidate(match, observation.inferenceMillis()) })
        val confirmed: Optional<FaceMatch> = stabilizer.accept(match!!)
        if (confirmed.isPresent()) {
            val value = confirmed.get()
            post(token, Runnable { listener.onMatchConfirmed(value) })
            post(token, Runnable {
                listener.onLog(
                    "人脸识别通过：" + value.profile!!.displayName
                )
            })
        }
    }

    @kotlin.Throws(Exception::class)
    private fun loadCache(dimension: Int): Cache {
        val loadedProfiles: MutableList<FaceProfile> = repository.listProfiles()
        val loadedEmbeddings: MutableMap<String?, FloatArray?> =
            LinkedHashMap<String?, FloatArray?>()
        for (profile in loadedProfiles) {
            loadedEmbeddings.put(
                profile.id, repository.loadEmbedding(profile.id, dimension)
            )
        }
        return Cache(loadedProfiles, loadedEmbeddings)
    }

    private fun applyCache(token: Long, cache: Cache) {
        synchronized(lock) {
            requireActiveLocked(token)
            profiles = cache.profiles
            embeddings = cache.embeddings
        }
        postProfiles(token, cache.profiles)
    }

    private fun beginStorageOperation(): Long {
        val token: Long
        synchronized(lock) {
            if (!canStartLocked()) {
                return -1L
            }
            token = ++generation
            state = State.LOADING
            resetFrameFlowLocked()
        }
        post(token, Runnable { listener.onStateChanged(State.LOADING) })
        return token
    }

    private fun finishIdle(token: Long) {
        synchronized(lock) {
            requireActiveLocked(token)
            state = State.IDLE
            resetFrameFlowLocked()
        }
        post(token, Runnable { listener.onStateChanged(State.IDLE) })
    }

    private fun finishError(token: Long, exception: Exception, fallback: State?) {
        var message = exception.message
        if (message == null || message.trim { it <= ' ' }.isEmpty()) {
            message = exception.javaClass.getSimpleName()
        }
        synchronized(lock) {
            if (!isActiveLocked(token)) {
                return
            }
            state = fallback
            resetFrameFlowLocked()
            enrollmentVectors.clear()
            enrollmentName = null
            enrollmentPortrait = null
            stabilizer.reset()
        }
        val visibleMessage = message
        post(token, Runnable { listener.onError(visibleMessage) })
        post(token, Runnable { listener.onStateChanged(fallback) })
    }

    private fun postProfiles(token: Long, values: MutableList<FaceProfile>) {
        val snapshot: MutableList<FaceProfile> = ArrayList<FaceProfile>(values)
        post(token, Runnable { listener.onProfilesChanged(snapshot) })
    }

    private fun postCurrentError(message: String?) {
        val token: Long
        synchronized(lock) {
            token = generation
        }
        post(token, Runnable { listener.onError(message) })
    }

    private fun post(token: Long, callback: Runnable) {
        callbackExecutor.execute(Runnable {
            synchronized(lock) {
                if (!isActiveLocked(token)) {
                    return@Runnable
                }
            }
            callback.run()
        })
    }

    private fun canStartLocked(): Boolean {
        return !closed && initialized && state == State.IDLE
    }

    private fun requireActive(token: Long) {
        synchronized(lock) {
            requireActiveLocked(token)
        }
    }

    private fun requireActiveLocked(token: Long) {
        if (!isActiveLocked(token)) {
            throw CancellationException()
        }
    }

    private fun isActiveLocked(token: Long): Boolean {
        return !closed && generation == token
    }

    private fun resetFrameFlowLocked() {
        frameInFlight = false
        lastFrameTimestamp = Long.Companion.MIN_VALUE
    }

    private class Cache(
        profiles: MutableList<FaceProfile>,
        embeddings: MutableMap<String?, FloatArray?>
    ) {
        val profiles: MutableList<FaceProfile>
        val embeddings: MutableMap<String?, FloatArray?>

        init {
            this.profiles = ArrayList<FaceProfile>(profiles)
            this.embeddings = cloneEmbeddingMap(embeddings)
        }
    }

    private class MainThreadExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    companion object {
        const val REQUIRED_ENROLLMENT_SAMPLES: Int = 5
        const val MIN_FRAME_INTERVAL_MILLIS: Long = 150L
        const val INITIAL_MATCH_THRESHOLD: Float = 0.363f

        private fun requireDisplayName(displayName: String): String {
            require(!(displayName == null || displayName.trim { it <= ' ' }
                .isEmpty())) { "请输入人员姓名" }
            return displayName.trim { it <= ' ' }
        }

        private fun cloneVectors(values: MutableList<FloatArray>): MutableList<FloatArray> {
            val copy: MutableList<FloatArray> = ArrayList<FloatArray>()
            for (value in values) {
                copy.add(value.clone())
            }
            return copy
        }

        private fun cloneEmbeddingMap(values: MutableMap<String?, FloatArray?>): MutableMap<String?, FloatArray?> {
            val copy: MutableMap<String?, FloatArray?> = LinkedHashMap<String?, FloatArray?>()
            for (entry in values.entries) {
                copy.put(entry.key, entry.value!!.clone())
            }
            return copy
        }

        private fun elapsedMillis(startedNanos: Long): Long {
            return max(0L, (System.nanoTime() - startedNanos) / 1000000L)
        }
    }
}
