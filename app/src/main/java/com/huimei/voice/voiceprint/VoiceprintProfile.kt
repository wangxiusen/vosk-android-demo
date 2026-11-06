package com.huimei.voice.voiceprint

import java.util.Objects

class VoiceprintProfile(
    id: String,
    displayName: String,
    sampleCount: Int,
    createdAtMillis: Long
) {
    val id: String
    val displayName: String
    val sampleCount: Int
    val createdAtMillis: Long

    init {
        require(!(id == null || id.trim { it <= ' ' }.isEmpty())) { "Profile ID is required" }
        require(!(displayName == null || displayName.trim { it <= ' ' }
            .isEmpty())) { "Display name is required" }
        require(sampleCount >= 0) { "Sample count cannot be negative" }
        this.id = id
        this.displayName = displayName.trim { it <= ' ' }
        this.sampleCount = sampleCount
        this.createdAtMillis = createdAtMillis
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is VoiceprintProfile) {
            return false
        }
        val that = other
        return id == that.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return displayName
    }
}
