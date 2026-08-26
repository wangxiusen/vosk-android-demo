package com.huimei.voice.tts;

public final class PcmLoudnessNormalizer {
    private static final float SILENCE_THRESHOLD = dbfsToLinear(-45.0f);
    private static final float MAX_GAIN = dbfsToLinear(18.0f);
    private static final float PEAK_LIMIT = dbfsToLinear(-1.0f);
    private static final float FLOOR_DBFS = -120.0f;

    private PcmLoudnessNormalizer() {
    }

    public static Result analyze(float[] samples, float targetRmsDbfs) {
        float activeSquareSum = 0.0f;
        int activeCount = 0;
        float peak = 0.0f;

        for (int index = 0; index < samples.length; index++) {
            float sample = samples[index];
            if (!Float.isFinite(sample)) {
                continue;
            }
            float absolute = Math.abs(sample);
            if (absolute > peak) {
                peak = absolute;
            }
            if (absolute >= SILENCE_THRESHOLD) {
                activeSquareSum += sample * sample;
                activeCount++;
            }
        }

        if (activeCount == 0) {
            return new Result(false, 1.0f, FLOOR_DBFS, linearToDbfs(peak),
                    FLOOR_DBFS, peak, false);
        }

        float activeRms = (float) Math.sqrt(activeSquareSum / activeCount);
        float requestedGain = dbfsToLinear(targetRmsDbfs) / activeRms;
        float levelLimitedGain = Math.min(requestedGain, MAX_GAIN);
        float peakSafeGain = peak > 0.0f ? PEAK_LIMIT / peak : MAX_GAIN;
        float gain = Math.min(levelLimitedGain, peakSafeGain);
        boolean peakLimited = peakSafeGain < levelLimitedGain - 0.0001f;

        return new Result(
                true,
                gain,
                linearToDbfs(activeRms),
                linearToDbfs(peak),
                linearToDbfs(activeRms * gain),
                peak * gain,
                peakLimited);
    }

    public static void copyWithGain(
            float[] input,
            int offset,
            int length,
            float startGain,
            float endGain,
            float[] output) {
        if (offset < 0 || length < 0 || offset + length > input.length || output.length < length) {
            throw new IllegalArgumentException("Invalid PCM buffer range");
        }

        float gain = length <= 1 ? endGain : startGain;
        float gainStep = length <= 1 ? 0.0f : (endGain - startGain) / (length - 1);
        for (int index = 0; index < length; index++) {
            float sample = Float.isFinite(input[offset + index]) ? input[offset + index] : 0.0f;
            output[index] = sample * gain;
            gain += gainStep;
        }
    }

    private static float dbfsToLinear(float dbfs) {
        return (float) Math.pow(10.0, dbfs / 20.0);
    }

    private static float linearToDbfs(float value) {
        if (!(value > 0.0f) || !Float.isFinite(value)) {
            return FLOOR_DBFS;
        }
        return (float) (20.0 * Math.log10(value));
    }

    public static final class Result {
        private final boolean activeAudio;
        private final float gain;
        private final float inputActiveRmsDbfs;
        private final float inputPeakDbfs;
        private final float outputActiveRmsDbfs;
        private final float outputPeak;
        private final boolean peakLimited;

        private Result(
                boolean activeAudio,
                float gain,
                float inputActiveRmsDbfs,
                float inputPeakDbfs,
                float outputActiveRmsDbfs,
                float outputPeak,
                boolean peakLimited) {
            this.activeAudio = activeAudio;
            this.gain = gain;
            this.inputActiveRmsDbfs = inputActiveRmsDbfs;
            this.inputPeakDbfs = inputPeakDbfs;
            this.outputActiveRmsDbfs = outputActiveRmsDbfs;
            this.outputPeak = outputPeak;
            this.peakLimited = peakLimited;
        }

        public boolean hasActiveAudio() {
            return activeAudio;
        }

        public float gain() {
            return gain;
        }

        public float inputActiveRmsDbfs() {
            return inputActiveRmsDbfs;
        }

        public float inputPeakDbfs() {
            return inputPeakDbfs;
        }

        public float outputActiveRmsDbfs() {
            return outputActiveRmsDbfs;
        }

        public float outputPeak() {
            return outputPeak;
        }

        public float outputPeakDbfs() {
            return linearToDbfs(outputPeak);
        }

        public float gainDb() {
            return linearToDbfs(gain);
        }

        public boolean isPeakLimited() {
            return peakLimited;
        }
    }
}
