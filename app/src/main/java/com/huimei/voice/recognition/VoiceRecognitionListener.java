package com.huimei.voice.recognition;

import com.huimei.voice.model.RecognitionAction;
import com.huimei.voice.model.VoiceLanguage;

public interface VoiceRecognitionListener {
    void onStatus(String status);

    void onDiagnostic(String message);

    void onWakeWindowChanged(boolean awake, long remainingMillis);

    void onWakeUp(VoiceLanguage language, RecognitionAction action);

    void onCommand(VoiceLanguage language, RecognitionAction action);
}
