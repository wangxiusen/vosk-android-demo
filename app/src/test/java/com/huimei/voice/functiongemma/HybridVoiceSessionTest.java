package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.TimeSource;

import org.junit.Test;

public final class HybridVoiceSessionTest {
    @Test
    public void sleepingIgnoresCommandsUntilTheWakePhrase() {
        MutableTime clock = new MutableTime();
        HybridVoiceSession session = new HybridVoiceSession(clock);
        session.start(VoiceLanguage.CHINESE);

        assertEquals(HybridVoiceSession.Action.NONE, session.accept("开机").action());
        HybridVoiceSession.Result wake = session.accept("惠美医疗");

        assertEquals(HybridVoiceSession.Action.WOKE, wake.action());
        assertEquals(HybridVoiceState.DICTATING, session.state());
        assertEquals(8_000L, session.remainingMillis());
    }

    @Test
    public void dictatingEmitsOneCommandAndSuppressesDuplicateFinalResults() {
        MutableTime clock = new MutableTime();
        HybridVoiceSession session = new HybridVoiceSession(clock);
        session.start(VoiceLanguage.ENGLISH);
        session.accept("hello medical");

        HybridVoiceSession.Result command = session.accept("please turn the machine on");
        HybridVoiceSession.Result duplicate = session.accept("please turn the machine on");

        assertEquals(HybridVoiceSession.Action.COMMAND, command.action());
        assertEquals("please turn the machine on", command.transcript());
        assertTrue(command.utteranceId() > 0L);
        assertEquals(HybridVoiceSession.Action.NONE, duplicate.action());
        assertEquals(HybridVoiceState.INFERENCING, session.state());
    }

    @Test
    public void wakeWindowTimesOutAtEightSeconds() {
        MutableTime clock = new MutableTime();
        HybridVoiceSession session = new HybridVoiceSession(clock);
        session.start(VoiceLanguage.CHINESE);
        session.accept("潓美医疗");

        clock.now = 7_999L;
        assertEquals(HybridVoiceSession.Action.NONE, session.pollTimeout().action());
        assertEquals(1L, session.remainingMillis());
        clock.now = 8_000L;
        assertEquals(HybridVoiceSession.Action.TIMED_OUT, session.pollTimeout().action());
        assertEquals(HybridVoiceState.SLEEPING, session.state());
    }

    @Test
    public void matchingInferenceCompletionReturnsToSleeping() {
        MutableTime clock = new MutableTime();
        HybridVoiceSession session = new HybridVoiceSession(clock);
        session.start(VoiceLanguage.CHINESE);
        session.accept("惠美医疗");
        long utteranceId = session.accept("帮我开机").utteranceId();

        assertFalse(session.completeInference(utteranceId + 1L));
        assertEquals(HybridVoiceState.INFERENCING, session.state());
        assertTrue(session.completeInference(utteranceId));
        assertEquals(HybridVoiceState.SLEEPING, session.state());
    }

    @Test
    public void languageChangeInvalidatesAnInFlightUtterance() {
        MutableTime clock = new MutableTime();
        HybridVoiceSession session = new HybridVoiceSession(clock);
        session.start(VoiceLanguage.CHINESE);
        session.accept("惠美医疗");
        long staleId = session.accept("帮我开机").utteranceId();

        session.switchLanguage(VoiceLanguage.ENGLISH);

        assertEquals(VoiceLanguage.ENGLISH, session.language());
        assertEquals(HybridVoiceState.SLEEPING, session.state());
        assertFalse(session.completeInference(staleId));
    }

    @Test
    public void stopAndCloseInvalidateCallbacks() {
        MutableTime clock = new MutableTime();
        HybridVoiceSession session = new HybridVoiceSession(clock);
        session.start(VoiceLanguage.CHINESE);
        session.accept("惠美医疗");
        long firstId = session.accept("帮我开机").utteranceId();

        session.stop();
        assertEquals(HybridVoiceState.STOPPED, session.state());
        assertFalse(session.completeInference(firstId));
        assertEquals(HybridVoiceSession.Action.NONE, session.accept("惠美医疗").action());

        session.start(VoiceLanguage.ENGLISH);
        session.close();
        session.start(VoiceLanguage.CHINESE);
        assertEquals(HybridVoiceState.STOPPED, session.state());
        assertEquals(HybridVoiceSession.Action.NONE, session.accept("惠美医疗").action());
    }

    private static final class MutableTime implements TimeSource {
        long now;

        @Override
        public long nowMillis() {
            return now;
        }
    }
}
