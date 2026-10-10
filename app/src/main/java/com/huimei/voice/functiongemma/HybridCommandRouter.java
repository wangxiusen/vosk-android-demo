package com.huimei.voice.functiongemma;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.CommandMatch;
import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.CommandCatalog;
import com.huimei.voice.recognition.TimeSource;

import java.util.Objects;
import java.util.Optional;

public final class HybridCommandRouter {
    private static final int MAX_INPUT_CODE_POINTS = 200;

    private final FunctionGemmaEngine engine;
    private final TimeSource timeSource;
    private final FunctionGemmaCommandValidator validator =
            new FunctionGemmaCommandValidator();

    public HybridCommandRouter(FunctionGemmaEngine engine, TimeSource timeSource) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    public HybridCommandResult route(VoiceLanguage language, String text) {
        Objects.requireNonNull(language, "language");
        String normalized = CommandCatalog.normalize(text);
        if (normalized.isEmpty()) {
            return HybridCommandResult.rejected("empty_input", null, 0L);
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_INPUT_CODE_POINTS) {
            return HybridCommandResult.rejected("input_too_long", null, 0L);
        }

        Optional<CommandMatch> exact = CommandCatalog.forLanguage(language).find(text);
        if (exact.isPresent() && exact.get().getEvent() != CommandEvent.WAKE_UP) {
            return HybridCommandResult.exact(exact.get().getEvent());
        }

        long startedAt = timeSource.nowMillis();
        try {
            FunctionGemmaInference inference = engine.infer(normalized);
            long elapsed = Math.max(0L, timeSource.nowMillis() - startedAt);
            Optional<CommandEvent> event = validator.validate(inference);
            return event.isPresent()
                    ? HybridCommandResult.modelAccepted(event.get(), inference, elapsed)
                    : HybridCommandResult.rejected("invalid_tool_call", inference, elapsed);
        } catch (Exception error) {
            long elapsed = Math.max(0L, timeSource.nowMillis() - startedAt);
            return HybridCommandResult.rejected("inference_error", null, elapsed);
        }
    }
}
