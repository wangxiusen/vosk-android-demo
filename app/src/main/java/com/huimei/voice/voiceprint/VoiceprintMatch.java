package com.huimei.voice.voiceprint;

public final class VoiceprintMatch {
    private final VoiceprintProfile profile;
    private final float score;
    private final float threshold;
    private final boolean accepted;

    private VoiceprintMatch(
            VoiceprintProfile profile,
            float score,
            float threshold,
            boolean accepted) {
        this.profile = profile;
        this.score = score;
        this.threshold = threshold;
        this.accepted = accepted;
    }

    static VoiceprintMatch accepted(VoiceprintProfile profile, float score, float threshold) {
        return new VoiceprintMatch(profile, score, threshold, true);
    }

    static VoiceprintMatch rejected(float score, float threshold) {
        return new VoiceprintMatch(null, score, threshold, false);
    }

    public VoiceprintProfile getProfile() {
        return profile;
    }

    public float getScore() {
        return score;
    }

    public float getThreshold() {
        return threshold;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
