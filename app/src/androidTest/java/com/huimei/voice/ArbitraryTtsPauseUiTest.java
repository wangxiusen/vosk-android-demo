package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.huimei.voice.tts.TtsPause;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ArbitraryTtsPauseUiTest {
    @Test
    public void defaultsToNaturalPunctuationPause() {
        try (ActivityScenario<ArbitraryTtsActivity> scenario =
                     ActivityScenario.launch(ArbitraryTtsActivity.class)) {
            scenario.onActivity(activity -> {
                int pauseSpinnerId = activity.getResources().getIdentifier(
                        "tts_pause_spinner",
                        "id",
                        activity.getPackageName());
                assertNotEquals("pause spinner is required", 0, pauseSpinnerId);
                Spinner pauseSpinner = activity.findViewById(pauseSpinnerId);
                assertNotNull(pauseSpinner);
                assertEquals(TtsPause.NATURAL.ordinal(), pauseSpinner.getSelectedItemPosition());
            });
        }
    }
}
