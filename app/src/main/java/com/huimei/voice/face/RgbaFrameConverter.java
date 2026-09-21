package com.huimei.voice.face;

public final class RgbaFrameConverter {
    private static final int RGBA_BYTES_PER_PIXEL = 4;

    private RgbaFrameConverter() {
    }

    public static FaceFrame convert(
            byte[] source,
            int width,
            int height,
            int rowStride,
            int pixelStride,
            int rotationDegrees) {
        if (source == null || width <= 0 || height <= 0
                || pixelStride < RGBA_BYTES_PER_PIXEL
                || rowStride < width * pixelStride) {
            throw new IllegalArgumentException("Invalid RGBA image plane");
        }
        if (rotationDegrees != 0 && rotationDegrees != 90
                && rotationDegrees != 180 && rotationDegrees != 270) {
            throw new IllegalArgumentException("Unsupported image rotation");
        }
        long lastPixelEnd = (height - 1L) * rowStride
                + (width - 1L) * pixelStride + RGBA_BYTES_PER_PIXEL;
        if (lastPixelEnd > source.length) {
            throw new IllegalArgumentException("RGBA image plane is truncated");
        }

        int outputWidth = rotationDegrees == 90 || rotationDegrees == 270
                ? height : width;
        int outputHeight = rotationDegrees == 90 || rotationDegrees == 270
                ? width : height;
        byte[] output = new byte[outputWidth * outputHeight * RGBA_BYTES_PER_PIXEL];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int outputX;
                int outputY;
                switch (rotationDegrees) {
                    case 90:
                        outputX = height - 1 - y;
                        outputY = x;
                        break;
                    case 180:
                        outputX = width - 1 - x;
                        outputY = height - 1 - y;
                        break;
                    case 270:
                        outputX = y;
                        outputY = width - 1 - x;
                        break;
                    default:
                        outputX = x;
                        outputY = y;
                        break;
                }
                int sourceOffset = y * rowStride + x * pixelStride;
                int outputOffset = (outputY * outputWidth + outputX) * RGBA_BYTES_PER_PIXEL;
                System.arraycopy(
                        source, sourceOffset, output, outputOffset, RGBA_BYTES_PER_PIXEL);
            }
        }
        return new FaceFrame(outputWidth, outputHeight, output);
    }
}
