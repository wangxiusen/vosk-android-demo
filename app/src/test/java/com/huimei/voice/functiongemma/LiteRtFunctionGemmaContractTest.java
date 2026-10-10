package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.EngineConfig;

import org.junit.Test;

import java.io.File;

public final class LiteRtFunctionGemmaContractTest {
    @Test
    public void adapterImplementsTheEngineLifecycleWithoutLoadingAModel() throws Exception {
        assertTrue(FunctionGemmaEngine.class.isAssignableFrom(LiteRtFunctionGemmaEngine.class));
        assertTrue(AutoCloseable.class.isAssignableFrom(LiteRtFunctionGemmaEngine.class));
        LiteRtFunctionGemmaEngine.class.getDeclaredMethod(
                "createEngineConfig", File.class, File.class);
        LiteRtFunctionGemmaEngine.class.getDeclaredMethod("createConversationConfig");
    }

    @Test
    public void usesTheBoundedCpuOnlyEngineConfiguration() {
        File model = new File("model.litertlm");
        File cache = new File("cache");

        EngineConfig config = LiteRtFunctionGemmaEngine.createEngineConfig(model, cache);

        assertEquals(model.getAbsolutePath(), config.getModelPath());
        assertTrue(config.getBackend() instanceof Backend.CPU);
        assertEquals(Integer.valueOf(4), ((Backend.CPU) config.getBackend()).getThreadCount());
        assertEquals(Integer.valueOf(1024), config.getMaxNumTokens());
        assertEquals(cache.getAbsolutePath(), config.getCacheDir());
    }

    @Test
    public void disablesToolExecutionAndUsesDeterministicShortOutput() {
        ConversationConfig config = LiteRtFunctionGemmaEngine.createConversationConfig();

        assertEquals(3, config.getTools().size());
        assertFalse(config.getAutomaticToolCalling());
        assertEquals(0.0, config.getSamplerConfig().getTemperature(), 0.0);
        assertEquals(Integer.valueOf(64), config.getMaxOutputToken());
    }
}
