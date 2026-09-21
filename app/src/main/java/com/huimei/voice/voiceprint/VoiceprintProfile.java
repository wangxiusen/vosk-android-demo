package com.huimei.voice.voiceprint;

import java.util.Objects;

public final class VoiceprintProfile {
    private final String id;
    private final String displayName;
    private final int sampleCount;
    private final long createdAtMillis;

    public VoiceprintProfile(
            String id,
            String displayName,
            int sampleCount,
            long createdAtMillis) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile ID is required");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("Sample count cannot be negative");
        }
        this.id = id;
        this.displayName = displayName.trim();
        this.sampleCount = sampleCount;
        this.createdAtMillis = createdAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VoiceprintProfile)) {
            return false;
        }
        VoiceprintProfile that = (VoiceprintProfile) other;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
