package com.huimei.voice.face

import kotlin.Boolean
import kotlin.ByteArray
import kotlin.FloatArray
import kotlin.Int
import kotlin.Long

class FaceObservation private constructor(
    faceCount: Int,
    qualityReason: FaceFrameQuality.Reason,
    embedding: FloatArray?,
    portraitJpeg: ByteArray?,
    detectionScore: Float,
    inferenceMillis: Long
) {
    private val faceCount: Int
    private val qualityReason: FaceFrameQuality.Reason
    private val embedding: FloatArray?
    private val portraitJpeg: ByteArray?
    private val detectionScore: Float
    private val inferenceMillis: Long

    init {
        require(faceCount >= 0 && inferenceMillis >= 0L && detectionScore.isFinite()) {
            "Face observation metadata is invalid"
        }
        val accepted = qualityReason == FaceFrameQuality.Reason.ACCEPTED
        require(!(accepted && (embedding == null || embedding.size == 0 || portraitJpeg == null || portraitJpeg.size == 0))) { "Accepted face observation needs features" }
        require(!(!accepted && (embedding != null || portraitJpeg != null))) { "Rejected face observation cannot contain features" }
        this.faceCount = faceCount
        this.qualityReason = qualityReason
        this.embedding = if (embedding == null) null else embedding.copyOf(embedding.size)
        this.portraitJpeg = if (portraitJpeg == null)
            null
        else portraitJpeg.copyOf(portraitJpeg.size)
        this.detectionScore = detectionScore
        this.inferenceMillis = inferenceMillis
    }

    val isQualityAccepted: Boolean
        get() = qualityReason == FaceFrameQuality.Reason.ACCEPTED

    fun faceCount(): Int {
        return faceCount
    }

    fun qualityReason(): FaceFrameQuality.Reason {
        return qualityReason
    }

    fun embedding(): FloatArray? {
        return if (embedding == null) null else embedding.copyOf(embedding.size)
    }

    fun portraitJpeg(): ByteArray? {
        return if (portraitJpeg == null) null else portraitJpeg.copyOf(portraitJpeg.size)
    }

    fun detectionScore(): kotlin.Float {
        return detectionScore
    }

    fun inferenceMillis(): Long {
        return inferenceMillis
    }

    companion object {
        @JvmStatic
        fun accepted(
            embedding: FloatArray?,
            portraitJpeg: ByteArray?,
            detectionScore: kotlin.Float,
            inferenceMillis: Long
        ): FaceObservation {
            return FaceObservation(
                1, FaceFrameQuality.Reason.ACCEPTED, embedding, portraitJpeg,
                detectionScore, inferenceMillis
            )
        }

        @JvmStatic
        fun rejected(
            faceCount: Int,
            reason: FaceFrameQuality.Reason,
            detectionScore: kotlin.Float,
            inferenceMillis: Long
        ): FaceObservation {
            require(reason != FaceFrameQuality.Reason.ACCEPTED) { "Rejected observation needs a rejection reason" }
            return FaceObservation(
                faceCount, reason, null, null, detectionScore, inferenceMillis
            )
        }
    }
}
