package com.huimei.voice.face

class FaceFrame(width: Int, height: Int, rgba: ByteArray) {
    private val width: Int
    private val height: Int
    private val rgba: ByteArray

    init {
        require(!(width <= 0 || height <= 0)) { "Face frame size must be positive" }
        val expectedLength = width * height.toLong() * 4L
        require(!(rgba == null || expectedLength > Int.Companion.MAX_VALUE || rgba.size != expectedLength.toInt())) { "Face frame RGBA byte count is invalid" }
        this.width = width
        this.height = height
        this.rgba = rgba.copyOf(rgba.size)
    }

    fun width(): Int {
        return width
    }

    fun height(): Int {
        return height
    }

    fun rgba(): ByteArray {
        return rgba.copyOf(rgba.size)
    }
}
