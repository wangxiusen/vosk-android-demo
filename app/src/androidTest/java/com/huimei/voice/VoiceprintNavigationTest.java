package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class VoiceprintNavigationTest {

    @Rule
    public final GrantPermissionRule microphonePermission =
            GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    @Test
    public void sherpaPageShowsEngineSentenceAndInitialLoadingState() {
        assertInitialPage(
                SherpaVoiceprintActivity.class,
                "Sherpa-ONNX CAM++");
    }

    @Test
    public void voskPageShowsEngineSentenceAndInitialLoadingState() {
        assertInitialPage(
                VoskVoiceprintActivity.class,
                "Vosk x-vector");
    }

    @Test
    public void backgroundAndRecreateDuringModelWorkDoesNotCrash() {
        try (ActivityScenario<SherpaVoiceprintActivity> scenario =
                     ActivityScenario.launch(SherpaVoiceprintActivity.class)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
            scenario.onActivity(activity -> assertTrue(
                    activity.<TextView>findViewById(R.id.voiceprint_fixed_phrase)
                            .getText().toString().contains("潓美医疗声纹测试")));
        }
    }

    private static <T extends BaseVoiceprintActivity> void assertInitialPage(
            Class<T> activityClass,
            String engineLabel) {
        try (ActivityScenario<T> scenario = ActivityScenario.launch(activityClass)) {
            scenario.onActivity(activity -> {
                assertEquals(
                        engineLabel,
                        activity.<TextView>findViewById(R.id.voiceprint_engine_title)
                                .getText().toString());
                assertEquals(
                        "潓美医疗声纹测试，一二三四五六七八九十，现在开始识别我的声音。",
                        activity.<TextView>findViewById(R.id.voiceprint_fixed_phrase)
                                .getText().toString());
                TextView status = activity.findViewById(R.id.voiceprint_status);
                Button record = activity.findViewById(R.id.voiceprint_record_button);
                if (status.getText().toString().contains("加载")) {
                    assertFalse(record.isEnabled());
                }
            });
            scenario.recreate();
        }
    }
}
