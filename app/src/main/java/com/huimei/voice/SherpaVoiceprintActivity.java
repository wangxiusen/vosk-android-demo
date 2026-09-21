package com.huimei.voice;

import com.huimei.voice.voiceprint.VoiceprintEngineType;

public final class SherpaVoiceprintActivity extends BaseVoiceprintActivity {
    @Override
    protected VoiceprintEngineType engineType() {
        return VoiceprintEngineType.SHERPA;
    }
}
