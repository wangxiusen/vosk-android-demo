package com.huimei.voice.voiceprint;

import java.util.List;
import java.util.Map;

public final class VoiceprintMatcher {
    private VoiceprintMatcher() {
    }

    public static float[] normalize(float[] vector) {
        validateVector(vector);
        double sumSquares = 0.0;
        for (float value : vector) {
            sumSquares += (double) value * value;
        }
        if (sumSquares == 0.0 || Double.isInfinite(sumSquares)) {
            throw new IllegalArgumentException("Voiceprint vector has no finite magnitude");
        }
        double norm = Math.sqrt(sumSquares);
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    public static float[] average(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("At least one voiceprint vector is required");
        }
        float[] first = normalize(vectors.get(0));
        double[] sum = new double[first.length];
        add(sum, first);
        for (int i = 1; i < vectors.size(); i++) {
            float[] normalized = normalize(vectors.get(i));
            if (normalized.length != sum.length) {
                throw new IllegalArgumentException("Voiceprint dimensions do not match");
            }
            add(sum, normalized);
        }
        float[] mean = new float[sum.length];
        for (int i = 0; i < mean.length; i++) {
            mean[i] = (float) (sum[i] / vectors.size());
        }
        return normalize(mean);
    }

    public static float cosine(float[] left, float[] right) {
        validateVector(left);
        validateVector(right);
        if (left.length != right.length) {
            throw new IllegalArgumentException("Voiceprint dimensions do not match");
        }
        double dot = 0.0;
        double leftSquares = 0.0;
        double rightSquares = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftSquares += (double) left[i] * left[i];
            rightSquares += (double) right[i] * right[i];
        }
        if (leftSquares == 0.0 || rightSquares == 0.0) {
            throw new IllegalArgumentException("Voiceprint vector has zero magnitude");
        }
        double score = dot / Math.sqrt(leftSquares * rightSquares);
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("Voiceprint similarity is not finite");
        }
        return (float) Math.max(-1.0, Math.min(1.0, score));
    }

    public static VoiceprintMatch search(
            Map<VoiceprintProfile, float[]> enrolled,
            float[] probe,
            float threshold) {
        validateThreshold(threshold);
        validateVector(probe);
        if (enrolled == null) {
            throw new IllegalArgumentException("Enrolled voiceprints are required");
        }
        VoiceprintProfile bestProfile = null;
        float bestScore = -1f;
        for (Map.Entry<VoiceprintProfile, float[]> entry : enrolled.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Voiceprint profile is required");
            }
            float score = cosine(entry.getValue(), probe);
            if (bestProfile == null || score > bestScore) {
                bestProfile = entry.getKey();
                bestScore = score;
            }
        }
        if (bestProfile != null && bestScore >= threshold) {
            return VoiceprintMatch.accepted(bestProfile, bestScore, threshold);
        }
        return VoiceprintMatch.rejected(bestScore, threshold);
    }

    private static void add(double[] sum, float[] vector) {
        for (int i = 0; i < sum.length; i++) {
            sum[i] += vector[i];
        }
    }

    private static void validateVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Voiceprint vector is required");
        }
        for (float value : vector) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Voiceprint vector must contain finite values");
            }
        }
    }

    private static void validateThreshold(float threshold) {
        if (Float.isNaN(threshold)
                || Float.isInfinite(threshold)
                || threshold < -1f
                || threshold > 1f) {
            throw new IllegalArgumentException("Voiceprint threshold must be between -1 and 1");
        }
    }
}
