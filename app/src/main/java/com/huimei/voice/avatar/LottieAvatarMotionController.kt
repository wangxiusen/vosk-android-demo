package com.huimei.voice.avatar

import android.graphics.PointF
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieFrameInfo
import com.airbnb.lottie.value.LottieValueCallback
import com.airbnb.lottie.value.ScaleXY

class LottieAvatarMotionController(private val animationView: LottieAnimationView) {
    private val density: Float

    init {
        density = animationView.getResources().getDisplayMetrics().density
    }

    fun showIdleEyesOnly() {
        showMouth(MouthShape.CLOSED)
        animationView.addValueCallback<PointF?>(
            KeyPath(CHARACTER_CONTROLLER),
            LottieProperty.TRANSFORM_POSITION,
            LottieValueCallback<PointF?>(PointF(450.0f * density, 450.0f * density))
        )
        animationView.addValueCallback<ScaleXY?>(
            KeyPath(CHARACTER_CONTROLLER),
            LottieProperty.TRANSFORM_SCALE,
            LottieValueCallback<ScaleXY?>(ScaleXY(1.0f, 1.0f))
        )
    }

    fun showMouth(shape: MouthShape?) {
        setOpacity(MOUTH_CLOSED, if (shape == MouthShape.CLOSED) 100 else 0)
        setOpacity(MOUTH_SMALL, if (shape == MouthShape.SMALL) 100 else 0)
        setOpacity(MOUTH_MEDIUM, if (shape == MouthShape.MEDIUM) 100 else 0)
        setOpacity(MOUTH_OPEN, if (shape == MouthShape.OPEN) 100 else 0)
    }

    fun showTalking() {
        for (layer in MOUTH_LAYERS) {
            animationView.clearValueCallback<Int?>(
                KeyPath(layer),
                LottieProperty.TRANSFORM_OPACITY
            )
        }
        animationView.clearValueCallback<PointF?>(
            KeyPath(CHARACTER_CONTROLLER),
            LottieProperty.TRANSFORM_POSITION
        )
        animationView.clearValueCallback<ScaleXY?>(
            KeyPath(CHARACTER_CONTROLLER),
            LottieProperty.TRANSFORM_SCALE
        )
    }

    private fun setOpacity(layer: String, opacity: Int) {
        animationView.addValueCallback<Int?>(
            KeyPath(layer),
            LottieProperty.TRANSFORM_OPACITY,
            object : LottieValueCallback<Int?>() {
                override fun getValue(frameInfo: LottieFrameInfo<Int?>?): Int? {
                    return opacity
                }
            })
    }

    companion object {
        private const val MOUTH_CLOSED = "mouth_closed"
        private const val MOUTH_SMALL = "mouth_small"
        private const val MOUTH_MEDIUM = "mouth_medium"
        private const val MOUTH_OPEN = "mouth_open"
        private const val CHARACTER_CONTROLLER = "character_controller"
        private val MOUTH_LAYERS = arrayOf<String?>(
            MOUTH_CLOSED,
            MOUTH_SMALL,
            MOUTH_MEDIUM,
            MOUTH_OPEN
        )
    }
}
