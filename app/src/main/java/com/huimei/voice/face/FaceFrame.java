package com.huimei.voice.face;

import java.util.Arrays;

public final class FaceFrame {
    private final int width;
    private final int height;
    private final byte[] rgba;

    public FaceFrame(int width, int height, byte[] rgba) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Face frame size must be positive");
        }
        long expectedLength = width * (long) height * 4L;
        if (rgba == null || expectedLength > Integer.MAX_VALUE
                || rgba.length != (int) expectedLength) {
            throw new IllegalArgumentException("Face frame RGBA byte count is invalid");
        }
        this.width = width;
        this.height = height;
        this.rgba = Arrays.copyOf(rgba, rgba.length);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public byte[] rgba() {
        return Arrays.copyOf(rgba, rgba.length);
    }
}
