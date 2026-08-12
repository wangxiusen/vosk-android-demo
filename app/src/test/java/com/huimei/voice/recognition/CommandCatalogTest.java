package com.huimei.voice.recognition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.CommandMatch;
import com.huimei.voice.model.VoiceLanguage;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommandCatalogTest {

    @Test
    public void chinesePhrasesMapToExpectedEvents() {
        Map<String, CommandEvent> expected = new LinkedHashMap<>();
        expected.put("潓美医疗", CommandEvent.WAKE_UP);
        expected.put("惠美医疗", CommandEvent.WAKE_UP);
        expected.put("半小时产气", CommandEvent.GAS_30_MINUTES);
        expected.put("三十分钟产气", CommandEvent.GAS_30_MINUTES);
        expected.put("30分钟产气", CommandEvent.GAS_30_MINUTES);
        expected.put("一小时产气", CommandEvent.GAS_1_HOUR);
        expected.put("1小时产气", CommandEvent.GAS_1_HOUR);
        expected.put("两小时产气", CommandEvent.GAS_2_HOURS);
        expected.put("2小时产气", CommandEvent.GAS_2_HOURS);
        expected.put("八小时产气", CommandEvent.GAS_8_HOURS);
        expected.put("8小时产气", CommandEvent.GAS_8_HOURS);
        expected.put("开机", CommandEvent.POWER_ON);
        expected.put("关机", CommandEvent.POWER_OFF);

        assertMappings(CommandCatalog.forLanguage(VoiceLanguage.CHINESE), expected);
    }

    @Test
    public void englishPhrasesMapToExpectedEvents() {
        Map<String, CommandEvent> expected = new LinkedHashMap<>();
        expected.put("hello medical", CommandEvent.WAKE_UP);
        expected.put("half hour gas production", CommandEvent.GAS_30_MINUTES);
        expected.put("thirty minute gas production", CommandEvent.GAS_30_MINUTES);
        expected.put("one hour gas production", CommandEvent.GAS_1_HOUR);
        expected.put("two hour gas production", CommandEvent.GAS_2_HOURS);
        expected.put("two hours gas production", CommandEvent.GAS_2_HOURS);
        expected.put("eight hour gas production", CommandEvent.GAS_8_HOURS);
        expected.put("eight hours gas production", CommandEvent.GAS_8_HOURS);
        expected.put("power on", CommandEvent.POWER_ON);
        expected.put("power off", CommandEvent.POWER_OFF);

        assertMappings(CommandCatalog.forLanguage(VoiceLanguage.ENGLISH), expected);
    }

    @Test
    public void normalizesEnglishCaseAndWhitespace() {
        CommandCatalog catalog = CommandCatalog.forLanguage(VoiceLanguage.ENGLISH);

        CommandMatch match = catalog.find("  Hello   MEDICAL  ").orElseThrow(AssertionError::new);

        assertEquals(CommandEvent.WAKE_UP, match.getEvent());
        assertEquals("hello medical", match.getRecognizedPhrase());
        assertEquals("Hello Medical", match.getDisplayPhrase());
    }

    @Test
    public void returnsEmptyForUnknownPhrase() {
        CommandCatalog catalog = CommandCatalog.forLanguage(VoiceLanguage.CHINESE);

        assertFalse(catalog.find("启动").isPresent());
        assertFalse(catalog.find(null).isPresent());
    }

    @Test
    public void exposesLanguageMetadataAndRestrictedGrammar() {
        CommandCatalog chinese = CommandCatalog.forLanguage(VoiceLanguage.CHINESE);
        CommandCatalog english = CommandCatalog.forLanguage(VoiceLanguage.ENGLISH);

        assertEquals("潓美医疗", chinese.wakePhrase());
        assertEquals("model-cn", chinese.assetModelPath());
        assertEquals("Hello Medical", english.wakePhrase());
        assertEquals("model-en-us", english.assetModelPath());
        assertTrue(chinese.grammarJson().contains("\"惠美 医疗\""));
        assertTrue(chinese.grammarJson().contains("\"半小时 产 气\""));
        assertFalse(chinese.grammarJson().contains("\"潓美医疗\""));
        assertTrue(chinese.grammarJson().contains("\"[unk]\""));
        assertTrue(english.grammarJson().contains("\"hello medical\""));
    }

    private static void assertMappings(CommandCatalog catalog, Map<String, CommandEvent> expected) {
        for (Map.Entry<String, CommandEvent> entry : expected.entrySet()) {
            CommandMatch match = catalog.find(entry.getKey()).orElseThrow(
                    () -> new AssertionError("Missing phrase: " + entry.getKey()));
            assertEquals("Wrong event for " + entry.getKey(), entry.getValue(), match.getEvent());
        }
    }
}
