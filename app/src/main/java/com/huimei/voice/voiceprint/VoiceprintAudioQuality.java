package com.huimei.voice.voiceprint;

public final class VoiceprintAudioQuality {
    public static final double MIN_DURATION_SECONDS = 3.0;
    public static final double MAX_DURATION_SECONDS = 7.0;
    public static final double QUIET_THRESHOLD_DBFS = -50.0;
    public static final double CLIPPING_RATIO_LIMIT = 0.01;

    private static final double PCM_FULL_SCALE = 32768.0;
    private static final double CLIPPING_AMPLITUDE = 0.98 * Short.MAX_VALUE;

    public enum Reason {
        NONE,
        TOO_SHORT,
        TOO_LONG,
        TOO_QUIET,
        CLIPPED
    }

    private VoiceprintAudioQuality() {
    }

    public static Result analyze(short[] samples, int sampleRate) {
        if (samples == null) {
            throw new IllegalArgumentException("PCM samples are required");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }

        double sumSquares = 0.0;
        int peak = 0;
        int clippedSamples = 0;
        for (short sample : samples) {
            int amplitude = Math.abs((int) sample);
            sumSquares += (double) sample * sample;
            peak = Math.max(peak, amplitude);
            if (amplitude >= CLIPPING_AMPLITUDE) {
                clippedSamples++;
            }
        }

        double duration = samples.length / (double) sampleRate;
        double rms = samples.length == 0 ? 0.0 : Math.sqrt(sumSquares / samples.length);
        double rmsDbfs = toDbfs(rms);
        double peakDbfs = toDbfs(peak);
        double clippingRatio = samples.length == 0
                ? 0.0
                : clippedSamples / (double) samples.length;

        Reason reason = Reason.NONE;
        if (duration < MIN_DURATION_SECONDS) {
            reason = Reason.TOO_SHORT;
        } else if (duration > MAX_DURATION_SECONDS) {
            reason = Reason.TOO_LONG;
        } else if (rmsDbfs < QUIET_THRESHOLD_DBFS) {
            reason = Reason.TOO_QUIET;
        } else if (clippingRatio >= CLIPPING_RATIO_LIMIT) {
            reason = Reason.CLIPPED;
        }
        return new Result(duration, rmsDbfs, peakDbfs, clippingRatio, reason);
    }

    private static double toDbfs(double amplitude) {
        if (amplitude <= 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        return 20.0 * Math.log10(amplitude / PCM_FULL_SCALE);
    }

    public static final class Result {
        private final double durationSeconds;
        private final double rmsDbfs;
        private final double peakDbfs;
        private final double clippingRatio;
        private final Reason reason;

        private Result(
                double durationSeconds,
                double rmsDbfs,
                double peakDbfs,
                double clippingRatio,
                Reason reason) {
            this.durationSeconds = durationSeconds;
            this.rmsDbfs = rmsDbfs;
            this.peakDbfs = peakDbfs;
            this.clippingRatio = clippingRatio;
            this.reason = reason;
        }

        public boolean isAccepted() {
            return reason == Reason.NONE;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public double getRmsDbfs() {
            return rmsDbfs;
        }

        public double getPeakDbfs() {
            return peakDbfs;
        }

        public double getClippingRatio() {
            return clippingRatio;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
