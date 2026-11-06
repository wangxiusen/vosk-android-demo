package com.huimei.voice.functiongemma

class LatestOperationGate {
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun isCurrent(token: Long): Boolean = !closed && token == generation

    @Synchronized
    fun close() {
        if (!closed) {
            closed = true
            generation++
        }
    }
}
