package com.huimei.voice.functiongemma

import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.CommandCatalog
import com.huimei.voice.recognition.TimeSource
import kotlin.math.max

class HybridCommandRouter(
    private val engine: FunctionGemmaEngine,
    private val timeSource: TimeSource
) {
    private val naturalParser = CommonNaturalCommandParser()
    private val validator = FunctionGemmaCommandValidator()

    fun route(language: VoiceLanguage, text: String): HybridCommandResult {
        val normalized = CommandCatalog.normalize(text)
        if (normalized.isEmpty()) return HybridCommandResult.rejected("empty_input", null, 0L)
        if (normalized.codePointCount(0, normalized.length) > 200) {
            return HybridCommandResult.rejected("input_too_long", null, 0L)
        }

        val exact = CommandCatalog.forLanguage(language).find(text)
        if (exact.isPresent && exact.get().event != CommandEvent.WAKE_UP) {
            return HybridCommandResult.exact(exact.get().event)
        }

        val natural = naturalParser.parse(language, text)
        when (natural.status()) {
            CommonNaturalCommandParser.Status.MATCHED ->
                return HybridCommandResult.localNatural(natural.event().get())
            CommonNaturalCommandParser.Status.REJECTED ->
                return HybridCommandResult.rejected(natural.reason(), null, 0L)
            else -> Unit
        }

        val startedAt = timeSource.nowMillis()
        return try {
            val inference = engine.infer(normalized)
            val elapsed = max(0L, timeSource.nowMillis() - startedAt)
            val event = validator.validate(inference)
            if (event.isPresent) {
                HybridCommandResult.modelAccepted(event.get(), inference, elapsed)
            } else {
                HybridCommandResult.rejected("invalid_tool_call", inference, elapsed)
            }
        } catch (_: Exception) {
            HybridCommandResult.rejected("inference_error", null, max(0L, timeSource.nowMillis() - startedAt))
        }
    }
}
