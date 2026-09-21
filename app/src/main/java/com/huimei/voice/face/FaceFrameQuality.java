package com.huimei.voice.face;

public final class FaceFrameQuality {
    public static final float MIN_CONFIDENCE = 0.90f;
    public static final float MIN_FACE_WIDTH_RATIO = 0.20f;
    public static final double MIN_BRIGHTNESS = 45d;
    public static final double MAX_BRIGHTNESS = 220d;
    public static final double MIN_SHARPNESS = 60d;

    public enum Reason {
        ACCEPTED("画面质量合格"),
        NO_FACE("未检测到人脸"),
        MULTIPLE_FACES("请保证画面中只有一张人脸"),
        LOW_CONFIDENCE("人脸检测置信度不足"),
        FACE_TOO_SMALL("请靠近摄像头"),
        TOO_DARK("画面过暗"),
        TOO_BRIGHT("画面过亮"),
        BLURRY("画面模糊");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private FaceFrameQuality() {
    }

    public static Decision evaluate(
            int faceCount,
            float confidence,
            float faceWidthRatio,
            double brightness,
            double sharpness) {
        if (faceCount <= 0) {
            return new Decision(Reason.NO_FACE);
        }
        if (faceCount > 1) {
            return new Decision(Reason.MULTIPLE_FACES);
        }
        if (!Float.isFinite(confidence) || confidence < MIN_CONFIDENCE) {
            return new Decision(Reason.LOW_CONFIDENCE);
        }
        if (!Float.isFinite(faceWidthRatio)
                || faceWidthRatio < MIN_FACE_WIDTH_RATIO) {
            return new Decision(Reason.FACE_TOO_SMALL);
        }
        if (!Double.isFinite(brightness) || brightness < MIN_BRIGHTNESS) {
            return new Decision(Reason.TOO_DARK);
        }
        if (brightness > MAX_BRIGHTNESS) {
            return new Decision(Reason.TOO_BRIGHT);
        }
        if (!Double.isFinite(sharpness) || sharpness < MIN_SHARPNESS) {
            return new Decision(Reason.BLURRY);
        }
        return new Decision(Reason.ACCEPTED);
    }

    public static final class Decision {
        private final Reason reason;

        private Decision(Reason reason) {
            this.reason = reason;
        }

        public boolean accepted() {
            return reason == Reason.ACCEPTED;
        }

        public Reason reason() {
            return reason;
        }
    }
}
