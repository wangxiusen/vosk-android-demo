package com.huimei.voice.recognition;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.CommandMatch;
import com.huimei.voice.model.ListeningState;
import com.huimei.voice.model.RecognitionAction;

import java.util.Objects;
import java.util.Optional;

public final class WakeCommandStateMachine {
    public static final long WAKE_WINDOW_MILLIS = 8_000L;

    private CommandCatalog catalog;
    private final TimeSource timeSource;
    private ListeningState state = ListeningState.SLEEPING;
    private long deadlineMillis;

    public WakeCommandStateMachine(CommandCatalog catalog, TimeSource timeSource) {
        this.catalog = Objects.requireNonNull(catalog);
        this.timeSource = Objects.requireNonNull(timeSource);
    }

    public RecognitionAction accept(String originalText) {
        RecognitionAction timeout = pollTimeout();
        if (timeout.getType() == RecognitionAction.Type.TIMED_OUT) {
            return timeout;
        }

        Optional<CommandMatch> optionalMatch = catalog.find(originalText);
        if (!optionalMatch.isPresent()) {
            return RecognitionAction.none();
        }

        CommandMatch match = optionalMatch.get();
        if (match.getEvent() == CommandEvent.WAKE_UP) {
            state = ListeningState.AWAKE;
            deadlineMillis = timeSource.nowMillis() + WAKE_WINDOW_MILLIS;
            return RecognitionAction.wokeUp(match, originalText.trim());
        }

        if (state == ListeningState.SLEEPING) {
            return RecognitionAction.none();
        }

        state = ListeningState.SLEEPING;
        deadlineMillis = 0L;
        return RecognitionAction.command(match, originalText.trim());
    }

    public RecognitionAction pollTimeout() {
        if (state == ListeningState.AWAKE && timeSource.nowMillis() >= deadlineMillis) {
            state = ListeningState.SLEEPING;
            deadlineMillis = 0L;
            return RecognitionAction.timedOut();
        }
        return RecognitionAction.none();
    }

    public void reset(CommandCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
        state = ListeningState.SLEEPING;
        deadlineMillis = 0L;
    }

    public long remainingMillis() {
        if (state != ListeningState.AWAKE) {
            return 0L;
        }
        return Math.max(0L, deadlineMillis - timeSource.nowMillis());
    }

    public ListeningState state() {
        return state;
    }
}
