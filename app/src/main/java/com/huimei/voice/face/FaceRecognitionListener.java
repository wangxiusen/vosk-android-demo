package com.huimei.voice.face;

import java.util.List;

public interface FaceRecognitionListener {
    void onStateChanged(FaceRecognitionController.State state);

    void onModelReady(long loadMillis, int dimension);

    void onProfilesChanged(List<FaceProfile> profiles);

    void onEnrollmentProgress(int accepted, int required);

    void onFrameRejected(FaceFrameQuality.Reason reason);

    void onCandidate(FaceMatch match, long inferenceMillis);

    void onMatchConfirmed(FaceMatch match);

    void onLog(String message);

    void onError(String message);
}
