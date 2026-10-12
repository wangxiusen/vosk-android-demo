package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.TimeSource;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HybridCommandRouterTest {
    @Test
    public void exactChineseAndEnglishCommandsBypassTheModel() {
        MutableTimeSource clock = new MutableTimeSource();
        CountingEngine engine = new CountingEngine(clock, accepted("power_on"));
        HybridCommandRouter router = new HybridCommandRouter(engine, clock);

        HybridCommandResult chinese = router.route(VoiceLanguage.CHINESE, "开机");
        HybridCommandResult english = router.route(VoiceLanguage.ENGLISH, "  POWER   OFF ");

        assertEquals(HybridCommandResult.Route.EXACT, chinese.route());
        assertEquals(CommandEvent.POWER_ON, chinese.event().get());
        assertEquals(HybridCommandResult.Route.EXACT, english.route());
        assertEquals(CommandEvent.POWER_OFF, english.event().get());
        assertEquals(0, engine.inferCount);
    }

    @Test
    public void commonNaturalCommandsBypassTheModelAndUnsafeCommandsAreRejected() {
        MutableTimeSource clock = new MutableTimeSource();
        CountingEngine engine = new CountingEngine(clock, accepted("power_off"));
        HybridCommandRouter router = new HybridCommandRouter(engine, clock);

        HybridCommandResult chinese = router.route(
                VoiceLanguage.CHINESE, "请帮我打开设备");
        HybridCommandResult english = router.route(
                VoiceLanguage.ENGLISH, "Run the device for two hours");
        HybridCommandResult unsafe = router.route(
                VoiceLanguage.CHINESE, "请不要开机");

        assertEquals(HybridCommandResult.Route.LOCAL_NATURAL, chinese.route());
        assertEquals(CommandEvent.POWER_ON, chinese.event().get());
        assertEquals(HybridCommandResult.Route.LOCAL_NATURAL, english.route());
        assertEquals(CommandEvent.GAS_2_HOURS, english.event().get());
        assertEquals(HybridCommandResult.Route.REJECTED, unsafe.route());
        assertEquals("negated_command", unsafe.reason());
        assertEquals(0, engine.inferCount);
    }

    @Test
    public void unmatchedNaturalLanguageUsesTheModelOnceAndReportsElapsedTime() {
        MutableTimeSource clock = new MutableTimeSource();
        CountingEngine engine = new CountingEngine(clock, accepted("power_on"));
        HybridCommandRouter router = new HybridCommandRouter(engine, clock);

        HybridCommandResult result = router.route(VoiceLanguage.CHINESE, "请按照我的安排处理");

        assertEquals(HybridCommandResult.Route.MODEL_ACCEPTED, result.route());
        assertEquals(CommandEvent.POWER_ON, result.event().get());
        assertEquals(1, engine.inferCount);
        assertEquals(7L, result.elapsedMillis());
        assertTrue(result.inference().isPresent());
    }

    @Test
    public void rejectsEmptyAndOversizedInputsWithoutCallingTheModel() {
        MutableTimeSource clock = new MutableTimeSource();
        CountingEngine engine = new CountingEngine(clock, accepted("power_on"));
        HybridCommandRouter router = new HybridCommandRouter(engine, clock);

        assertEquals(HybridCommandResult.Route.REJECTED,
                router.route(VoiceLanguage.CHINESE, "  ").route());
        assertEquals(HybridCommandResult.Route.REJECTED,
                router.route(VoiceLanguage.ENGLISH, repeatCodePoint("好", 201)).route());
        assertEquals(0, engine.inferCount);
    }

    @Test
    public void rejectsEngineErrorsAndInvalidToolCallsWithoutRetrying() {
        MutableTimeSource clock = new MutableTimeSource();
        CountingEngine failing = new CountingEngine(clock, null);
        failing.failure = new IllegalStateException("broken");
        HybridCommandResult failed = new HybridCommandRouter(failing, clock)
                .route(VoiceLanguage.ENGLISH, "please turn it on");
        assertEquals(HybridCommandResult.Route.REJECTED, failed.route());
        assertEquals(1, failing.inferCount);
        assertFalse(failed.event().isPresent());

        assertRejected(Collections.emptyList());
        assertRejected(Arrays.asList(
                new FunctionGemmaToolCall("power_on", Collections.emptyMap()),
                new FunctionGemmaToolCall("power_off", Collections.emptyMap())));
        assertRejected(Collections.singletonList(
                new FunctionGemmaToolCall("unknown", Collections.emptyMap())));
    }

    private static void assertRejected(java.util.List<FunctionGemmaToolCall> calls) {
        MutableTimeSource clock = new MutableTimeSource();
        CountingEngine engine = new CountingEngine(
                clock, new FunctionGemmaInference(calls, calls.toString()));
        HybridCommandResult result = new HybridCommandRouter(engine, clock)
                .route(VoiceLanguage.ENGLISH, "some natural request");
        assertEquals(HybridCommandResult.Route.REJECTED, result.route());
        assertEquals(1, engine.inferCount);
    }

    private static FunctionGemmaInference accepted(String name) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        return new FunctionGemmaInference(Collections.singletonList(
                new FunctionGemmaToolCall(name, arguments)), name);
    }

    private static String repeatCodePoint(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static final class MutableTimeSource implements TimeSource {
        long now;

        @Override
        public long nowMillis() {
            return now;
        }
    }

    private static final class CountingEngine implements FunctionGemmaEngine {
        final MutableTimeSource clock;
        final FunctionGemmaInference response;
        RuntimeException failure;
        int inferCount;

        CountingEngine(MutableTimeSource clock, FunctionGemmaInference response) {
            this.clock = clock;
            this.response = response;
        }

        @Override
        public void initialize(File modelFile) {
        }

        @Override
        public FunctionGemmaInference infer(String text) {
            inferCount++;
            clock.now += 7L;
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public void close() {
        }
    }
}
