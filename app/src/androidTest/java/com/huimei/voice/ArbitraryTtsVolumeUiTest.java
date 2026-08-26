package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.huimei.voice.tts.TtsVolume;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ArbitraryTtsVolumeUiTest {
    @Test
    public void exposesThreeLevelsAndDefaultsToEnhanced() {
        try (ActivityScenario<ArbitraryTtsActivity> scenario =
                     ActivityScenario.launch(ArbitraryTtsActivity.class)) {
            scenario.onActivity(activity -> {
                int spinnerId = activity.getResources().getIdentifier(
                        "tts_volume_spinner",
                        "id",
                        activity.getPackageName());
                assertNotEquals("volume spinner is required", 0, spinnerId);
                Spinner spinner = activity.findViewById(spinnerId);
                assertNotNull(spinner);
                assertEquals(3, spinner.getCount());
                assertEquals(TtsVolume.ENHANCED.ordinal(), spinner.getSelectedItemPosition());
            });
        }
    }
}
