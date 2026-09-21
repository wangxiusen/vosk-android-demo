package com.huimei.voice.voiceprint;

import java.util.List;

public interface VoiceprintListener {
    void onStateChanged(VoiceprintController.State state);

    void onModelReady(long loadMillis, int dimension);

    void onProfilesChanged(List<VoiceprintProfile> profiles);

    void onEnrollmentProgress(
            int acceptedSamples,
            int requiredSamples,
            VoiceprintAudioQuality.Result quality);

    void onAudioRejected(VoiceprintAudioQuality.Result quality);

    void onMatch(VoiceprintMatch match, long inferenceMillis);

    void onError(String message);

    void onLog(String message);
}
