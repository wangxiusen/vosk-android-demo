package com.huimei.voice.avatar;

public final class AudioEnergyMouthMapper {
    private static final double CLOSED_MAX_RMS = 0.012;
    private static final double SMALL_MAX_RMS = 0.04;
    private static final double MEDIUM_MAX_RMS = 0.10;

    private AudioEnergyMouthMapper() {
    }

    public static MouthShape shapeFor(float[] samples, int offset, int length) {
        if (length <= 0) {
            return MouthShape.CLOSED;
        }
        double sumSquares = 0.0;
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            double sample = samples[index];
            sumSquares += sample * sample;
        }
        double rms = Math.sqrt(sumSquares / length);
        if (rms <= CLOSED_MAX_RMS) {
            return MouthShape.CLOSED;
        }
        if (rms <= SMALL_MAX_RMS) {
            return MouthShape.SMALL;
        }
        if (rms <= MEDIUM_MAX_RMS) {
            return MouthShape.MEDIUM;
        }
        return MouthShape.OPEN;
    }
}
