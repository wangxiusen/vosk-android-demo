package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;

public final class AudioEnergyMouthMapperTest {
    @Test
    public void mapsPcmEnergyToFourMouthShapes() throws Exception {
        Class<?> mapperClass;
        try {
            mapperClass = Class.forName("com.huimei.voice.avatar.AudioEnergyMouthMapper");
        } catch (ClassNotFoundException error) {
            fail("AudioEnergyMouthMapper is required");
            return;
        }

        Method shapeFor = mapperClass.getMethod(
                "shapeFor", float[].class, int.class, int.class);
        assertShape(shapeFor, 0.0f, "CLOSED");
        assertShape(shapeFor, 0.02f, "SMALL");
        assertShape(shapeFor, 0.06f, "MEDIUM");
        assertShape(shapeFor, 0.15f, "OPEN");
    }

    private static void assertShape(Method shapeFor, float sample, String expected)
            throws Exception {
        float[] samples = new float[480];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = sample;
        }
        Object shape = shapeFor.invoke(null, samples, 0, samples.length);
        assertEquals(expected, ((Enum<?>) shape).name());
    }
}
