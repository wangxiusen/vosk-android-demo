package com.huimei.voice.functiongemma

import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.CommandCatalog
import java.util.Optional

class CommonNaturalCommandParser {
    enum class Status { MATCHED, REJECTED, NO_MATCH }

    class Result private constructor(
        private val status: Status,
        private val event: CommandEvent?,
        private val reason: String
    ) {
        fun status(): Status = status
        fun event(): Optional<CommandEvent> = Optional.ofNullable(event)
        fun reason(): String = reason

        companion object {
            fun matched(event: CommandEvent) = Result(Status.MATCHED, event, "local_natural")
            fun rejected(reason: String) = Result(Status.REJECTED, null, reason)
            fun noMatch() = Result(Status.NO_MATCH, null, "no_match")
        }
    }

    fun parse(language: VoiceLanguage, input: String): Result {
        val text = normalize(language, input)
        if (text.isEmpty()) return Result.noMatch()

        val target = if (language == VoiceLanguage.CHINESE) {
            containsAny(text, "设备", "机器", "制氧机", "氧气机")
        } else {
            containsAny(text, "device", "machine", "oxygen concentrator", "concentrator")
        }
        val powerOn = if (language == VoiceLanguage.CHINESE) {
            text.contains("开机") || target && containsAny(text, "启动", "开启", "打开")
        } else {
            target && (ENGLISH_POWER_ON.containsMatchIn(text) || ENGLISH_START_VERB.containsMatchIn(text))
        }
        val powerOff = if (language == VoiceLanguage.CHINESE) {
            text.contains("关机") || target && containsAny(text, "关闭", "关掉", "停止", "停机")
        } else {
            target && (ENGLISH_POWER_OFF.containsMatchIn(text) || ENGLISH_STOP_VERB.containsMatchIn(text))
        }
        val durationContext = if (language == VoiceLanguage.CHINESE) {
            containsAny(text, "产气", "制氧", "供氧") || target && containsAny(text, "运行", "工作", "运转")
        } else {
            containsAny(text, "gas production", "produce gas", "producing gas", "oxygen production") ||
                target && ENGLISH_DURATION_VERB.containsMatchIn(text)
        }
        val durations = if (language == VoiceLanguage.CHINESE) chineseDurations(text) else englishDurations(text)
        val commandLike = powerOn || powerOff || durationContext || durations.isNotEmpty()

        if (commandLike && isNegated(language, text)) return Result.rejected("negated_command")
        if (powerOn && powerOff) return Result.rejected("conflicting_command")
        if (durations.size > 1) return Result.rejected("conflicting_duration")
        if (-1 in durations) return Result.rejected("unsupported_duration")
        if ((powerOn || powerOff) && durations.isNotEmpty()) return Result.rejected("conflicting_command")
        if (durationContext && durations.size == 1) return Result.matched(eventForMinutes(durations[0]))
        if (powerOn) return Result.matched(CommandEvent.POWER_ON)
        if (powerOff) return Result.matched(CommandEvent.POWER_OFF)
        return Result.noMatch()
    }

    private fun normalize(language: VoiceLanguage, input: String): String {
        val normalized = CommandCatalog.normalize(input).replace('’', '\'')
        return if (language == VoiceLanguage.CHINESE) {
            normalized.replace(Regex("[\\s，。！？、,!.?;；：:—_\\-]+"), "")
        } else {
            normalized.replace('-', ' ').replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun isNegated(language: VoiceLanguage, text: String): Boolean =
        if (language == VoiceLanguage.CHINESE) {
            containsAny(text, "不要", "不用", "无需", "别", "禁止", "不准")
        } else {
            ENGLISH_NEGATION.containsMatchIn(text)
        }

    private fun chineseDurations(text: String): List<Int> = CHINESE_DURATION.findAll(text).map { match ->
        val number = match.groupValues[1]
        val unit = match.groupValues[2]
        if (unit == "小时") {
            when (number) {
                "半" -> 30
                "一", "1" -> 60
                "二", "两", "2" -> 120
                "八", "8" -> 480
                else -> -1
            }
        } else {
            when (number) {
                "三十", "30" -> 30
                "六十", "60" -> 60
                "一百二十", "120" -> 120
                "四百八十", "480" -> 480
                else -> -1
            }
        }
    }.toList()

    private fun englishDurations(text: String): List<Int> = ENGLISH_DURATION.findAll(text).map { match ->
        val number = match.groupValues[1]
        val unit = match.groupValues[2]
        if (unit.startsWith("hour")) {
            when {
                number.startsWith("half") -> 30
                number == "one" || number == "1" -> 60
                number == "two" || number == "2" -> 120
                number == "eight" || number == "8" -> 480
                else -> -1
            }
        } else {
            when (number) {
                "thirty", "30" -> 30
                "sixty", "60" -> 60
                "120" -> 120
                "480" -> 480
                else -> -1
            }
        }
    }.toList()

    private fun eventForMinutes(minutes: Int): CommandEvent = when (minutes) {
        30 -> CommandEvent.GAS_30_MINUTES
        60 -> CommandEvent.GAS_1_HOUR
        120 -> CommandEvent.GAS_2_HOURS
        480 -> CommandEvent.GAS_8_HOURS
        else -> throw IllegalArgumentException("Unsupported duration: $minutes")
    }

    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    companion object {
        private val CHINESE_DURATION = Regex("(半|[一二两三四五六七八九十百]+|\\d+)(?:个)?(分钟|小时)")
        private val ENGLISH_DURATION = Regex("\\b(half(?: an)?|one|two|three|four|five|six|seven|eight|nine|ten|thirty|forty five|sixty|\\d+)\\s+(minutes?|hours?)\\b")
        private val ENGLISH_NEGATION = Regex("\\b(?:do not|don't|dont|never|not|no)\\b")
        private val ENGLISH_POWER_ON = Regex("\\b(?:turn|switch|power)\\b.*\\bon\\b")
        private val ENGLISH_POWER_OFF = Regex("\\b(?:turn|switch|power)\\b.*\\boff\\b")
        private val ENGLISH_START_VERB = Regex("\\b(?:start|activate|open)\\b")
        private val ENGLISH_STOP_VERB = Regex("\\b(?:shut down|shutdown|stop|deactivate|close)\\b")
        private val ENGLISH_DURATION_VERB = Regex("\\b(?:run|running|work|operate|operating)\\b")
    }
}
