package com.huimei.voice.functiongemma;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FunctionGemmaToolCall {
    private final String name;
    private final Map<String, Object> arguments;

    public FunctionGemmaToolCall(String name, Map<String, Object> arguments) {
        this.name = Objects.requireNonNull(name, "name");
        this.arguments = arguments == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    public String name() {
        return name;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return name + String.valueOf(arguments);
    }
}
