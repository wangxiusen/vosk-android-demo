package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.huimei.voice.model.VoiceLanguage;

import org.junit.Test;
import org.vosk.Model;
import org.vosk.Recognizer;

public final class HybridVoiceControllerContractTest {
    @Test
    public void controllerExposesTheLifecycleNeededByTheActivity() throws Exception {
        assertTrue(AutoCloseable.class.isAssignableFrom(HybridVoiceCommandController.class));
        assertEquals(2, HybridVoiceCommandController.class.getConstructor(
                Context.class, HybridVoiceCommandListener.class).getParameterCount());
        assertEquals(void.class, HybridVoiceCommandController.class
                .getMethod("start", VoiceLanguage.class).getReturnType());
        assertEquals(void.class, HybridVoiceCommandController.class
                .getMethod("switchLanguage", VoiceLanguage.class).getReturnType());
        assertEquals(void.class, HybridVoiceCommandController.class
                .getMethod("completeInference", long.class).getReturnType());
        assertEquals(void.class, HybridVoiceCommandController.class
                .getMethod("stop").getReturnType());
    }

    @Test
    public void packagedVoskSupportsGrammarAndFreeDictationRecognizers() throws Exception {
        assertEquals(3, Recognizer.class.getConstructor(
                Model.class, float.class, String.class).getParameterCount());
        assertEquals(2, Recognizer.class.getConstructor(
                Model.class, float.class).getParameterCount());
    }
}
