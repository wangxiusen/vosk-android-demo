package com.huimei.voice.avatar

import kotlin.math.sqrt

object AudioEnergyMouthMapper {
    private const val CLOSED_MAX_RMS = 0.012
    private const val SMALL_MAX_RMS = 0.04
    private const val MEDIUM_MAX_RMS = 0.10

    @JvmStatic
    fun shapeFor(samples: FloatArray, offset: Int, length: Int): MouthShape {
        if (length <= 0) {
            return MouthShape.CLOSED
        }
        var sumSquares = 0.0
        val end = offset + length
        for (index in offset..<end) {
            val sample = samples[index].toDouble()
            sumSquares += sample * sample
        }
        val rms: Double = sqrt(sumSquares / length)
        if (rms <= CLOSED_MAX_RMS) {
            return MouthShape.CLOSED
        }
        if (rms <= SMALL_MAX_RMS) {
            return MouthShape.SMALL
        }
        if (rms <= MEDIUM_MAX_RMS) {
            return MouthShape.MEDIUM
        }
        return MouthShape.OPEN
    }
}
