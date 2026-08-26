package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ArbitraryTtsVoiceUiTest {
    @Test
    public void chineseVoiceSpinnerOffersTenFemaleVoices() {
        try (ActivityScenario<ArbitraryTtsActivity> scenario =
                     ActivityScenario.launch(ArbitraryTtsActivity.class)) {
            scenario.onActivity(activity -> {
                Spinner voiceSpinner = activity.findViewById(R.id.tts_voice_spinner);
                assertEquals(10, voiceSpinner.getCount());
                for (int index = 0; index < voiceSpinner.getCount(); index++) {
                    assertTrue(voiceSpinner.getItemAtPosition(index)
                            .toString()
                            .contains("中文女声"));
                }
            });
        }
    }
}
