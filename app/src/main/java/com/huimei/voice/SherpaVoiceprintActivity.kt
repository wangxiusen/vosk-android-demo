package com.huimei.voice

import com.huimei.voice.voiceprint.VoiceprintEngineType

class SherpaVoiceprintActivity : BaseVoiceprintActivity() {
    override fun engineType(): VoiceprintEngineType? {
        return VoiceprintEngineType.SHERPA
    }
}
