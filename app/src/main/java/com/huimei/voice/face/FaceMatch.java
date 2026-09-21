package com.huimei.voice.face;

public final class FaceMatch {
    private final FaceProfile profile;
    private final float score;
    private final boolean accepted;

    public FaceMatch(FaceProfile profile, float score, boolean accepted) {
        if (profile == null) {
            throw new IllegalArgumentException("Face match profile is required");
        }
        if (!Float.isFinite(score)) {
            throw new IllegalArgumentException("Face match score must be finite");
        }
        this.profile = profile;
        this.score = score;
        this.accepted = accepted;
    }

    private FaceMatch() {
        profile = null;
        score = 0f;
        accepted = false;
    }

    public static FaceMatch unknown() {
        return new FaceMatch();
    }

    public FaceProfile getProfile() {
        return profile;
    }

    public float getScore() {
        return score;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isUnknown() {
        return profile == null;
    }
}
