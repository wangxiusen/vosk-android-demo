package com.huimei.voice.face

import java.util.Objects

class FaceProfile(id: String, displayName: String, sampleCount: Int, createdAtMillis: Long) {
    val id: String
    val displayName: String
    val sampleCount: Int
    val createdAtMillis: Long

    init {
        require(!(id == null || id.trim { it <= ' ' }.isEmpty())) { "Face profile ID is required" }
        require(!(displayName == null || displayName.trim { it <= ' ' }
            .isEmpty())) { "Face profile display name is required" }
        require(sampleCount > 0) { "Face profile sample count must be positive" }
        require(createdAtMillis >= 0L) { "Face profile creation time is invalid" }
        this.id = id.trim { it <= ' ' }
        this.displayName = displayName.trim { it <= ' ' }
        this.sampleCount = sampleCount
        this.createdAtMillis = createdAtMillis
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is FaceProfile) {
            return false
        }
        val value = other
        return sampleCount == value.sampleCount && createdAtMillis == value.createdAtMillis && id == value.id
                && displayName == value.displayName
    }

    override fun hashCode(): Int {
        return Objects.hash(id, displayName, sampleCount, createdAtMillis)
    }

    override fun toString(): String {
        return displayName
    }
}
