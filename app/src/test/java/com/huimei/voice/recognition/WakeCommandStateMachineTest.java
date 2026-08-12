package com.huimei.voice.recognition;

import static org.junit.Assert.assertEquals;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.ListeningState;
import com.huimei.voice.model.RecognitionAction;
import com.huimei.voice.model.VoiceLanguage;

import org.junit.Before;
import org.junit.Test;

public class WakeCommandStateMachineTest {
    private FakeTimeSource clock;
    private WakeCommandStateMachine machine;

    @Before
    public void setUp() {
        clock = new FakeTimeSource(1_000L);
        machine = new WakeCommandStateMachine(
                CommandCatalog.forLanguage(VoiceLanguage.CHINESE), clock);
    }

    @Test
    public void ignoresCommandsWhileSleeping() {
        RecognitionAction action = machine.accept("开机");

        assertEquals(RecognitionAction.Type.NONE, action.getType());
        assertEquals(ListeningState.SLEEPING, machine.state());
    }

    @Test
    public void acceptsChineseWakeAliasesAndStartsEightSecondWindow() {
        RecognitionAction action = machine.accept("惠美医疗");

        assertEquals(RecognitionAction.Type.WOKE_UP, action.getType());
        assertEquals(CommandEvent.WAKE_UP, action.getMatch().getEvent());
        assertEquals("惠美医疗", action.getOriginalText());
        assertEquals(ListeningState.AWAKE, machine.state());
        assertEquals(8_000L, machine.remainingMillis());
    }

    @Test
    public void acceptsEnglishWakePhraseAfterLanguageReset() {
        machine.reset(CommandCatalog.forLanguage(VoiceLanguage.ENGLISH));

        RecognitionAction action = machine.accept("  HELLO   Medical ");

        assertEquals(RecognitionAction.Type.WOKE_UP, action.getType());
        assertEquals(ListeningState.AWAKE, machine.state());
    }

    @Test
    public void commandInsideWindowEmitsEventAndReturnsToSleep() {
        machine.accept("潓美医疗");
        clock.advance(7_999L);

        RecognitionAction action = machine.accept("半小时产气");

        assertEquals(RecognitionAction.Type.COMMAND, action.getType());
        assertEquals(CommandEvent.GAS_30_MINUTES, action.getMatch().getEvent());
        assertEquals(ListeningState.SLEEPING, machine.state());
    }

    @Test
    public void commandAtDeadlineTimesOutWithoutEmittingCommand() {
        machine.accept("潓美医疗");
        clock.advance(8_000L);

        RecognitionAction action = machine.accept("开机");

        assertEquals(RecognitionAction.Type.TIMED_OUT, action.getType());
        assertEquals(ListeningState.SLEEPING, machine.state());
    }

    @Test
    public void unknownSpeechDoesNotCloseWakeWindow() {
        machine.accept("潓美医疗");
        clock.advance(2_000L);

        RecognitionAction action = machine.accept("无效命令");

        assertEquals(RecognitionAction.Type.NONE, action.getType());
        assertEquals(ListeningState.AWAKE, machine.state());
        assertEquals(6_000L, machine.remainingMillis());
    }

    @Test
    public void repeatedWakePhraseRefreshesWindow() {
        machine.accept("潓美医疗");
        clock.advance(6_000L);

        RecognitionAction action = machine.accept("惠美医疗");

        assertEquals(RecognitionAction.Type.WOKE_UP, action.getType());
        assertEquals(8_000L, machine.remainingMillis());
    }

    @Test
    public void pollTimeoutAndLanguageResetReturnToSleep() {
        machine.accept("潓美医疗");
        clock.advance(8_001L);

        assertEquals(RecognitionAction.Type.TIMED_OUT, machine.pollTimeout().getType());
        assertEquals(ListeningState.SLEEPING, machine.state());

        machine.accept("潓美医疗");
        machine.reset(CommandCatalog.forLanguage(VoiceLanguage.ENGLISH));
        assertEquals(ListeningState.SLEEPING, machine.state());
        assertEquals(0L, machine.remainingMillis());
    }

    private static final class FakeTimeSource implements TimeSource {
        private long now;

        FakeTimeSource(long now) {
            this.now = now;
        }

        @Override
        public long nowMillis() {
            return now;
        }

        void advance(long millis) {
            now += millis;
        }
    }
}
