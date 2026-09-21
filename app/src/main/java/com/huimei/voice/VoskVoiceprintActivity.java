package com.huimei.voice;

import com.huimei.voice.voiceprint.VoiceprintEngineType;

public final class VoskVoiceprintActivity extends BaseVoiceprintActivity {
    @Override
    protected VoiceprintEngineType engineType() {
        return VoiceprintEngineType.VOSK;
    }
}
