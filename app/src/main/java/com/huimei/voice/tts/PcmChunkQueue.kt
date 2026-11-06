package com.huimei.voice.tts

import java.util.ArrayDeque

class PcmChunkQueue(private val capacity: Int) {
    private val lock = Object()
    private val chunks = ArrayDeque<FloatArray>()
    private var finished = false
    private var canceled = false

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun offer(samples: FloatArray?): Boolean = synchronized(lock) {
        if (samples == null || samples.isEmpty() || finished || canceled || chunks.size >= capacity) {
            return@synchronized false
        }
        chunks.addLast(samples.copyOf())
        lock.notifyAll()
        true
    }

    @Throws(InterruptedException::class)
    fun take(): FloatArray? = synchronized(lock) {
        while (chunks.isEmpty() && !finished && !canceled) lock.wait()
        when {
            canceled -> null
            chunks.isNotEmpty() -> chunks.removeFirst()
            else -> END_OF_STREAM
        }
    }

    fun finish() = synchronized(lock) {
        finished = true
        lock.notifyAll()
    }

    fun cancel() = synchronized(lock) {
        canceled = true
        chunks.clear()
        lock.notifyAll()
    }

    companion object {
        @JvmField
        val END_OF_STREAM = FloatArray(0)
    }
}
