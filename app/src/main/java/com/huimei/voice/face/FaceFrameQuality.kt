package com.huimei.voice.face

import kotlin.Boolean
import kotlin.Int
import kotlin.String

object FaceFrameQuality {
    const val MIN_CONFIDENCE: Float = 0.90f
    const val MIN_FACE_WIDTH_RATIO: Float = 0.20f
    const val MIN_BRIGHTNESS: Double = 45.0
    const val MAX_BRIGHTNESS: Double = 220.0
    const val MIN_SHARPNESS: Double = 60.0

    @JvmStatic
    fun evaluate(
        faceCount: Int,
        confidence: Float,
        faceWidthRatio: Float,
        brightness: Double,
        sharpness: Double
    ): Decision {
        if (faceCount <= 0) {
            return Decision(Reason.NO_FACE)
        }
        if (faceCount > 1) {
            return Decision(Reason.MULTIPLE_FACES)
        }
        if (!confidence.isFinite() || confidence < MIN_CONFIDENCE) {
            return Decision(Reason.LOW_CONFIDENCE)
        }
        if (!faceWidthRatio.isFinite()
            || faceWidthRatio < MIN_FACE_WIDTH_RATIO
        ) {
            return Decision(Reason.FACE_TOO_SMALL)
        }
        if (!brightness.isFinite() || brightness < MIN_BRIGHTNESS) {
            return Decision(Reason.TOO_DARK)
        }
        if (brightness > MAX_BRIGHTNESS) {
            return Decision(Reason.TOO_BRIGHT)
        }
        if (!sharpness.isFinite() || sharpness < MIN_SHARPNESS) {
            return Decision(Reason.BLURRY)
        }
        return Decision(Reason.ACCEPTED)
    }

    enum class Reason(message: String) {
        ACCEPTED("画面质量合格"),
        NO_FACE("未检测到人脸"),
        MULTIPLE_FACES("请保证画面中只有一张人脸"),
        LOW_CONFIDENCE("人脸检测置信度不足"),
        FACE_TOO_SMALL("请靠近摄像头"),
        TOO_DARK("画面过暗"),
        TOO_BRIGHT("画面过亮"),
        BLURRY("画面模糊");

        private val message: String?

        init {
            this.message = message
        }

        fun message(): String? {
            return message
        }
    }

    class Decision internal constructor(private val reason: Reason?) {
        fun accepted(): Boolean {
            return reason == Reason.ACCEPTED
        }

        fun reason(): Reason? {
            return reason
        }
    }
}
