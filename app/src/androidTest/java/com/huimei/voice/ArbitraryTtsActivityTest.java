package com.huimei.voice;

import static org.junit.Assert.assertTrue;

import android.os.Debug;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class ArbitraryTtsActivityTest {
    private static final String PERFORMANCE_TAG = "HuimeiTtsPerf";
    private static final long MODEL_TIMEOUT_MILLIS = 120_000L;
    private static final long PLAYBACK_TIMEOUT_MILLIS = 120_000L;

    @Test
    public void speaksBothChineseVoicesAndEnglishThenStops() {
        try (ActivityScenario<ArbitraryTtsActivity> scenario =
                     ActivityScenario.launch(ArbitraryTtsActivity.class)) {
            waitForPlayEnabled(scenario, MODEL_TIMEOUT_MILLIS);
            logPss("chinese_ready");

            speakAndWait(
                    scenario,
                    "潓美医疗提醒您。设备正在运行，请注意安全；如有异常，请立即检查。",
                    0,
                    "chinese_north");
            speakAndWait(
                    scenario,
                    "氧气机运行正常，请注意用氧安全。",
                    1,
                    "chinese_south");

            scenario.onActivity(activity -> {
                RadioGroup languageGroup = activity.findViewById(
                        R.id.tts_language_group);
                languageGroup.check(R.id.tts_language_english);
            });
            waitForPlayEnabled(scenario, MODEL_TIMEOUT_MILLIS);
            logPss("english_ready");
            speakAndWait(
                    scenario,
                    "Huimei Medical is ready. The oxygen concentrator is running, "
                            + "and the system is operating normally.",
                    0,
                    "english");

            scenario.onActivity(activity -> {
                EditText input = activity.findViewById(R.id.tts_text_input);
                input.setText(
                        "This is a long offline playback test. The medical assistant "
                                + "keeps speaking until the stop button is pressed. "
                                + "The device remains offline throughout this test.");
                Button play = activity.findViewById(R.id.tts_play_button);
                play.performClick();
            });
            waitForStatus(scenario, "正在使用", PLAYBACK_TIMEOUT_MILLIS);
            scenario.onActivity(activity -> {
                Button stop = activity.findViewById(R.id.tts_stop_button);
                stop.performClick();
            });
            waitForStatus(scenario, "口播已停止", 10_000L);
        }
    }

    private static void speakAndWait(
            ActivityScenario<ArbitraryTtsActivity> scenario,
            String text,
            int voiceIndex,
            String metricLabel) {
        scenario.onActivity(activity -> {
            Spinner voice = activity.findViewById(R.id.tts_voice_spinner);
            voice.setSelection(voiceIndex);
            EditText input = activity.findViewById(R.id.tts_text_input);
            input.setText(text);
            Button play = activity.findViewById(R.id.tts_play_button);
            assertTrue("play button should be enabled", play.isEnabled());
            play.performClick();
        });
        waitForStatus(scenario, "正在使用", PLAYBACK_TIMEOUT_MILLIS);
        logPss(metricLabel + "_playing");
        waitForStatus(scenario, "口播完成", PLAYBACK_TIMEOUT_MILLIS);
    }

    private static void logPss(String label) {
        Log.i(PERFORMANCE_TAG,
                "test_pss label=" + label
                        + " pss_kb=" + Debug.getPss()
                        + " native_heap_bytes=" + Debug.getNativeHeapAllocatedSize());
    }

    private static void waitForPlayEnabled(
            ActivityScenario<ArbitraryTtsActivity> scenario,
            long timeoutMillis) {
        waitFor(
                scenario,
                activity -> activity.<Button>findViewById(R.id.tts_play_button)
                        .isEnabled(),
                "play button did not become enabled",
                timeoutMillis);
    }

    private static void waitForStatus(
            ActivityScenario<ArbitraryTtsActivity> scenario,
            String expectedText,
            long timeoutMillis) {
        waitFor(
                scenario,
                activity -> activity.<TextView>findViewById(R.id.tts_status)
                        .getText()
                        .toString()
                        .contains(expectedText),
                "status did not contain: " + expectedText,
                timeoutMillis);
    }

    private static void waitFor(
            ActivityScenario<ArbitraryTtsActivity> scenario,
            ActivityCondition condition,
            String failureMessage,
            long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        AtomicReference<String> lastStatus = new AtomicReference<>("");
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean satisfied = new AtomicBoolean();
            scenario.onActivity(activity -> {
                satisfied.set(condition.isSatisfied(activity));
                TextView status = activity.findViewById(R.id.tts_status);
                lastStatus.set(status.getText().toString());
            });
            if (satisfied.get()) {
                return;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test interrupted", error);
            }
        }
        throw new AssertionError(failureMessage + "; last status=" + lastStatus.get());
    }

    private interface ActivityCondition {
        boolean isSatisfied(ArbitraryTtsActivity activity);
    }
}
