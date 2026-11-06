package com.huimei.voice.recognition

import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.CommandMatch
import com.huimei.voice.model.VoiceLanguage
import org.json.JSONArray
import java.util.Arrays
import java.util.Objects
import java.util.Optional
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap

class CommandCatalog private constructor(
    private val language: VoiceLanguage?,
    private val wakePhrase: String?,
    private val assetModelPath: String?,
    private val phrases: MutableMap<String?, CommandMatch?>,
    private val grammarPhrases: MutableList<String?>
) {
    fun find(text: String?): Optional<CommandMatch> {
        var normalized: String = normalize(text)
        if (language == VoiceLanguage.CHINESE) {
            normalized = normalized.replace(" ", "")
        }
        return Optional.ofNullable(phrases[normalized])
    }

    fun grammarJson(): String {
        val grammar = JSONArray()
        for (phrase in grammarPhrases) {
            grammar.put(phrase)
        }
        grammar.put("[unk]")
        return grammar.toString()
    }

    fun language(): VoiceLanguage? {
        return language
    }

    fun wakePhrase(): String? {
        return wakePhrase
    }

    fun assetModelPath(): String? {
        return assetModelPath
    }

    companion object {
        @JvmStatic
        fun forLanguage(language: VoiceLanguage?): CommandCatalog {
            Objects.requireNonNull<VoiceLanguage?>(language, "language")
            return if (language == VoiceLanguage.CHINESE) chinese() else english()
        }

        private fun chinese(): CommandCatalog {
            val phrases = LinkedHashMap<String?, CommandMatch?>()
            add(phrases, "潓美医疗", "潓美医疗", CommandEvent.WAKE_UP)
            add(phrases, "惠美医疗", "潓美医疗", CommandEvent.WAKE_UP)
            add(phrases, "半小时产气", "30分钟产气", CommandEvent.GAS_30_MINUTES)
            add(phrases, "三十分钟产气", "30分钟产气", CommandEvent.GAS_30_MINUTES)
            add(phrases, "30分钟产气", "30分钟产气", CommandEvent.GAS_30_MINUTES)
            add(phrases, "一小时产气", "1小时产气", CommandEvent.GAS_1_HOUR)
            add(phrases, "1小时产气", "1小时产气", CommandEvent.GAS_1_HOUR)
            add(phrases, "两小时产气", "2小时产气", CommandEvent.GAS_2_HOURS)
            add(phrases, "2小时产气", "2小时产气", CommandEvent.GAS_2_HOURS)
            add(phrases, "八小时产气", "8小时产气", CommandEvent.GAS_8_HOURS)
            add(phrases, "8小时产气", "8小时产气", CommandEvent.GAS_8_HOURS)
            add(phrases, "开机", "开机", CommandEvent.POWER_ON)
            add(phrases, "关机", "关机", CommandEvent.POWER_OFF)
            val grammarPhrases: MutableList<String?> = mutableListOf<String?>(
                "惠美 医疗",
                "惠 美 医疗",
                "半小时 产 气",
                "三十分钟 产 气",
                "一小时 产 气",
                "两小时 产 气",
                "八小时 产 气",
                "开机",
                "关机"
            )
            return CommandCatalog(
                VoiceLanguage.CHINESE, "潓美医疗", "model-cn", phrases, grammarPhrases
            )
        }

        private fun english(): CommandCatalog {
            val phrases = LinkedHashMap<String?, CommandMatch?>()
            add(phrases, "hello medical", "Hello Medical", CommandEvent.WAKE_UP)
            add(
                phrases,
                "half hour gas production",
                "30-minute gas production",
                CommandEvent.GAS_30_MINUTES
            )
            add(
                phrases,
                "thirty minute gas production",
                "30-minute gas production",
                CommandEvent.GAS_30_MINUTES
            )
            add(
                phrases,
                "one hour gas production",
                "1-hour gas production",
                CommandEvent.GAS_1_HOUR
            )
            add(
                phrases,
                "two hour gas production",
                "2-hour gas production",
                CommandEvent.GAS_2_HOURS
            )
            add(
                phrases,
                "two hours gas production",
                "2-hour gas production",
                CommandEvent.GAS_2_HOURS
            )
            add(
                phrases,
                "eight hour gas production",
                "8-hour gas production",
                CommandEvent.GAS_8_HOURS
            )
            add(
                phrases,
                "eight hours gas production",
                "8-hour gas production",
                CommandEvent.GAS_8_HOURS
            )
            add(phrases, "power on", "Power on", CommandEvent.POWER_ON)
            add(phrases, "power off", "Power off", CommandEvent.POWER_OFF)
            return CommandCatalog(
                VoiceLanguage.ENGLISH,
                "Hello Medical",
                "model-en-us",
                phrases,
                Arrays.asList<String?>(*phrases.keys.toTypedArray<String?>())
            )
        }

        private fun add(
            phrases: MutableMap<String?, CommandMatch?>,
            recognizedPhrase: String?,
            displayPhrase: String?,
            event: CommandEvent?
        ) {
            val normalized: String = normalize(recognizedPhrase)
            phrases.put(normalized, CommandMatch(normalized, displayPhrase, event))
        }

        @JvmStatic
        fun normalize(text: String?): String {
            if (text == null) {
                return ""
            }
            return text.trim { it <= ' ' }.lowercase().replace("\\s+".toRegex(), " ")
        }
    }
}
