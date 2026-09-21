package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.SpeakerModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class VoiceprintEngineContractTest {

    @Test
    public void bothAdaptersExposeTheSharedEngineContract() throws Exception {
        assertTrue(VoiceprintEngine.class.isAssignableFrom(SherpaVoiceprintEngine.class));
        assertTrue(VoiceprintEngine.class.isAssignableFrom(VoskVoiceprintEngine.class));
        assertEquals(1, SherpaVoiceprintEngine.class.getConstructor(Context.class).getParameterCount());
        assertEquals(1, VoskVoiceprintEngine.class.getConstructor(Context.class).getParameterCount());

        Method extract = VoiceprintEngine.class.getMethod("extract", short[].class, int.class);
        assertEquals(float[].class, extract.getReturnType());
        assertEquals(void.class, VoiceprintEngine.class.getMethod("initialize").getReturnType());
        assertEquals(int.class, VoiceprintEngine.class.getMethod("dimension").getReturnType());
    }

    @Test
    public void packagedSdksExposeRequiredSpeakerApis() throws Exception {
        Class<?> extractor = Class.forName(
                "com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor",
                false,
                getClass().getClassLoader());
        assertEquals("SpeakerEmbeddingExtractor", extractor.getSimpleName());

        Constructor<Recognizer> constructor = Recognizer.class.getConstructor(
                Model.class, float.class, SpeakerModel.class);
        assertEquals(3, constructor.getParameterCount());
        assertEquals(String.class, Recognizer.class.getMethod("getFinalResult").getReturnType());
    }

    @Test
    public void voskUsesTheNativeMinimumSpeakerFrameRequirement() {
        assertEquals(50, VoskVoiceprintEngine.MINIMUM_SPEAKER_FRAMES);
    }
}
