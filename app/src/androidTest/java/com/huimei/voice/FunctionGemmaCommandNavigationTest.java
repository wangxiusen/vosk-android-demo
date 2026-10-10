package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FunctionGemmaCommandNavigationTest {
    @Test
    public void eighthDemoPageShowsTheFunctionGemmaTitle() {
        assertEquals(8, DemoDestination.values().length);
        assertEquals(
                FunctionGemmaCommandActivity.class,
                DemoDestination.values()[7].targetActivity());

        try (ActivityScenario<FunctionGemmaCommandActivity> scenario =
                     ActivityScenario.launch(FunctionGemmaCommandActivity.class)) {
            scenario.onActivity(activity -> assertEquals(
                    "FunctionGemma 语音指令 Demo",
                    activity.<TextView>findViewById(R.id.functiongemma_title)
                            .getText().toString()));
        }
    }
}
