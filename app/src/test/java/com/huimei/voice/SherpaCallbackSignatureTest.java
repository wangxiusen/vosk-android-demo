package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SherpaCallbackSignatureTest {
    @Test
    public void callbackExposesThePrimitiveFloatArrayInvokeMethodRequiredByJni()
            throws Exception {
        Class<?> callbackClass;
        try {
            callbackClass = Class.forName(
                    "com.huimei.voice.tts.OfflineTtsPlayer$StreamingCallback");
        } catch (ClassNotFoundException error) {
            fail("StreamingCallback is required for sherpa JNI compatibility");
            return;
        }

        Method invoke = callbackClass.getDeclaredMethod("invoke", float[].class);
        assertEquals(Integer.class, invoke.getReturnType());
        assertFalse(invoke.isSynthetic());
    }

    @Test
    public void callbackQueuesPcmWithoutOwningAnAudioTrack() throws Exception {
        Class<?> callbackClass = Class.forName(
                "com.huimei.voice.tts.OfflineTtsPlayer$StreamingCallback");
        boolean hasPcmQueue = false;
        boolean hasAudioTrack = false;
        for (Field field : callbackClass.getDeclaredFields()) {
            hasPcmQueue |= field.getType().getName().equals(
                    "com.huimei.voice.tts.PcmChunkQueue");
            hasAudioTrack |= field.getType().getName().equals("android.media.AudioTrack");
        }

        assertTrue("callback must enqueue PCM for the playback worker", hasPcmQueue);
        assertFalse("callback must not block on AudioTrack", hasAudioTrack);
    }
}
