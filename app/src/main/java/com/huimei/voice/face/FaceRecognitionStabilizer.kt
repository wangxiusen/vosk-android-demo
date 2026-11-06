package com.huimei.voice.face

import java.util.Optional

class FaceRecognitionStabilizer(requiredFrames: Int) {
    private val requiredFrames: Int
    private var candidateId: String? = null
    private var consecutiveFrames = 0

    init {
        require(requiredFrames > 0) { "Required face frame count must be positive" }
        this.requiredFrames = requiredFrames
    }

    fun accept(match: FaceMatch): Optional<FaceMatch> {
        requireNotNull(match) { "Face match is required" }
        if (!match.isAccepted || match.isUnknown) {
            reset()
            return Optional.empty()
        }

        val profileId = match.profile!!.id
        if (profileId != candidateId) {
            candidateId = profileId
            consecutiveFrames = 1
        } else {
            consecutiveFrames++
        }
        if (consecutiveFrames < requiredFrames) {
            return Optional.empty()
        }
        reset()
        return Optional.of(match)
    }

    fun reset() {
        candidateId = null
        consecutiveFrames = 0
    }
}
