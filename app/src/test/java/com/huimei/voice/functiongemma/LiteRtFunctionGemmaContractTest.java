package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.EngineConfig;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public final class LiteRtFunctionGemmaContractTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        assertTrue(config.getSystemInstruction().toString().contains(
                "You are a model that can do function calling with the following functions"));
        assertEquals(0.0, config.getSamplerConfig().getTemperature(), 0.0);
        assertEquals(Integer.valueOf(64), config.getMaxOutputToken());
    }

    @Test
    public void failedInitializationKeepsTheOriginalFailureWhenCleanupAlsoFails() {
        RuntimeException initializeFailure = new RuntimeException("real initialize failure");
        RuntimeException closeFailure = new RuntimeException("not initialized");

        LiteRtFunctionGemmaEngine.closeAfterFailedInitialization(
                () -> {
                    throw closeFailure;
                },
                initializeFailure);

        assertEquals(1, initializeFailure.getSuppressed().length);
        assertSame(closeFailure, initializeFailure.getSuppressed()[0]);
    }

    @Test
    public void createsTheNativeRuntimeCacheDirectoryBeforeInitialization() throws Exception {
        File cache = new File(temporaryFolder.getRoot(), "nested/functiongemma");

        LiteRtFunctionGemmaEngine.ensureCacheDirectory(cache);

        assertTrue(cache.isDirectory());
    }
}
