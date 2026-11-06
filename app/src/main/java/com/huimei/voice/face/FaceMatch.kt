package com.huimei.voice.face

import kotlin.Boolean

class FaceMatch {
    val profile: FaceProfile?
    val score: Float
    val isAccepted: Boolean

    constructor(profile: FaceProfile, score: Float, accepted: Boolean) {
        requireNotNull(profile) { "Face match profile is required" }
        require(score.isFinite()) { "Face match score must be finite" }
        this.profile = profile
        this.score = score
        this.isAccepted = accepted
    }

    private constructor() {
        profile = null
        score = 0f
        this.isAccepted = false
    }

    val isUnknown: Boolean
        get() = profile == null

    companion object {
        @JvmStatic
        fun unknown(): FaceMatch {
            return FaceMatch()
        }
    }
}
