package com.huimei.voice.functiongemma;

import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.CommandCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CommonNaturalCommandParser {
    enum Status {
        MATCHED,
        REJECTED,
        NO_MATCH
    }

    private static final Pattern CHINESE_DURATION = Pattern.compile(
            "(半|[一二两三四五六七八九十百]+|\\d+)(?:个)?(分钟|小时)");
    private static final Pattern ENGLISH_DURATION = Pattern.compile(
            "\\b(half(?: an)?|one|two|three|four|five|six|seven|eight|nine|ten|"
                    + "thirty|forty five|sixty|\\d+)\\s+(minutes?|hours?)\\b");
    private static final Pattern ENGLISH_NEGATION = Pattern.compile(
            "\\b(?:do not|don't|dont|never|not|no)\\b");
    private static final Pattern ENGLISH_POWER_ON = Pattern.compile(
            "\\b(?:turn|switch|power)\\b.*\\bon\\b");
    private static final Pattern ENGLISH_POWER_OFF = Pattern.compile(
            "\\b(?:turn|switch|power)\\b.*\\boff\\b");
    private static final Pattern ENGLISH_START_VERB = Pattern.compile(
            "\\b(?:start|activate|open)\\b");
    private static final Pattern ENGLISH_STOP_VERB = Pattern.compile(
            "\\b(?:shut down|shutdown|stop|deactivate|close)\\b");
    private static final Pattern ENGLISH_DURATION_VERB = Pattern.compile(
            "\\b(?:run|running|work|operate|operating)\\b");

    Result parse(VoiceLanguage language, String input) {
        String text = normalize(language, input);
        if (text.isEmpty()) {
            return Result.noMatch();
        }

        boolean target = language == VoiceLanguage.CHINESE
                ? containsAny(text, "设备", "机器", "制氧机", "氧气机")
                : containsAny(text, "device", "machine", "oxygen concentrator", "concentrator");
        boolean powerOn = language == VoiceLanguage.CHINESE
                ? text.contains("开机")
                        || target && containsAny(text, "启动", "开启", "打开")
                : target && (ENGLISH_POWER_ON.matcher(text).find()
                        || ENGLISH_START_VERB.matcher(text).find());
        boolean powerOff = language == VoiceLanguage.CHINESE
                ? text.contains("关机")
                        || target && containsAny(text, "关闭", "关掉", "停止", "停机")
                : target && (ENGLISH_POWER_OFF.matcher(text).find()
                        || ENGLISH_STOP_VERB.matcher(text).find());
        boolean durationContext = language == VoiceLanguage.CHINESE
                ? containsAny(text, "产气", "制氧", "供氧")
                        || target && containsAny(text, "运行", "工作", "运转")
                : containsAny(
                        text, "gas production", "produce gas", "producing gas",
                        "oxygen production")
                        || target && ENGLISH_DURATION_VERB.matcher(text).find();
        List<Integer> durations = language == VoiceLanguage.CHINESE
                ? chineseDurations(text)
                : englishDurations(text);
        boolean commandLike = powerOn || powerOff || durationContext || !durations.isEmpty();

        if (commandLike && isNegated(language, text)) {
            return Result.rejected("negated_command");
        }
        if (powerOn && powerOff) {
            return Result.rejected("conflicting_command");
        }
        if (durations.size() > 1) {
            return Result.rejected("conflicting_duration");
        }
        if (durations.contains(-1)) {
            return Result.rejected("unsupported_duration");
        }
        if ((powerOn || powerOff) && !durations.isEmpty()) {
            return Result.rejected("conflicting_command");
        }
        if (durationContext && durations.size() == 1) {
            return Result.matched(eventForMinutes(durations.get(0)));
        }
        if (powerOn) {
            return Result.matched(CommandEvent.POWER_ON);
        }
        if (powerOff) {
            return Result.matched(CommandEvent.POWER_OFF);
        }
        return Result.noMatch();
    }

    private static String normalize(VoiceLanguage language, String input) {
        String normalized = CommandCatalog.normalize(input).replace('’', '\'');
        if (language == VoiceLanguage.CHINESE) {
            return normalized.replaceAll("[\\s，。！？、,!.?;；：:—_\\-]+", "");
        }
        return normalized.replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isNegated(VoiceLanguage language, String text) {
        return language == VoiceLanguage.CHINESE
                ? containsAny(text, "不要", "不用", "无需", "别", "禁止", "不准")
                : ENGLISH_NEGATION.matcher(text).find();
    }

    private static List<Integer> chineseDurations(String text) {
        List<Integer> values = new ArrayList<>();
        Matcher matcher = CHINESE_DURATION.matcher(text);
        while (matcher.find()) {
            String number = matcher.group(1);
            String unit = matcher.group(2);
            int minutes = -1;
            if ("小时".equals(unit)) {
                if ("半".equals(number)) {
                    minutes = 30;
                } else if (equalsAny(number, "一", "1")) {
                    minutes = 60;
                } else if (equalsAny(number, "二", "两", "2")) {
                    minutes = 120;
                } else if (equalsAny(number, "八", "8")) {
                    minutes = 480;
                }
            } else if ("三十".equals(number) || "30".equals(number)) {
                minutes = 30;
            } else if ("六十".equals(number) || "60".equals(number)) {
                minutes = 60;
            } else if ("一百二十".equals(number) || "120".equals(number)) {
                minutes = 120;
            } else if ("四百八十".equals(number) || "480".equals(number)) {
                minutes = 480;
            }
            values.add(minutes);
        }
        return values;
    }

    private static List<Integer> englishDurations(String text) {
        List<Integer> values = new ArrayList<>();
        Matcher matcher = ENGLISH_DURATION.matcher(text);
        while (matcher.find()) {
            String number = matcher.group(1);
            String unit = matcher.group(2);
            int minutes = -1;
            if (unit.startsWith("hour")) {
                if (number.startsWith("half")) {
                    minutes = 30;
                } else if ("one".equals(number) || "1".equals(number)) {
                    minutes = 60;
                } else if ("two".equals(number) || "2".equals(number)) {
                    minutes = 120;
                } else if ("eight".equals(number) || "8".equals(number)) {
                    minutes = 480;
                }
            } else if ("thirty".equals(number) || "30".equals(number)) {
                minutes = 30;
            } else if ("sixty".equals(number) || "60".equals(number)) {
                minutes = 60;
            } else if ("120".equals(number)) {
                minutes = 120;
            } else if ("480".equals(number)) {
                minutes = 480;
            }
            values.add(minutes);
        }
        return values;
    }

    private static CommandEvent eventForMinutes(int minutes) {
        switch (minutes) {
            case 30:
                return CommandEvent.GAS_30_MINUTES;
            case 60:
                return CommandEvent.GAS_1_HOUR;
            case 120:
                return CommandEvent.GAS_2_HOURS;
            case 480:
                return CommandEvent.GAS_8_HOURS;
            default:
                throw new IllegalArgumentException("Unsupported duration: " + minutes);
        }
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsAny(String text, String... values) {
        for (String value : values) {
            if (text.equals(value)) {
                return true;
            }
        }
        return false;
    }

    static final class Result {
        private final Status status;
        private final CommandEvent event;
        private final String reason;

        private Result(Status status, CommandEvent event, String reason) {
            this.status = status;
            this.event = event;
            this.reason = reason;
        }

        static Result matched(CommandEvent event) {
            return new Result(Status.MATCHED, event, "local_natural");
        }

        static Result rejected(String reason) {
            return new Result(Status.REJECTED, null, reason);
        }

        static Result noMatch() {
            return new Result(Status.NO_MATCH, null, "no_match");
        }

        Status status() {
            return status;
        }

        Optional<CommandEvent> event() {
            return Optional.ofNullable(event);
        }

        String reason() {
            return reason;
        }
    }
}
