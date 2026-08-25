package com.huimei.voice.avatar;

import android.graphics.PointF;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieFrameInfo;
import com.airbnb.lottie.value.ScaleXY;
import com.airbnb.lottie.value.LottieValueCallback;

public final class LottieAvatarMotionController {
    private static final String MOUTH_CLOSED = "mouth_closed";
    private static final String MOUTH_SMALL = "mouth_small";
    private static final String MOUTH_MEDIUM = "mouth_medium";
    private static final String MOUTH_OPEN = "mouth_open";
    private static final String CHARACTER_CONTROLLER = "character_controller";
    private static final String[] MOUTH_LAYERS = {
            MOUTH_CLOSED,
            MOUTH_SMALL,
            MOUTH_MEDIUM,
            MOUTH_OPEN
    };

    private final LottieAnimationView animationView;
    private final float density;

    public LottieAvatarMotionController(LottieAnimationView animationView) {
        this.animationView = animationView;
        density = animationView.getResources().getDisplayMetrics().density;
    }

    public void showIdleEyesOnly() {
        setOpacity(MOUTH_CLOSED, 100);
        setOpacity(MOUTH_SMALL, 0);
        setOpacity(MOUTH_MEDIUM, 0);
        setOpacity(MOUTH_OPEN, 0);
        animationView.addValueCallback(
                new KeyPath(CHARACTER_CONTROLLER),
                LottieProperty.TRANSFORM_POSITION,
                new LottieValueCallback<>(new PointF(450.0f * density, 450.0f * density)));
        animationView.addValueCallback(
                new KeyPath(CHARACTER_CONTROLLER),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(1.0f, 1.0f)));
    }

    public void showTalking() {
        for (String layer : MOUTH_LAYERS) {
            animationView.clearValueCallback(
                    new KeyPath(layer),
                    LottieProperty.TRANSFORM_OPACITY);
        }
        animationView.clearValueCallback(
                new KeyPath(CHARACTER_CONTROLLER),
                LottieProperty.TRANSFORM_POSITION);
        animationView.clearValueCallback(
                new KeyPath(CHARACTER_CONTROLLER),
                LottieProperty.TRANSFORM_SCALE);
    }

    private void setOpacity(String layer, int opacity) {
        animationView.addValueCallback(
                new KeyPath(layer),
                LottieProperty.TRANSFORM_OPACITY,
                new LottieValueCallback<Integer>() {
                    @Override
                    public Integer getValue(LottieFrameInfo<Integer> frameInfo) {
                        return opacity;
                    }
                });
    }
}
