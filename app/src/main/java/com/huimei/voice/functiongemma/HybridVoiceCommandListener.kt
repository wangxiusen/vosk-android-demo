package com.huimei.voice.functiongemma

import com.huimei.voice.model.VoiceLanguage

interface HybridVoiceCommandListener {
    fun onStateChanged(state: HybridVoiceState, message: String)
    fun onWake(language: VoiceLanguage, remainingMillis: Long)
    fun onTranscript(language: VoiceLanguage, transcript: String, utteranceId: Long)
    fun onWakeTimeout(language: VoiceLanguage)
    fun onDiagnostic(message: String)
    fun onError(message: String)
}
