package com.huimei.voice.functiongemma;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.CommandMatch;
import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.CommandCatalog;
import com.huimei.voice.recognition.TimeSource;

import java.util.Objects;
import java.util.Optional;

public final class HybridVoiceSession {
    private static final long WAKE_WINDOW_MILLIS = 8_000L;

    public enum Action {
        NONE,
        WOKE,
        COMMAND,
        TIMED_OUT
    }

    private final TimeSource timeSource;
    private HybridVoiceState state = HybridVoiceState.STOPPED;
    private VoiceLanguage language;
    private long deadlineMillis;
    private long utteranceGeneration;
    private long activeUtteranceId;
    private boolean closed;

    public HybridVoiceSession(TimeSource timeSource) {
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    public synchronized void start(VoiceLanguage requestedLanguage) {
        if (closed) {
            return;
        }
        language = Objects.requireNonNull(requestedLanguage, "requestedLanguage");
        invalidateUtterance();
        state = HybridVoiceState.SLEEPING;
    }

    public synchronized void switchLanguage(VoiceLanguage requestedLanguage) {
        start(requestedLanguage);
    }

    public synchronized Result accept(String transcript) {
        if (closed || state == HybridVoiceState.STOPPED || transcript == null) {
            return Result.none();
        }
        if (state == HybridVoiceState.SLEEPING) {
            Optional<CommandMatch> match = CommandCatalog.forLanguage(language).find(transcript);
            if (!match.isPresent() || match.get().getEvent() != CommandEvent.WAKE_UP) {
                return Result.none();
            }
            state = HybridVoiceState.DICTATING;
            deadlineMillis = timeSource.nowMillis() + WAKE_WINDOW_MILLIS;
            return Result.woke();
        }
        if (state != HybridVoiceState.DICTATING) {
            return Result.none();
        }
        if (timeSource.nowMillis() >= deadlineMillis) {
            state = HybridVoiceState.SLEEPING;
            return Result.timedOut();
        }
        String normalized = CommandCatalog.normalize(transcript);
        if (normalized.isEmpty()) {
            return Result.none();
        }
        activeUtteranceId = ++utteranceGeneration;
        state = HybridVoiceState.INFERENCING;
        return Result.command(normalized, activeUtteranceId);
    }

    public synchronized Result pollTimeout() {
        if (state == HybridVoiceState.DICTATING
                && timeSource.nowMillis() >= deadlineMillis) {
            state = HybridVoiceState.SLEEPING;
            return Result.timedOut();
        }
        return Result.none();
    }

    public synchronized boolean completeInference(long utteranceId) {
        if (closed
                || state != HybridVoiceState.INFERENCING
                || utteranceId != activeUtteranceId) {
            return false;
        }
        state = HybridVoiceState.SLEEPING;
        activeUtteranceId = 0L;
        return true;
    }

    public synchronized long remainingMillis() {
        if (state != HybridVoiceState.DICTATING) {
            return 0L;
        }
        return Math.max(0L, deadlineMillis - timeSource.nowMillis());
    }

    public synchronized void stop() {
        if (closed) {
            return;
        }
        invalidateUtterance();
        state = HybridVoiceState.STOPPED;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        invalidateUtterance();
        closed = true;
        state = HybridVoiceState.STOPPED;
    }

    public synchronized HybridVoiceState state() {
        return state;
    }

    public synchronized VoiceLanguage language() {
        return language;
    }

    private void invalidateUtterance() {
        utteranceGeneration++;
        activeUtteranceId = 0L;
        deadlineMillis = 0L;
    }

    public static final class Result {
        private static final Result NONE = new Result(Action.NONE, "", 0L);
        private static final Result WOKE = new Result(Action.WOKE, "", 0L);
        private static final Result TIMED_OUT = new Result(Action.TIMED_OUT, "", 0L);

        private final Action action;
        private final String transcript;
        private final long utteranceId;

        private Result(Action action, String transcript, long utteranceId) {
            this.action = action;
            this.transcript = transcript;
            this.utteranceId = utteranceId;
        }

        static Result none() {
            return NONE;
        }

        static Result woke() {
            return WOKE;
        }

        static Result timedOut() {
            return TIMED_OUT;
        }

        static Result command(String transcript, long utteranceId) {
            return new Result(Action.COMMAND, transcript, utteranceId);
        }

        public Action action() {
            return action;
        }

        public String transcript() {
            return transcript;
        }

        public long utteranceId() {
            return utteranceId;
        }
    }
}
