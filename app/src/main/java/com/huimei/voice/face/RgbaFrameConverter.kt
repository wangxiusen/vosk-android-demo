package com.huimei.voice.face

object RgbaFrameConverter {
    private const val RGBA_BYTES_PER_PIXEL = 4

    @JvmStatic
    fun convert(
        source: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): FaceFrame {
        require(!(source == null || width <= 0 || height <= 0 || pixelStride < RGBA_BYTES_PER_PIXEL || rowStride < width * pixelStride)) { "Invalid RGBA image plane" }
        require(!(rotationDegrees != 0 && rotationDegrees != 90 && rotationDegrees != 180 && rotationDegrees != 270)) { "Unsupported image rotation" }
        val lastPixelEnd =
            ((height - 1L) * rowStride + (width - 1L) * pixelStride + RGBA_BYTES_PER_PIXEL)
        require(lastPixelEnd <= source.size) { "RGBA image plane is truncated" }

        val outputWidth = if (rotationDegrees == 90 || rotationDegrees == 270)
            height
        else
            width
        val outputHeight = if (rotationDegrees == 90 || rotationDegrees == 270)
            width
        else
            height
        val output = ByteArray(outputWidth * outputHeight * RGBA_BYTES_PER_PIXEL)
        for (y in 0..<height) {
            for (x in 0..<width) {
                val outputX: Int
                val outputY: Int
                when (rotationDegrees) {
                    90 -> {
                        outputX = height - 1 - y
                        outputY = x
                    }

                    180 -> {
                        outputX = width - 1 - x
                        outputY = height - 1 - y
                    }

                    270 -> {
                        outputX = y
                        outputY = width - 1 - x
                    }

                    else -> {
                        outputX = x
                        outputY = y
                    }
                }
                val sourceOffset = y * rowStride + x * pixelStride
                val outputOffset = (outputY * outputWidth + outputX) * RGBA_BYTES_PER_PIXEL
                System.arraycopy(
                    source, sourceOffset, output, outputOffset, RGBA_BYTES_PER_PIXEL
                )
            }
        }
        return FaceFrame(outputWidth, outputHeight, output)
    }
}
