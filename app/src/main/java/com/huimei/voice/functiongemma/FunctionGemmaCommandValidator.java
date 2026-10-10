package com.huimei.voice.functiongemma;

import com.huimei.voice.model.CommandEvent;

import java.util.Map;
import java.util.Optional;

public final class FunctionGemmaCommandValidator {
    public Optional<CommandEvent> validate(FunctionGemmaInference inference) {
        if (inference == null || inference.toolCalls().size() != 1) {
            return Optional.empty();
        }
        FunctionGemmaToolCall call = inference.toolCalls().get(0);
        Map<String, Object> arguments = call.arguments();
        if (arguments == null) {
            return Optional.empty();
        }
        switch (call.name()) {
            case "power_on":
                return arguments.isEmpty()
                        ? Optional.of(CommandEvent.POWER_ON)
                        : Optional.empty();
            case "power_off":
                return arguments.isEmpty()
                        ? Optional.of(CommandEvent.POWER_OFF)
                        : Optional.empty();
            case "set_gas_duration":
                return validateDuration(arguments);
            default:
                return Optional.empty();
        }
    }

    private static Optional<CommandEvent> validateDuration(Map<String, Object> arguments) {
        if (arguments.size() != 1 || !arguments.containsKey("duration_minutes")) {
            return Optional.empty();
        }
        Object value = arguments.get("duration_minutes");
        if (!(value instanceof Number)) {
            return Optional.empty();
        }
        Number number = (Number) value;
        long integral = number.longValue();
        if (number.doubleValue() != (double) integral) {
            return Optional.empty();
        }
        if (integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        switch ((int) integral) {
            case 30:
                return Optional.of(CommandEvent.GAS_30_MINUTES);
            case 60:
                return Optional.of(CommandEvent.GAS_1_HOUR);
            case 120:
                return Optional.of(CommandEvent.GAS_2_HOURS);
            case 480:
                return Optional.of(CommandEvent.GAS_8_HOURS);
            default:
                return Optional.empty();
        }
    }
}
