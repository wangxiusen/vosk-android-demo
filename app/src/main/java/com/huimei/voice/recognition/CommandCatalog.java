package com.huimei.voice.recognition;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.CommandMatch;
import com.huimei.voice.model.VoiceLanguage;

import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CommandCatalog {
    private final VoiceLanguage language;
    private final String wakePhrase;
    private final String assetModelPath;
    private final Map<String, CommandMatch> phrases;

    private CommandCatalog(
            VoiceLanguage language,
            String wakePhrase,
            String assetModelPath,
            Map<String, CommandMatch> phrases) {
        this.language = language;
        this.wakePhrase = wakePhrase;
        this.assetModelPath = assetModelPath;
        this.phrases = phrases;
    }

    public static CommandCatalog forLanguage(VoiceLanguage language) {
        Objects.requireNonNull(language, "language");
        return language == VoiceLanguage.CHINESE ? chinese() : english();
    }

    private static CommandCatalog chinese() {
        LinkedHashMap<String, CommandMatch> phrases = new LinkedHashMap<>();
        add(phrases, "潓美医疗", "潓美医疗", CommandEvent.WAKE_UP);
        add(phrases, "惠美医疗", "潓美医疗", CommandEvent.WAKE_UP);
        add(phrases, "半小时产气", "30分钟产气", CommandEvent.GAS_30_MINUTES);
        add(phrases, "三十分钟产气", "30分钟产气", CommandEvent.GAS_30_MINUTES);
        add(phrases, "30分钟产气", "30分钟产气", CommandEvent.GAS_30_MINUTES);
        add(phrases, "一小时产气", "1小时产气", CommandEvent.GAS_1_HOUR);
        add(phrases, "1小时产气", "1小时产气", CommandEvent.GAS_1_HOUR);
        add(phrases, "两小时产气", "2小时产气", CommandEvent.GAS_2_HOURS);
        add(phrases, "2小时产气", "2小时产气", CommandEvent.GAS_2_HOURS);
        add(phrases, "八小时产气", "8小时产气", CommandEvent.GAS_8_HOURS);
        add(phrases, "8小时产气", "8小时产气", CommandEvent.GAS_8_HOURS);
        add(phrases, "开机", "开机", CommandEvent.POWER_ON);
        add(phrases, "关机", "关机", CommandEvent.POWER_OFF);
        return new CommandCatalog(VoiceLanguage.CHINESE, "潓美医疗", "model-cn", phrases);
    }

    private static CommandCatalog english() {
        LinkedHashMap<String, CommandMatch> phrases = new LinkedHashMap<>();
        add(phrases, "hello medical", "Hello Medical", CommandEvent.WAKE_UP);
        add(phrases, "half hour gas production", "30-minute gas production", CommandEvent.GAS_30_MINUTES);
        add(phrases, "thirty minute gas production", "30-minute gas production", CommandEvent.GAS_30_MINUTES);
        add(phrases, "one hour gas production", "1-hour gas production", CommandEvent.GAS_1_HOUR);
        add(phrases, "two hour gas production", "2-hour gas production", CommandEvent.GAS_2_HOURS);
        add(phrases, "two hours gas production", "2-hour gas production", CommandEvent.GAS_2_HOURS);
        add(phrases, "eight hour gas production", "8-hour gas production", CommandEvent.GAS_8_HOURS);
        add(phrases, "eight hours gas production", "8-hour gas production", CommandEvent.GAS_8_HOURS);
        add(phrases, "power on", "Power on", CommandEvent.POWER_ON);
        add(phrases, "power off", "Power off", CommandEvent.POWER_OFF);
        return new CommandCatalog(VoiceLanguage.ENGLISH, "Hello Medical", "model-en-us", phrases);
    }

    private static void add(
            Map<String, CommandMatch> phrases,
            String recognizedPhrase,
            String displayPhrase,
            CommandEvent event) {
        String normalized = normalize(recognizedPhrase);
        phrases.put(normalized, new CommandMatch(normalized, displayPhrase, event));
    }

    public Optional<CommandMatch> find(String text) {
        String normalized = normalize(text);
        if (language == VoiceLanguage.CHINESE) {
            normalized = normalized.replace(" ", "");
        }
        return Optional.ofNullable(phrases.get(normalized));
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public String grammarJson() {
        JSONArray grammar = new JSONArray();
        for (String phrase : phrases.keySet()) {
            grammar.put(phrase);
        }
        grammar.put("[unk]");
        return grammar.toString();
    }

    public VoiceLanguage language() {
        return language;
    }

    public String wakePhrase() {
        return wakePhrase;
    }

    public String assetModelPath() {
        return assetModelPath;
    }
}
