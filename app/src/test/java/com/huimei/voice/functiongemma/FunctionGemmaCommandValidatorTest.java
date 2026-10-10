package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.huimei.voice.model.CommandEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FunctionGemmaCommandValidatorTest {
    private final FunctionGemmaCommandValidator validator =
            new FunctionGemmaCommandValidator();

    @Test
    public void mapsOnlyTheWhitelistedPowerTools() {
        assertEvent(CommandEvent.POWER_ON, call("power_on", Collections.emptyMap()));
        assertEvent(CommandEvent.POWER_OFF, call("power_off", Collections.emptyMap()));
    }

    @Test
    public void mapsOnlyTheFourWhitelistedDurations() {
        assertEvent(CommandEvent.GAS_30_MINUTES, duration(30));
        assertEvent(CommandEvent.GAS_1_HOUR, duration(60L));
        assertEvent(CommandEvent.GAS_2_HOURS, duration(120.0d));
        assertEvent(CommandEvent.GAS_8_HOURS, duration(480));
    }

    @Test
    public void rejectsAnythingExceptExactlyOneToolCall() {
        assertRejected(new FunctionGemmaInference(Collections.emptyList(), "none"));
        assertRejected(new FunctionGemmaInference(Arrays.asList(
                call("power_on", Collections.emptyMap()),
                call("power_off", Collections.emptyMap())), "two"));
    }

    @Test
    public void rejectsUnknownOrOverParameterizedPowerTools() {
        assertRejected(inference(call("restart", Collections.emptyMap())));
        assertRejected(inference(call("power_on", singleton("force", true))));
        assertRejected(inference(call("power_off", singleton("reason", "test"))));
        assertRejected(inference(call("power_on", null)));
    }

    @Test
    public void rejectsMalformedOrUnsupportedDurations() {
        assertRejected(inference(call("set_gas_duration", Collections.emptyMap())));
        assertRejected(inference(call("set_gas_duration", singleton("duration_minutes", "30"))));
        assertRejected(inference(call("set_gas_duration", singleton("duration_minutes", 30.5d))));
        assertRejected(inference(call("set_gas_duration", singleton("duration_minutes", -30))));
        assertRejected(inference(call("set_gas_duration", singleton("duration_minutes", 45))));
        assertRejected(inference(call(
                "set_gas_duration", singleton("duration_minutes", 4_294_967_326L))));
        assertRejected(inference(call("set_gas_duration", null)));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("duration_minutes", 30);
        extra.put("force", true);
        assertRejected(inference(call("set_gas_duration", extra)));
    }

    private void assertEvent(CommandEvent expected, FunctionGemmaToolCall call) {
        Optional<CommandEvent> actual = validator.validate(inference(call));
        assertEquals(Optional.of(expected), actual);
    }

    private void assertRejected(FunctionGemmaInference inference) {
        assertFalse(validator.validate(inference).isPresent());
    }

    private static FunctionGemmaToolCall duration(Number minutes) {
        return call("set_gas_duration", singleton("duration_minutes", minutes));
    }

    private static FunctionGemmaToolCall call(String name, Map<String, Object> arguments) {
        return new FunctionGemmaToolCall(name, arguments);
    }

    private static FunctionGemmaInference inference(FunctionGemmaToolCall call) {
        return new FunctionGemmaInference(Collections.singletonList(call), call.toString());
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }
}
