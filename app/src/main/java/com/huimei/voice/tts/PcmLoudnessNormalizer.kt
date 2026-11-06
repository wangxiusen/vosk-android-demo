package com.huimei.voice.tts

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class PcmLoudnessNormalizer private constructor() {
    class Result private constructor(
        private val activeAudio: Boolean,
        private val gain: Float,
        private val inputActiveRmsDbfs: Float,
        private val inputPeakDbfs: Float,
        private val outputActiveRmsDbfs: Float,
        private val outputPeak: Float,
        private val peakLimited: Boolean
    ) {
        fun hasActiveAudio(): Boolean = activeAudio
        fun gain(): Float = gain
        fun inputActiveRmsDbfs(): Float = inputActiveRmsDbfs
        fun inputPeakDbfs(): Float = inputPeakDbfs
        fun outputActiveRmsDbfs(): Float = outputActiveRmsDbfs
        fun outputPeak(): Float = outputPeak
        fun outputPeakDbfs(): Float = linearToDbfs(outputPeak)
        fun gainDb(): Float = linearToDbfs(gain)
        fun isPeakLimited(): Boolean = peakLimited

        companion object {
            fun create(
                activeAudio: Boolean,
                gain: Float,
                inputActiveRmsDbfs: Float,
                inputPeakDbfs: Float,
                outputActiveRmsDbfs: Float,
                outputPeak: Float,
                peakLimited: Boolean
            ) = Result(activeAudio, gain, inputActiveRmsDbfs, inputPeakDbfs, outputActiveRmsDbfs, outputPeak, peakLimited)
        }
    }

    companion object {
        private const val FLOOR_DBFS = -120.0f
        private val SILENCE_THRESHOLD = dbfsToLinear(-45.0f)
        private val MAX_GAIN = dbfsToLinear(18.0f)
        private val PEAK_LIMIT = dbfsToLinear(-1.0f)

        @JvmStatic
        fun analyze(samples: FloatArray, targetRmsDbfs: Float): Result {
            var activeSquareSum = 0.0f
            var activeCount = 0
            var peak = 0.0f
            for (sample in samples) {
                if (!sample.isFinite()) continue
                val absolute = abs(sample)
                if (absolute > peak) peak = absolute
                if (absolute >= SILENCE_THRESHOLD) {
                    activeSquareSum += sample * sample
                    activeCount++
                }
            }

            if (activeCount == 0) {
                return Result.create(false, 1.0f, FLOOR_DBFS, linearToDbfs(peak), FLOOR_DBFS, peak, false)
            }

            val activeRms = sqrt(activeSquareSum / activeCount)
            val requestedGain = dbfsToLinear(targetRmsDbfs) / activeRms
            val levelLimitedGain = min(requestedGain, MAX_GAIN)
            val peakSafeGain = if (peak > 0.0f) PEAK_LIMIT / peak else MAX_GAIN
            val gain = min(levelLimitedGain, peakSafeGain)
            val peakLimited = peakSafeGain < levelLimitedGain - 0.0001f
            return Result.create(
                true, gain, linearToDbfs(activeRms), linearToDbfs(peak),
                linearToDbfs(activeRms * gain), peak * gain, peakLimited
            )
        }

        @JvmStatic
        fun copyWithGain(
            input: FloatArray,
            offset: Int,
            length: Int,
            startGain: Float,
            endGain: Float,
            output: FloatArray
        ) {
            if (offset < 0 || length < 0 || offset + length > input.size || output.size < length) {
                throw IllegalArgumentException("Invalid PCM buffer range")
            }
            var gain = if (length <= 1) endGain else startGain
            val gainStep = if (length <= 1) 0.0f else (endGain - startGain) / (length - 1)
            for (index in 0 until length) {
                val sample = input[offset + index].takeIf(Float::isFinite) ?: 0.0f
                output[index] = sample * gain
                gain += gainStep
            }
        }

        private fun dbfsToLinear(dbfs: Float): Float = 10.0.pow((dbfs / 20.0f).toDouble()).toFloat()

        private fun linearToDbfs(value: Float): Float =
            if (value > 0.0f && value.isFinite()) (20.0 * log10(value.toDouble())).toFloat() else FLOOR_DBFS
    }
}
