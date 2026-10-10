package com.huimei.voice.functiongemma;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.ToolCall;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LiteRtFunctionGemmaEngine implements FunctionGemmaEngine {
    private final File cacheDir;
    private Engine engine;

    public LiteRtFunctionGemmaEngine(File cacheDir) {
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir");
    }

    @Override
    public synchronized void initialize(File modelFile) {
        Objects.requireNonNull(modelFile, "modelFile");
        close();
        Engine candidate = new Engine(createEngineConfig(modelFile, cacheDir));
        try {
            candidate.initialize();
            engine = candidate;
        } catch (RuntimeException | Error failure) {
            candidate.close();
            throw failure;
        }
    }

    @Override
    public synchronized FunctionGemmaInference infer(String text) {
        if (engine == null || !engine.isInitialized()) {
            throw new IllegalStateException("FunctionGemma engine is not initialized");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        try (Conversation conversation = engine.createConversation(createConversationConfig())) {
            Message response = conversation.sendMessage(text.trim());
            List<FunctionGemmaToolCall> calls = new ArrayList<>();
            for (ToolCall toolCall : response.getToolCalls()) {
                calls.add(new FunctionGemmaToolCall(
                        toolCall.getName(), toolCall.getArguments()));
            }
            return new FunctionGemmaInference(calls, response.toString());
        }
    }

    @Override
    public synchronized void close() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }

    static EngineConfig createEngineConfig(File modelFile, File cacheDir) {
        return new EngineConfig(
                modelFile.getAbsolutePath(),
                new Backend.CPU(4, null),
                null,
                null,
                1024,
                null,
                cacheDir.getAbsolutePath());
    }

    static ConversationConfig createConversationConfig() {
        return new ConversationConfig(
                null,
                Collections.emptyList(),
                FunctionGemmaToolSchema.toolProviders(),
                new SamplerConfig(1, 1.0, 0.0, 0),
                false,
                Collections.emptyList(),
                Collections.emptyMap(),
                null,
                false,
                64);
    }
}
