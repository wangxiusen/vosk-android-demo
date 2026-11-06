package com.huimei.voice.face

interface FaceEngine : AutoCloseable {
    @kotlin.Throws(Exception::class)
    fun initialize()

    fun dimension(): Int

    @kotlin.Throws(Exception::class)
    fun analyze(frame: FaceFrame): FaceObservation

    override fun close()
}
