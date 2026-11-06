package com.huimei.voice.recognition

import com.huimei.voice.model.RecognitionAction
import com.huimei.voice.model.VoiceLanguage

interface VoiceRecognitionListener {
    fun onStatus(status: String?)

    fun onDiagnostic(message: String?)

    fun onWakeWindowChanged(awake: Boolean, remainingMillis: Long)

    fun onWakeUp(language: VoiceLanguage, action: RecognitionAction)

    fun onCommand(language: VoiceLanguage, action: RecognitionAction)
}
