package com.huimei.voice.face;

import java.util.Arrays;

public final class FaceObservation {
    private final int faceCount;
    private final FaceFrameQuality.Reason qualityReason;
    private final float[] embedding;
    private final byte[] portraitJpeg;
    private final float detectionScore;
    private final long inferenceMillis;

    private FaceObservation(
            int faceCount,
            FaceFrameQuality.Reason qualityReason,
            float[] embedding,
            byte[] portraitJpeg,
            float detectionScore,
            long inferenceMillis) {
        if (faceCount < 0 || qualityReason == null || inferenceMillis < 0L
                || !Float.isFinite(detectionScore)) {
            throw new IllegalArgumentException("Face observation metadata is invalid");
        }
        boolean accepted = qualityReason == FaceFrameQuality.Reason.ACCEPTED;
        if (accepted && (embedding == null || embedding.length == 0
                || portraitJpeg == null || portraitJpeg.length == 0)) {
            throw new IllegalArgumentException("Accepted face observation needs features");
        }
        if (!accepted && (embedding != null || portraitJpeg != null)) {
            throw new IllegalArgumentException("Rejected face observation cannot contain features");
        }
        this.faceCount = faceCount;
        this.qualityReason = qualityReason;
        this.embedding = embedding == null ? null : Arrays.copyOf(embedding, embedding.length);
        this.portraitJpeg = portraitJpeg == null
                ? null : Arrays.copyOf(portraitJpeg, portraitJpeg.length);
        this.detectionScore = detectionScore;
        this.inferenceMillis = inferenceMillis;
    }

    public static FaceObservation accepted(
            float[] embedding,
            byte[] portraitJpeg,
            float detectionScore,
            long inferenceMillis) {
        return new FaceObservation(
                1, FaceFrameQuality.Reason.ACCEPTED, embedding, portraitJpeg,
                detectionScore, inferenceMillis);
    }

    public static FaceObservation rejected(
            int faceCount,
            FaceFrameQuality.Reason reason,
            float detectionScore,
            long inferenceMillis) {
        if (reason == FaceFrameQuality.Reason.ACCEPTED) {
            throw new IllegalArgumentException("Rejected observation needs a rejection reason");
        }
        return new FaceObservation(
                faceCount, reason, null, null, detectionScore, inferenceMillis);
    }

    public boolean isQualityAccepted() {
        return qualityReason == FaceFrameQuality.Reason.ACCEPTED;
    }

    public int faceCount() {
        return faceCount;
    }

    public FaceFrameQuality.Reason qualityReason() {
        return qualityReason;
    }

    public float[] embedding() {
        return embedding == null ? null : Arrays.copyOf(embedding, embedding.length);
    }

    public byte[] portraitJpeg() {
        return portraitJpeg == null ? null : Arrays.copyOf(portraitJpeg, portraitJpeg.length);
    }

    public float detectionScore() {
        return detectionScore;
    }

    public long inferenceMillis() {
        return inferenceMillis;
    }
}
