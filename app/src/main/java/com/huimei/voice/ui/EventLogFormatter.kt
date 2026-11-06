package com.huimei.voice.ui

import com.huimei.voice.model.RecognitionAction
import com.huimei.voice.model.VoiceLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventLogFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun eventLine(
        language: VoiceLanguage,
        type: String?,
        action: RecognitionAction
    ): String {
        return String.format(
            Locale.ROOT,
            "%s | %s | %s | %s | %s | %s",
            timeFormat.format(Date()),
            language.displayName,
            type,
            action.originalText,
            action.match!!.displayPhrase,
            action.match.event.name
        )
    }

    fun diagnosticLine(message: String?): String {
        return timeFormat.format(Date()) + " | 系统 | " + message
    }
}
