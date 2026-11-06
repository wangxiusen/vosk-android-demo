package com.huimei.voice.voiceprint

interface VoiceprintListener {
    fun onStateChanged(state: VoiceprintController.State?)

    fun onModelReady(loadMillis: Long, dimension: Int)

    fun onProfilesChanged(profiles: MutableList<VoiceprintProfile>)

    fun onEnrollmentProgress(
        acceptedSamples: Int,
        requiredSamples: Int,
        quality: VoiceprintAudioQuality.Result
    )

    fun onAudioRejected(quality: VoiceprintAudioQuality.Result)

    fun onMatch(match: VoiceprintMatch, inferenceMillis: Long)

    fun onError(message: String?)

    fun onLog(message: String?)
}
