package com.huimei.voice;

import android.app.Activity;

public enum DemoDestination {
    VOICE_RECOGNITION(
            R.string.demo_voice_title,
            R.string.demo_voice_summary,
            VoiceRecognitionActivity.class),
    LOTTIE_AVATAR(
            R.string.demo_lottie_title,
            R.string.demo_lottie_summary,
            LottieDemoActivity.class),
    VOICE_AVATAR(
            R.string.demo_voice_avatar_title,
            R.string.demo_voice_avatar_summary,
            VoiceAvatarActivity.class),
    ARBITRARY_TTS_AVATAR(
            R.string.demo_tts_avatar_title,
            R.string.demo_tts_avatar_summary,
            ArbitraryTtsActivity.class);

    private final int titleResourceId;
    private final int summaryResourceId;
    private final Class<? extends Activity> targetActivity;

    DemoDestination(
            int titleResourceId,
            int summaryResourceId,
            Class<? extends Activity> targetActivity) {
        this.titleResourceId = titleResourceId;
        this.summaryResourceId = summaryResourceId;
        this.targetActivity = targetActivity;
    }

    public int titleResourceId() {
        return titleResourceId;
    }

    public int summaryResourceId() {
        return summaryResourceId;
    }

    public Class<? extends Activity> targetActivity() {
        return targetActivity;
    }

    public String targetActivityClassName() {
        return targetActivity.getName();
    }
}
