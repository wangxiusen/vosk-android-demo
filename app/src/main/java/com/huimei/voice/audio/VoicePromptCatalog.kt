package com.huimei.voice.audio

import com.huimei.voice.R
import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.VoiceLanguage

object VoicePromptCatalog {
    @JvmStatic
    fun rawResourceFor(language: VoiceLanguage?, event: CommandEvent): Int {
        when (event) {
            CommandEvent.WAKE_UP -> return if (language == VoiceLanguage.CHINESE)
                R.raw.zh_wake_up
            else
                R.raw.en_wake_up

            CommandEvent.GAS_1_HOUR -> return if (language == VoiceLanguage.CHINESE)
                R.raw.zh_gas_1_hour
            else
                R.raw.en_gas_1_hour

            CommandEvent.GAS_8_HOURS -> return if (language == VoiceLanguage.CHINESE)
                R.raw.zh_gas_8_hours
            else
                R.raw.en_gas_8_hours

            else -> return 0
        }
    }
}
