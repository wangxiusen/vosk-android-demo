package com.huimei.voice

import com.huimei.voice.voiceprint.VoiceprintEngineType

class VoskVoiceprintActivity : BaseVoiceprintActivity() {
    override fun engineType(): VoiceprintEngineType? {
        return VoiceprintEngineType.VOSK
    }
}
