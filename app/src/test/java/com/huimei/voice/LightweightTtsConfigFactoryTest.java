package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.huimei.voice.tts.TtsLanguage;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import org.junit.Test;

import java.lang.reflect.Method;

public final class LightweightTtsConfigFactoryTest {
    @Test
    public void buildsCommercialFriendlyChineseAishell3Configuration() throws Exception {
        OfflineTtsConfig config = create(TtsLanguage.CHINESE, "/unused");
        OfflineTtsVitsModelConfig vits = config.getModel().getVits();

        assertEquals("vits-icefall-zh-aishell3/model.onnx", vits.getModel());
        assertEquals("vits-icefall-zh-aishell3/tokens.txt", vits.getTokens());
        assertEquals("vits-icefall-zh-aishell3/lexicon.txt", vits.getLexicon());
        assertEquals(
                "vits-icefall-zh-aishell3/phone.fst,"
                        + "vits-icefall-zh-aishell3/date.fst,"
                        + "vits-icefall-zh-aishell3/number.fst",
                config.getRuleFsts());
        assertCommonConfiguration(config);
    }

    @Test
    public void buildsEnglishPiperLjspeechConfigurationWithCopiedEspeakData()
            throws Exception {
        OfflineTtsConfig config = create(TtsLanguage.ENGLISH, "/files/en-espeak");
        OfflineTtsVitsModelConfig vits = config.getModel().getVits();

        assertEquals(
                "vits-piper-en_US-ljspeech-medium/en_US-ljspeech-medium.onnx",
                vits.getModel());
        assertEquals(
                "vits-piper-en_US-ljspeech-medium/tokens.txt",
                vits.getTokens());
        assertEquals("", vits.getLexicon());
        assertEquals("/files/en-espeak", vits.getDataDir());
        assertEquals("", config.getRuleFsts());
        assertCommonConfiguration(config);
    }

    private static OfflineTtsConfig create(TtsLanguage language, String dataDirectory)
            throws Exception {
        Class<?> factoryClass;
        try {
            factoryClass = Class.forName(
                    "com.huimei.voice.tts.LightweightTtsConfigFactory");
        } catch (ClassNotFoundException error) {
            fail("LightweightTtsConfigFactory is required");
            return null;
        }
        Method create = factoryClass.getMethod(
                "create",
                TtsLanguage.class,
                String.class);
        return (OfflineTtsConfig) create.invoke(null, language, dataDirectory);
    }

    private static void assertCommonConfiguration(OfflineTtsConfig config) {
        assertEquals(4, config.getModel().getNumThreads());
        assertFalse(config.getModel().getDebug());
        assertEquals("cpu", config.getModel().getProvider());
        assertEquals(1, config.getMaxNumSentences());
        assertEquals(0.2f, config.getSilenceScale(), 0.0001f);
    }
}
