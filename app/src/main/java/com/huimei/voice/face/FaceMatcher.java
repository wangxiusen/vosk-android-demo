package com.huimei.voice.face;

import java.util.List;
import java.util.Map;

public final class FaceMatcher {
    private FaceMatcher() {
    }

    public static float[] normalize(float[] vector) {
        requireVector(vector);
        double squaredLength = 0d;
        for (float value : vector) {
            squaredLength += value * (double) value;
        }
        if (squaredLength <= 0d) {
            throw new IllegalArgumentException("Face vector magnitude must be positive");
        }
        double length = Math.sqrt(squaredLength);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / length);
        }
        return normalized;
    }

    public static float cosine(float[] left, float[] right) {
        requireVector(left);
        requireVector(right);
        if (left.length != right.length) {
            throw new IllegalArgumentException("Face vector dimensions must match");
        }
        float[] normalizedLeft = normalize(left);
        float[] normalizedRight = normalize(right);
        double score = 0d;
        for (int i = 0; i < normalizedLeft.length; i++) {
            score += normalizedLeft[i] * (double) normalizedRight[i];
        }
        return (float) Math.max(-1d, Math.min(1d, score));
    }

    public static float[] averageNormalized(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("Face vectors are required");
        }
        requireVector(vectors.get(0));
        int dimension = vectors.get(0).length;
        double[] sum = new double[dimension];
        for (float[] vector : vectors) {
            requireVector(vector);
            if (vector.length != dimension) {
                throw new IllegalArgumentException("Face vector dimensions must match");
            }
            float[] normalized = normalize(vector);
            for (int i = 0; i < dimension; i++) {
                sum[i] += normalized[i];
            }
        }
        float[] average = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            average[i] = (float) (sum[i] / vectors.size());
        }
        return normalize(average);
    }

    public static FaceMatch best(
            float[] query,
            List<FaceProfile> profiles,
            Map<String, float[]> embeddings,
            float threshold) {
        requireVector(query);
        if (profiles == null || embeddings == null) {
            throw new IllegalArgumentException("Face profiles and embeddings are required");
        }
        if (!Float.isFinite(threshold) || threshold < -1f || threshold > 1f) {
            throw new IllegalArgumentException("Face match threshold is invalid");
        }
        if (profiles.isEmpty()) {
            return FaceMatch.unknown();
        }

        FaceProfile bestProfile = null;
        float bestScore = -1f;
        for (FaceProfile profile : profiles) {
            if (profile == null) {
                throw new IllegalArgumentException("Face profile cannot be null");
            }
            float[] embedding = embeddings.get(profile.getId());
            if (embedding == null) {
                throw new IllegalArgumentException("Face profile embedding is missing");
            }
            float score = cosine(query, embedding);
            if (bestProfile == null || score > bestScore) {
                bestProfile = profile;
                bestScore = score;
            }
        }
        return new FaceMatch(bestProfile, bestScore, bestScore >= threshold);
    }

    private static void requireVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Face vector is required");
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Face vector values must be finite");
            }
        }
    }
}
