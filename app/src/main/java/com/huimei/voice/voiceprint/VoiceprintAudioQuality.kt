package com.huimei.voice.voiceprint

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

object VoiceprintAudioQuality {
    const val MIN_DURATION_SECONDS: Double = 3.0
    const val MAX_DURATION_SECONDS: Double = 7.0
    @JvmField
    val QUIET_THRESHOLD_DBFS: Double = -50.0
    const val CLIPPING_RATIO_LIMIT: Double = 0.01

    private const val PCM_FULL_SCALE = 32768.0
    private val CLIPPING_AMPLITUDE = 0.98 * Short.Companion.MAX_VALUE

    @JvmStatic
    fun analyze(samples: ShortArray?, sampleRate: Int): Result {
        val samples = requireNotNull(samples) { "PCM samples are required" }
        require(sampleRate > 0) { "Sample rate must be positive" }

        var sumSquares = 0.0
        var peak = 0
        var clippedSamples = 0
        for (sample in samples) {
            val amplitude: Int = abs(sample.toInt())
            sumSquares += sample.toDouble() * sample
            peak = max(peak, amplitude)
            if (amplitude >= CLIPPING_AMPLITUDE) {
                clippedSamples++
            }
        }

        val duration = samples.size / sampleRate.toDouble()
        val rms = if (samples.size == 0) 0.0 else sqrt(sumSquares / samples.size)
        val rmsDbfs = toDbfs(rms)
        val peakDbfs = toDbfs(peak.toDouble())
        val clippingRatio = if (samples.size == 0)
            0.0
        else
            clippedSamples / samples.size.toDouble()

        var reason = Reason.NONE
        if (duration < MIN_DURATION_SECONDS) {
            reason = Reason.TOO_SHORT
        } else if (duration > MAX_DURATION_SECONDS) {
            reason = Reason.TOO_LONG
        } else if (rmsDbfs < QUIET_THRESHOLD_DBFS) {
            reason = Reason.TOO_QUIET
        } else if (clippingRatio >= CLIPPING_RATIO_LIMIT) {
            reason = Reason.CLIPPED
        }
        return VoiceprintAudioQuality.Result(duration, rmsDbfs, peakDbfs, clippingRatio, reason)
    }

    private fun toDbfs(amplitude: Double): Double {
        if (amplitude <= 0.0) {
            return Double.Companion.NEGATIVE_INFINITY
        }
        return 20.0 * log10(amplitude / PCM_FULL_SCALE)
    }

    enum class Reason {
        NONE,
        TOO_SHORT,
        TOO_LONG,
        TOO_QUIET,
        CLIPPED
    }

    class Result internal constructor(
        val durationSeconds: Double,
        val rmsDbfs: Double,
        val peakDbfs: Double,
        val clippingRatio: Double,
        val reason: Reason?
    ) {
        val isAccepted: Boolean
            get() = reason == Reason.NONE
    }
}
