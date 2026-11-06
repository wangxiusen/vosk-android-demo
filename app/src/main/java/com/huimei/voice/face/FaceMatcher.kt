package com.huimei.voice.face

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.Double
import kotlin.DoubleArray
import kotlin.FloatArray
import kotlin.String

object FaceMatcher {
    @JvmStatic
    fun normalize(vector: FloatArray): FloatArray {
        requireVector(vector)
        var squaredLength = 0.0
        for (value in vector) {
            squaredLength += value * value.toDouble()
        }
        require(!(squaredLength <= 0.0)) { "Face vector magnitude must be positive" }
        val length: Double = sqrt(squaredLength)
        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = (vector[i] / length).toFloat()
        }
        return normalized
    }

    @JvmStatic
    fun cosine(left: FloatArray, right: FloatArray): Float {
        requireVector(left)
        requireVector(right)
        require(left.size == right.size) { "Face vector dimensions must match" }
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        var score = 0.0
        for (i in normalizedLeft.indices) {
            score += normalizedLeft[i] * normalizedRight[i].toDouble()
        }
        return max(-1.0, min(1.0, score)).toFloat()
    }

    @JvmStatic
    fun averageNormalized(vectors: MutableList<FloatArray>): FloatArray {
        require(!(vectors == null || vectors.isEmpty())) { "Face vectors are required" }
        requireVector(vectors.get(0))
        val dimension = vectors.get(0).size
        val sum = DoubleArray(dimension)
        for (vector in vectors) {
            requireVector(vector)
            require(vector.size == dimension) { "Face vector dimensions must match" }
            val normalized = normalize(vector)
            for (i in 0..<dimension) {
                sum[i] += normalized[i].toDouble()
            }
        }
        val average = FloatArray(dimension)
        for (i in 0..<dimension) {
            average[i] = (sum[i] / vectors.size).toFloat()
        }
        return normalize(average)
    }

    @JvmStatic
    fun best(
        query: FloatArray,
        profiles: MutableList<FaceProfile>,
        embeddings: MutableMap<String?, FloatArray?>,
        threshold: Float
    ): FaceMatch? {
        requireVector(query)
        require(!(profiles == null || embeddings == null)) { "Face profiles and embeddings are required" }
        require(threshold.isFinite() && threshold >= -1f && threshold <= 1f) { "Face match threshold is invalid" }
        if (profiles.isEmpty()) {
            return FaceMatch.unknown()
        }

        var bestProfile: FaceProfile? = null
        var bestScore = -1f
        for (profile in profiles) {
            requireNotNull(profile) { "Face profile cannot be null" }
            val embedding = embeddings.get(profile.id)
            requireNotNull(embedding) { "Face profile embedding is missing" }
            val score = FaceMatcher.cosine(query, embedding!!)
            if (bestProfile == null || score > bestScore) {
                bestProfile = profile
                bestScore = score
            }
        }
        return FaceMatch(bestProfile!!, bestScore, bestScore >= threshold)
    }

    private fun requireVector(vector: FloatArray) {
        require(!(vector == null || vector.size == 0)) { "Face vector is required" }
        for (value in vector) {
            require(value.isFinite()) { "Face vector values must be finite" }
        }
    }
}
