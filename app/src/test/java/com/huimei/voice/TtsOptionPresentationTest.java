package com.huimei.voice;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huimei.voice.tts.TtsLanguage;
import com.huimei.voice.tts.TtsSpeed;

import org.junit.Test;

public final class TtsOptionPresentationTest {
    @Test
    public void languagesProvideLocalizedSampleText() {
        assertTrue(TtsLanguage.CHINESE.sampleText().contains("潓美医疗"));
        assertTrue(TtsLanguage.ENGLISH.sampleText().contains("Huimei Medical"));
    }

    @Test
    public void speedOptionsProvideVisibleLabels() {
        for (TtsSpeed speed : TtsSpeed.values()) {
            assertFalse(speed.displayName().trim().isEmpty());
        }
    }
}
