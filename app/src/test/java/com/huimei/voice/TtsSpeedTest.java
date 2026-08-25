package com.huimei.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class TtsSpeedTest {
    @Test
    public void exposesSlowNormalAndFastSpeechRates() throws Exception {
        Class<?> speedClass;
        try {
            speedClass = Class.forName("com.huimei.voice.tts.TtsSpeed");
        } catch (ClassNotFoundException error) {
            fail("TtsSpeed is required");
            return;
        }

        Object[] speeds = (Object[]) speedClass.getMethod("values").invoke(null);
        String[] names = new String[speeds.length];
        float[] rates = new float[speeds.length];
        for (int index = 0; index < speeds.length; index++) {
            names[index] = ((Enum<?>) speeds[index]).name();
            rates[index] = (float) speedClass.getMethod("rate").invoke(speeds[index]);
        }

        assertArrayEquals(new String[]{"SLOW", "NORMAL", "FAST"}, names);
        assertEquals(0.8f, rates[0], 0.001f);
        assertEquals(1.0f, rates[1], 0.001f);
        assertEquals(1.2f, rates[2], 0.001f);
    }
}
