package com.huimei.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class TtsPauseTest {
    @Test
    public void exposesShortNaturalAndLongPunctuationPauses() throws Exception {
        Class<?> pauseClass;
        try {
            pauseClass = Class.forName("com.huimei.voice.tts.TtsPause");
        } catch (ClassNotFoundException error) {
            fail("TtsPause is required");
            return;
        }

        Object[] pauses = (Object[]) pauseClass.getMethod("values").invoke(null);
        String[] names = new String[pauses.length];
        float[] scales = new float[pauses.length];
        for (int index = 0; index < pauses.length; index++) {
            names[index] = ((Enum<?>) pauses[index]).name();
            scales[index] = (float) pauseClass.getMethod("silenceScale").invoke(pauses[index]);
        }

        assertArrayEquals(new String[]{"SHORT", "NATURAL", "LONG"}, names);
        assertEquals(0.5f, scales[0], 0.001f);
        assertEquals(1.0f, scales[1], 0.001f);
        assertEquals(1.5f, scales[2], 0.001f);
    }
}
