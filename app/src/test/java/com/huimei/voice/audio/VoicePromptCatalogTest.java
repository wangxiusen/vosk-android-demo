package com.huimei.voice.audio;

import static org.junit.Assert.assertEquals;

import com.huimei.voice.R;
import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.VoiceLanguage;

import org.junit.Test;

public final class VoicePromptCatalogTest {
    @Test
    public void chineseEventsUseChinesePromptResources() {
        assertEquals(
                R.raw.zh_wake_up,
                VoicePromptCatalog.rawResourceFor(VoiceLanguage.CHINESE, CommandEvent.WAKE_UP));
        assertEquals(
                R.raw.zh_gas_1_hour,
                VoicePromptCatalog.rawResourceFor(VoiceLanguage.CHINESE, CommandEvent.GAS_1_HOUR));
        assertEquals(
                R.raw.zh_gas_8_hours,
                VoicePromptCatalog.rawResourceFor(VoiceLanguage.CHINESE, CommandEvent.GAS_8_HOURS));
    }

    @Test
    public void englishEventsUseEnglishPromptResources() {
        assertEquals(
                R.raw.en_wake_up,
                VoicePromptCatalog.rawResourceFor(VoiceLanguage.ENGLISH, CommandEvent.WAKE_UP));
        assertEquals(
                R.raw.en_gas_1_hour,
                VoicePromptCatalog.rawResourceFor(VoiceLanguage.ENGLISH, CommandEvent.GAS_1_HOUR));
        assertEquals(
                R.raw.en_gas_8_hours,
                VoicePromptCatalog.rawResourceFor(VoiceLanguage.ENGLISH, CommandEvent.GAS_8_HOURS));
    }

    @Test
    public void eventsWithoutProvidedAudioRemainSilent() {
        CommandEvent[] silentEvents = {
                CommandEvent.GAS_30_MINUTES,
                CommandEvent.GAS_2_HOURS,
                CommandEvent.POWER_ON,
                CommandEvent.POWER_OFF
        };

        for (VoiceLanguage language : VoiceLanguage.values()) {
            for (CommandEvent event : silentEvents) {
                assertEquals(0, VoicePromptCatalog.rawResourceFor(language, event));
            }
        }
    }
}
