package com.huimei.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;

public final class DemoDestinationTest {
    @Test
    public void exposesVoiceAndLottieDemosInStableOrder() throws Exception {
        Class<?> destinationClass;
        try {
            destinationClass = Class.forName("com.huimei.voice.DemoDestination");
        } catch (ClassNotFoundException error) {
            fail("DemoDestination is required for the launcher list");
            return;
        }

        Object[] destinations = (Object[]) destinationClass.getMethod("values").invoke(null);
        String[] names = new String[destinations.length];
        String[] activityClassNames = new String[destinations.length];
        Method targetActivityClassName = destinationClass.getMethod("targetActivityClassName");

        for (int index = 0; index < destinations.length; index++) {
            names[index] = ((Enum<?>) destinations[index]).name();
            activityClassNames[index] = (String) targetActivityClassName.invoke(destinations[index]);
        }

        assertArrayEquals(
                new String[]{"VOICE_RECOGNITION", "LOTTIE_AVATAR", "VOICE_AVATAR"},
                names);
        assertEquals("com.huimei.voice.VoiceRecognitionActivity", activityClassNames[0]);
        assertEquals("com.huimei.voice.LottieDemoActivity", activityClassNames[1]);
        assertEquals("com.huimei.voice.VoiceAvatarActivity", activityClassNames[2]);
    }
}
