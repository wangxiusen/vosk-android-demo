package com.huimei.voice.functiongemma

import com.huimei.voice.model.CommandEvent
import java.util.Optional

class FunctionGemmaCommandValidator {
    fun validate(inference: FunctionGemmaInference?): Optional<CommandEvent> {
        if (inference == null || inference.toolCalls().size != 1) return Optional.empty()
        val call = inference.toolCalls()[0]
        val arguments = call.arguments() ?: return Optional.empty()
        return when (call.name()) {
            "power_on" -> if (arguments.isEmpty()) Optional.of(CommandEvent.POWER_ON) else Optional.empty()
            "power_off" -> if (arguments.isEmpty()) Optional.of(CommandEvent.POWER_OFF) else Optional.empty()
            "set_gas_duration" -> validateDuration(arguments)
            else -> Optional.empty()
        }
    }

    private fun validateDuration(arguments: Map<String, Any?>): Optional<CommandEvent> {
        if (arguments.size != 1 || !arguments.containsKey("duration_minutes")) return Optional.empty()
        val number = arguments["duration_minutes"] as? Number ?: return Optional.empty()
        val integral = number.toLong()
        if (number.toDouble() != integral.toDouble() || integral < Int.MIN_VALUE || integral > Int.MAX_VALUE) {
            return Optional.empty()
        }
        return when (integral.toInt()) {
            30 -> Optional.of(CommandEvent.GAS_30_MINUTES)
            60 -> Optional.of(CommandEvent.GAS_1_HOUR)
            120 -> Optional.of(CommandEvent.GAS_2_HOURS)
            480 -> Optional.of(CommandEvent.GAS_8_HOURS)
            else -> Optional.empty()
        }
    }
}
