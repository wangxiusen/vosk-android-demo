package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.VoiceLanguage;

import org.junit.Test;

public final class CommonNaturalCommandParserTest {
    private final CommonNaturalCommandParser parser = new CommonNaturalCommandParser();

    @Test
    public void mapsCommonChineseNaturalCommandsWithoutEnumeratingWholeSentences() {
        assertMatched(VoiceLanguage.CHINESE, "请帮我打开设备", CommandEvent.POWER_ON);
        assertMatched(VoiceLanguage.CHINESE, "启动一下制氧机", CommandEvent.POWER_ON);
        assertMatched(VoiceLanguage.CHINESE, "麻烦开启机器", CommandEvent.POWER_ON);
        assertMatched(VoiceLanguage.CHINESE, "请关闭设备", CommandEvent.POWER_OFF);
        assertMatched(VoiceLanguage.CHINESE, "帮我把机器关掉", CommandEvent.POWER_OFF);
        assertMatched(VoiceLanguage.CHINESE, "停止设备运行", CommandEvent.POWER_OFF);
        assertMatched(VoiceLanguage.CHINESE, "让设备运行半小时", CommandEvent.GAS_30_MINUTES);
        assertMatched(VoiceLanguage.CHINESE, "制氧机工作一小时", CommandEvent.GAS_1_HOUR);
        assertMatched(VoiceLanguage.CHINESE, "让机器运转两小时", CommandEvent.GAS_2_HOURS);
        assertMatched(VoiceLanguage.CHINESE, "请让氧气机工作八小时", CommandEvent.GAS_8_HOURS);
    }

    @Test
    public void mapsCommonEnglishNaturalCommandsWithoutEnumeratingWholeSentences() {
        assertMatched(VoiceLanguage.ENGLISH, "Please turn on the machine", CommandEvent.POWER_ON);
        assertMatched(VoiceLanguage.ENGLISH, "Start the device", CommandEvent.POWER_ON);
        assertMatched(VoiceLanguage.ENGLISH, "Switch on the oxygen concentrator", CommandEvent.POWER_ON);
        assertMatched(VoiceLanguage.ENGLISH, "Please turn off the machine", CommandEvent.POWER_OFF);
        assertMatched(VoiceLanguage.ENGLISH, "Stop the device", CommandEvent.POWER_OFF);
        assertMatched(VoiceLanguage.ENGLISH, "Shut down the oxygen concentrator", CommandEvent.POWER_OFF);
        assertMatched(VoiceLanguage.ENGLISH, "Run the device for half an hour",
                CommandEvent.GAS_30_MINUTES);
        assertMatched(VoiceLanguage.ENGLISH, "Let the machine work for one hour",
                CommandEvent.GAS_1_HOUR);
        assertMatched(VoiceLanguage.ENGLISH, "Operate the oxygen concentrator for two hours",
                CommandEvent.GAS_2_HOURS);
        assertMatched(VoiceLanguage.ENGLISH, "Run the device for eight hours",
                CommandEvent.GAS_8_HOURS);
    }

    @Test
    public void rejectsNegatedConflictingAndUnsupportedCommands() {
        assertRejected(VoiceLanguage.CHINESE, "请不要开机", "negated_command");
        assertRejected(VoiceLanguage.CHINESE, "不要让设备运行半小时", "negated_command");
        assertRejected(VoiceLanguage.CHINESE, "先开机然后关机", "conflicting_command");
        assertRejected(VoiceLanguage.CHINESE, "开机并运行一小时", "conflicting_command");
        assertRejected(VoiceLanguage.CHINESE, "让设备运行三小时", "unsupported_duration");
        assertRejected(VoiceLanguage.CHINESE, "让设备运行一百二十小时",
                "unsupported_duration");
        assertRejected(VoiceLanguage.CHINESE, "让设备运行半小时和一小时",
                "conflicting_duration");
        assertRejected(VoiceLanguage.ENGLISH, "Please do not turn on the machine",
                "negated_command");
        assertRejected(VoiceLanguage.ENGLISH, "Turn the machine on and then off",
                "conflicting_command");
        assertRejected(VoiceLanguage.ENGLISH, "Turn on the machine and run it for one hour",
                "conflicting_command");
        assertRejected(VoiceLanguage.ENGLISH, "Run the device for three hours",
                "unsupported_duration");
        assertRejected(VoiceLanguage.ENGLISH,
                "Run the device for one hour and eight hours", "conflicting_duration");
    }

    @Test
    public void leavesUnrelatedOrAmbiguousTextForTheModel() {
        assertEquals(CommonNaturalCommandParser.Status.NO_MATCH,
                parser.parse(VoiceLanguage.CHINESE, "今天天气怎么样").status());
        assertEquals(CommonNaturalCommandParser.Status.NO_MATCH,
                parser.parse(VoiceLanguage.CHINESE, "打开声音").status());
        assertEquals(CommonNaturalCommandParser.Status.NO_MATCH,
                parser.parse(VoiceLanguage.ENGLISH, "Play some music").status());
        assertEquals(CommonNaturalCommandParser.Status.NO_MATCH,
                parser.parse(VoiceLanguage.ENGLISH, "Restart the machine").status());
    }

    private void assertMatched(
            VoiceLanguage language,
            String text,
            CommandEvent expected) {
        CommonNaturalCommandParser.Result result = parser.parse(language, text);
        assertEquals(CommonNaturalCommandParser.Status.MATCHED, result.status());
        assertEquals(expected, result.event().get());
    }

    private void assertRejected(VoiceLanguage language, String text, String expectedReason) {
        CommonNaturalCommandParser.Result result = parser.parse(language, text);
        assertEquals(CommonNaturalCommandParser.Status.REJECTED, result.status());
        assertEquals(expectedReason, result.reason());
    }
}
