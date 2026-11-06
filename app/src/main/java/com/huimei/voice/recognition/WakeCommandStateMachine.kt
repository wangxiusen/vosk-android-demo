package com.huimei.voice.recognition

import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.ListeningState
import com.huimei.voice.model.RecognitionAction
import java.util.Objects
import kotlin.math.max

class WakeCommandStateMachine(catalog: CommandCatalog?, timeSource: TimeSource?) {
    private var catalog: CommandCatalog
    private val timeSource: TimeSource
    private var state = ListeningState.SLEEPING
    private var deadlineMillis: Long = 0

    init {
        this.catalog = Objects.requireNonNull<CommandCatalog>(catalog)
        this.timeSource = Objects.requireNonNull<TimeSource>(timeSource)
    }

    fun accept(originalText: String): RecognitionAction {
        val timeout = pollTimeout()
        if (timeout.type == RecognitionAction.Type.TIMED_OUT) {
            return timeout
        }

        val optionalMatch = catalog.find(originalText)
        if (!optionalMatch.isPresent()) {
            return RecognitionAction.none()
        }

        val match = optionalMatch.get()
        if (match.event == CommandEvent.WAKE_UP) {
            state = ListeningState.AWAKE
            deadlineMillis = timeSource.nowMillis() + WAKE_WINDOW_MILLIS
            return RecognitionAction.wokeUp(match, originalText.trim { it <= ' ' })
        }

        if (state == ListeningState.SLEEPING) {
            return RecognitionAction.none()
        }

        state = ListeningState.SLEEPING
        deadlineMillis = 0L
        return RecognitionAction.command(match, originalText.trim { it <= ' ' })
    }

    fun pollTimeout(): RecognitionAction {
        if (state == ListeningState.AWAKE && timeSource.nowMillis() >= deadlineMillis) {
            state = ListeningState.SLEEPING
            deadlineMillis = 0L
            return RecognitionAction.timedOut()
        }
        return RecognitionAction.none()
    }

    fun reset(catalog: CommandCatalog?) {
        this.catalog = Objects.requireNonNull<CommandCatalog>(catalog)
        state = ListeningState.SLEEPING
        deadlineMillis = 0L
    }

    fun remainingMillis(): Long {
        if (state != ListeningState.AWAKE) {
            return 0L
        }
        return max(0L, deadlineMillis - timeSource.nowMillis())
    }

    fun restartWakeWindow(): Boolean {
        if (state != ListeningState.AWAKE) {
            return false
        }
        deadlineMillis = timeSource.nowMillis() + WAKE_WINDOW_MILLIS
        return true
    }

    fun state(): ListeningState {
        return state
    }

    companion object {
        const val WAKE_WINDOW_MILLIS: Long = 8000L
    }
}
