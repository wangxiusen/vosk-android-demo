package com.huimei.voice.functiongemma

import java.util.Locale

class FunctionGemmaModelManifest private constructor() {
    class Entry(
        private val version: String,
        private val filename: String,
        private val sizeBytes: Long,
        sha256: String
    ) {
        private val sha256 = sha256.lowercase(Locale.ROOT)

        init {
            require(version.isNotBlank() && filename.isNotBlank()) { "version and filename must not be blank" }
            require(sizeBytes > 0L) { "sizeBytes must be positive" }
            require(this.sha256.matches(Regex("[0-9a-f]{64}"))) { "sha256 must be 64 lowercase hex characters" }
        }

        fun version(): String = version
        fun filename(): String = filename
        fun sizeBytes(): Long = sizeBytes
        fun sha256(): String = sha256
    }

    companion object {
        @JvmField
        val MODEL = Entry(
            "mobile-actions-38942192",
            "mobile_actions_q8_ekv1024.litertlm",
            288_964_608L,
            "33e295cbd996b419bb1de8f3f85c5b6b01ee058a2c89bdb2173cf3e6ff4ce9d0"
        )
    }
}
