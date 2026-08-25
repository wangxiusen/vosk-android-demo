package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

public final class TtsVoiceCatalogTest {
    @Test
    public void exposesFemaleVoicesForEachSupportedLanguage() throws Exception {
        Class<?> languageClass;
        Class<?> catalogClass;
        try {
            languageClass = Class.forName("com.huimei.voice.tts.TtsLanguage");
            catalogClass = Class.forName("com.huimei.voice.tts.TtsVoiceCatalog");
        } catch (ClassNotFoundException error) {
            fail("TTS language and voice catalog are required");
            return;
        }

        Method voicesFor = catalogClass.getMethod("voicesFor", languageClass);
        assertVoices(
                languageClass,
                voicesFor,
                "CHINESE",
                new int[]{4, 122},
                "中文女声");
        assertVoices(
                languageClass,
                voicesFor,
                "ENGLISH",
                new int[]{0},
                "美式女声");
    }

    private static void assertVoices(
            Class<?> languageClass,
            Method voicesFor,
            String languageName,
            int[] expectedIds,
            String expectedLabel) throws Exception {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object language = Enum.valueOf((Class<? extends Enum>) languageClass, languageName);
        @SuppressWarnings("unchecked")
        List<Object> voices = (List<Object>) voicesFor.invoke(null, language);
        assertEquals(expectedIds.length, voices.size());
        for (int index = 0; index < expectedIds.length; index++) {
            Object voice = voices.get(index);
            assertEquals(
                    expectedIds[index],
                    voice.getClass().getMethod("speakerId").invoke(voice));
            String displayName = (String) voice.getClass()
                    .getMethod("displayName")
                    .invoke(voice);
            assertFalse(displayName.trim().isEmpty());
            assertTrue(displayName.contains(expectedLabel));
        }
    }
}
