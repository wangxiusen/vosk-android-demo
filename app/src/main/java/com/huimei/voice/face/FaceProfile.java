package com.huimei.voice.face;

import java.util.Objects;

public final class FaceProfile {
    private final String id;
    private final String displayName;
    private final int sampleCount;
    private final long createdAtMillis;

    public FaceProfile(String id, String displayName, int sampleCount, long createdAtMillis) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Face profile ID is required");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Face profile display name is required");
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("Face profile sample count must be positive");
        }
        if (createdAtMillis < 0L) {
            throw new IllegalArgumentException("Face profile creation time is invalid");
        }
        this.id = id.trim();
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
        if (!(other instanceof FaceProfile)) {
            return false;
        }
        FaceProfile value = (FaceProfile) other;
        return sampleCount == value.sampleCount
                && createdAtMillis == value.createdAtMillis
                && id.equals(value.id)
                && displayName.equals(value.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, sampleCount, createdAtMillis);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
