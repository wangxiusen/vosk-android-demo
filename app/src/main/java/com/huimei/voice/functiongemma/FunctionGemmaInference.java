package com.huimei.voice.functiongemma;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FunctionGemmaInference {
    private final List<FunctionGemmaToolCall> toolCalls;
    private final String rawOutput;

    public FunctionGemmaInference(List<FunctionGemmaToolCall> toolCalls, String rawOutput) {
        this.toolCalls = toolCalls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.rawOutput = rawOutput == null ? "" : rawOutput;
    }

    public List<FunctionGemmaToolCall> toolCalls() {
        return toolCalls;
    }

    public String rawOutput() {
        return rawOutput;
    }
}
