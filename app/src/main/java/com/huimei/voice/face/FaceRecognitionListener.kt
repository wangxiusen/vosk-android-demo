package com.huimei.voice.face

interface FaceRecognitionListener {
    fun onStateChanged(state: FaceRecognitionController.State?)

    fun onModelReady(loadMillis: Long, dimension: Int)

    fun onProfilesChanged(profiles: MutableList<FaceProfile>)

    fun onEnrollmentProgress(accepted: Int, required: Int)

    fun onFrameRejected(reason: FaceFrameQuality.Reason?)

    fun onCandidate(match: FaceMatch?, inferenceMillis: Long)

    fun onMatchConfirmed(match: FaceMatch?)

    fun onLog(message: String?)

    fun onError(message: String?)
}
