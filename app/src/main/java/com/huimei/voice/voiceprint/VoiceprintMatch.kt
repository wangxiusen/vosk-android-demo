package com.huimei.voice.voiceprint

class VoiceprintMatch private constructor(
    val profile: VoiceprintProfile?,
    val score: Float,
    val threshold: Float,
    val isAccepted: Boolean
) {
    companion object {
        @JvmStatic
        fun accepted(profile: VoiceprintProfile?, score: Float, threshold: Float): VoiceprintMatch {
            return VoiceprintMatch(profile, score, threshold, true)
        }

        @JvmStatic
        fun rejected(score: Float, threshold: Float): VoiceprintMatch {
            return VoiceprintMatch(null, score, threshold, false)
        }
    }
}
