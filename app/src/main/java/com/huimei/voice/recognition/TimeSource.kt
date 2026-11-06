package com.huimei.voice.recognition

fun interface TimeSource {
    fun nowMillis(): Long
}
