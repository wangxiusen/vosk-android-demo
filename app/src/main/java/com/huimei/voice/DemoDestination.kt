package com.huimei.voice

import android.app.Activity

enum class DemoDestination(
    private val titleResourceId: Int,
    private val summaryResourceId: Int,
    private val targetActivity: Class<out Activity?>
) {
    VOICE_RECOGNITION(
        R.string.demo_voice_title,
        R.string.demo_voice_summary,
        VoiceRecognitionActivity::class.java
    ),
    LOTTIE_AVATAR(
        R.string.demo_lottie_title,
        R.string.demo_lottie_summary,
        LottieDemoActivity::class.java
    ),
    VOICE_AVATAR(
        R.string.demo_voice_avatar_title,
        R.string.demo_voice_avatar_summary,
        VoiceAvatarActivity::class.java
    ),
    ARBITRARY_TTS_AVATAR(
        R.string.demo_tts_avatar_title,
        R.string.demo_tts_avatar_summary,
        ArbitraryTtsActivity::class.java
    ),
    SHERPA_VOICEPRINT(
        R.string.demo_sherpa_voiceprint_title,
        R.string.demo_sherpa_voiceprint_summary,
        SherpaVoiceprintActivity::class.java
    ),
    VOSK_VOICEPRINT(
        R.string.demo_vosk_voiceprint_title,
        R.string.demo_vosk_voiceprint_summary,
        VoskVoiceprintActivity::class.java
    ),
    FACE_RECOGNITION(
        R.string.demo_face_title,
        R.string.demo_face_summary,
        FaceRecognitionActivity::class.java
    ),
    FUNCTIONGEMMA_COMMAND(
        R.string.demo_functiongemma_title,
        R.string.demo_functiongemma_summary,
        FunctionGemmaCommandActivity::class.java
    );

    fun titleResourceId(): Int {
        return titleResourceId
    }

    fun summaryResourceId(): Int {
        return summaryResourceId
    }

    fun targetActivity(): Class<out Activity?> {
        return targetActivity
    }

    fun targetActivityClassName(): String {
        return targetActivity.getName()
    }
}
