package com.huimei.voice.functiongemma;

import com.huimei.voice.model.VoiceLanguage;

public interface HybridVoiceCommandListener {
    void onStateChanged(HybridVoiceState state, String message);

    void onWake(VoiceLanguage language, long remainingMillis);

    void onTranscript(VoiceLanguage language, String transcript, long utteranceId);

    void onWakeTimeout(VoiceLanguage language);

    void onDiagnostic(String message);

    void onError(String message);
}
