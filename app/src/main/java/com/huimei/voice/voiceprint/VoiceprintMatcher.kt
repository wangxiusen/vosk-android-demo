package com.huimei.voice.voiceprint

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object VoiceprintMatcher {
    @JvmStatic
    fun normalize(vector: FloatArray): FloatArray {
        validateVector(vector)
        var sumSquares = 0.0
        for (value in vector) {
            sumSquares += value.toDouble() * value
        }
        require(sumSquares != 0.0 && sumSquares.isFinite()) { "Voiceprint vector has no finite magnitude" }
        val norm = sqrt(sumSquares)
        val result = FloatArray(vector.size)
        for (i in vector.indices) {
            result[i] = (vector[i] / norm).toFloat()
        }
        return result
    }

    @JvmStatic
    fun average(vectors: MutableList<FloatArray?>): FloatArray {
        require(!(vectors == null || vectors.isEmpty())) { "At least one voiceprint vector is required" }
        val first = VoiceprintMatcher.normalize(vectors.get(0)!!)
        val sum = DoubleArray(first.size)
        add(sum, first)
        for (i in 1..<vectors.size) {
            val normalized = VoiceprintMatcher.normalize(vectors.get(i)!!)
            require(normalized.size == sum.size) { "Voiceprint dimensions do not match" }
            add(sum, normalized)
        }
        val mean = FloatArray(sum.size)
        for (i in mean.indices) {
            mean[i] = (sum[i] / vectors.size).toFloat()
        }
        return normalize(mean)
    }

    @JvmStatic
    fun cosine(left: FloatArray, right: FloatArray): Float {
        validateVector(left)
        validateVector(right)
        require(left.size == right.size) { "Voiceprint dimensions do not match" }
        var dot = 0.0
        var leftSquares = 0.0
        var rightSquares = 0.0
        for (i in left.indices) {
            dot += left[i].toDouble() * right[i]
            leftSquares += left[i].toDouble() * left[i]
            rightSquares += right[i].toDouble() * right[i]
        }
        require(!(leftSquares == 0.0 || rightSquares == 0.0)) { "Voiceprint vector has zero magnitude" }
        val score = dot / sqrt(leftSquares * rightSquares)
        require(score.isFinite()) { "Voiceprint similarity is not finite" }
        return max(-1.0, min(1.0, score)).toFloat()
    }

    @JvmStatic
    fun search(
        enrolled: MutableMap<VoiceprintProfile?, FloatArray?>,
        probe: FloatArray,
        threshold: Float
    ): VoiceprintMatch {
        validateThreshold(threshold)
        validateVector(probe)
        requireNotNull(enrolled) { "Enrolled voiceprints are required" }
        var bestProfile: VoiceprintProfile? = null
        var bestScore = -1f
        for (entry in enrolled.entries) {
            requireNotNull(entry.key) { "Voiceprint profile is required" }
            val score = VoiceprintMatcher.cosine(entry.value!!, probe)
            if (bestProfile == null || score > bestScore) {
                bestProfile = entry.key
                bestScore = score
            }
        }
        if (bestProfile != null && bestScore >= threshold) {
            return VoiceprintMatch.accepted(bestProfile, bestScore, threshold)
        }
        return VoiceprintMatch.rejected(bestScore, threshold)
    }

    private fun add(sum: DoubleArray, vector: FloatArray) {
        for (i in sum.indices) {
            sum[i] += vector[i].toDouble()
        }
    }

    private fun validateVector(vector: FloatArray) {
        require(!(vector == null || vector.size == 0)) { "Voiceprint vector is required" }
        for (value in vector) {
            require(value.isFinite()) { "Voiceprint vector must contain finite values" }
        }
    }

    private fun validateThreshold(threshold: kotlin.Float) {
        require(
            !(threshold.isNaN()
                    || threshold.isInfinite()
                    || threshold < -1f || threshold > 1f)
        ) { "Voiceprint threshold must be between -1 and 1" }
    }
}
