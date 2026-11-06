package com.huimei.voice.functiongemma

import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.CommandCatalog
import com.huimei.voice.recognition.TimeSource
import kotlin.math.max

class HybridVoiceSession(private val timeSource: TimeSource) {
    enum class Action { NONE, WOKE, COMMAND, TIMED_OUT }

    class Result private constructor(
        private val action: Action,
        private val transcript: String,
        private val utteranceId: Long
    ) {
        fun action(): Action = action
        fun transcript(): String = transcript
        fun utteranceId(): Long = utteranceId

        companion object {
            private val NONE = Result(Action.NONE, "", 0L)
            private val WOKE = Result(Action.WOKE, "", 0L)
            private val TIMED_OUT = Result(Action.TIMED_OUT, "", 0L)

            fun none(): Result = NONE
            fun woke(): Result = WOKE
            fun timedOut(): Result = TIMED_OUT
            fun command(transcript: String, utteranceId: Long) = Result(Action.COMMAND, transcript, utteranceId)
        }
    }

    private var state = HybridVoiceState.STOPPED
    private var language: VoiceLanguage? = null
    private var deadlineMillis = 0L
    private var utteranceGeneration = 0L
    private var activeUtteranceId = 0L
    private var closed = false

    @Synchronized
    fun start(requestedLanguage: VoiceLanguage) {
        if (closed) return
        language = requestedLanguage
        invalidateUtterance()
        state = HybridVoiceState.SLEEPING
    }

    @Synchronized
    fun switchLanguage(requestedLanguage: VoiceLanguage) = start(requestedLanguage)

    @Synchronized
    fun accept(transcript: String?): Result {
        if (closed || state == HybridVoiceState.STOPPED || transcript == null) return Result.none()
        if (state == HybridVoiceState.SLEEPING) {
            val match = CommandCatalog.forLanguage(requireNotNull(language)).find(transcript)
            if (!match.isPresent || match.get().event != CommandEvent.WAKE_UP) return Result.none()
            state = HybridVoiceState.DICTATING
            deadlineMillis = timeSource.nowMillis() + 8_000L
            return Result.woke()
        }
        if (state != HybridVoiceState.DICTATING) return Result.none()
        if (timeSource.nowMillis() >= deadlineMillis) {
            state = HybridVoiceState.SLEEPING
            return Result.timedOut()
        }
        val normalized = CommandCatalog.normalize(transcript)
        if (normalized.isEmpty()) return Result.none()
        activeUtteranceId = ++utteranceGeneration
        state = HybridVoiceState.INFERENCING
        return Result.command(normalized, activeUtteranceId)
    }

    @Synchronized
    fun pollTimeout(): Result {
        if (state == HybridVoiceState.DICTATING && timeSource.nowMillis() >= deadlineMillis) {
            state = HybridVoiceState.SLEEPING
            return Result.timedOut()
        }
        return Result.none()
    }

    @Synchronized
    fun completeInference(utteranceId: Long): Boolean {
        if (closed || state != HybridVoiceState.INFERENCING || utteranceId != activeUtteranceId) return false
        state = HybridVoiceState.SLEEPING
        activeUtteranceId = 0L
        return true
    }

    @Synchronized
    fun remainingMillis(): Long =
        if (state == HybridVoiceState.DICTATING) max(0L, deadlineMillis - timeSource.nowMillis()) else 0L

    @Synchronized
    fun stop() {
        if (closed) return
        invalidateUtterance()
        state = HybridVoiceState.STOPPED
    }

    @Synchronized
    fun close() {
        if (closed) return
        invalidateUtterance()
        closed = true
        state = HybridVoiceState.STOPPED
    }

    @Synchronized
    fun state(): HybridVoiceState = state

    @Synchronized
    fun language(): VoiceLanguage? = language

    private fun invalidateUtterance() {
        utteranceGeneration++
        activeUtteranceId = 0L
        deadlineMillis = 0L
    }
}
