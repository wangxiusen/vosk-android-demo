package com.huimei.voice.face;

import java.util.Optional;

public final class FaceRecognitionStabilizer {
    private final int requiredFrames;
    private String candidateId;
    private int consecutiveFrames;

    public FaceRecognitionStabilizer(int requiredFrames) {
        if (requiredFrames <= 0) {
            throw new IllegalArgumentException("Required face frame count must be positive");
        }
        this.requiredFrames = requiredFrames;
    }

    public Optional<FaceMatch> accept(FaceMatch match) {
        if (match == null) {
            throw new IllegalArgumentException("Face match is required");
        }
        if (!match.isAccepted() || match.isUnknown()) {
            reset();
            return Optional.empty();
        }

        String profileId = match.getProfile().getId();
        if (!profileId.equals(candidateId)) {
            candidateId = profileId;
            consecutiveFrames = 1;
        } else {
            consecutiveFrames++;
        }
        if (consecutiveFrames < requiredFrames) {
            return Optional.empty();
        }
        reset();
        return Optional.of(match);
    }

    public void reset() {
        candidateId = null;
        consecutiveFrames = 0;
    }
}
