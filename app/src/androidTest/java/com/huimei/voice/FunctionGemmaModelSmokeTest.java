package com.huimei.voice;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.huimei.voice.functiongemma.FunctionGemmaModelManifest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class FunctionGemmaModelSmokeTest {
    private static final long MODEL_READY_TIMEOUT_MILLIS = 60_000L;
    private static final long INFERENCE_TIMEOUT_MILLIS = 30_000L;

    @Test
    public void routesExactAndCommonNaturalCommandsAndRejectsUnsafeInputs() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File model = new File(
                new File(
                        new File(
                                new File(context.getFilesDir(), "functiongemma"),
                                "models"),
                        FunctionGemmaModelManifest.MODEL.version()),
                FunctionGemmaModelManifest.MODEL.filename());
        Assume.assumeTrue("FunctionGemma model is not installed", model.isFile());

        try (ActivityScenario<FunctionGemmaCommandActivity> scenario =
                     ActivityScenario.launch(FunctionGemmaCommandActivity.class)) {
            waitForText(
                    scenario,
                    R.id.functiongemma_model_status,
                    "模型已就绪",
                    MODEL_READY_TIMEOUT_MILLIS);

            route(scenario, false, "开机");
            route(scenario, true, "two hours gas production");
            route(scenario, true, "Please turn off the machine because I am finished");
            route(scenario, true, "Please turn on the machine for me");
            route(scenario, false, "请帮我打开设备");
            route(scenario, false, "让设备运行半小时");
            route(scenario, false, "请不要开机");
            route(scenario, false, "让设备产气三个小时");
            String log = route(scenario, false, "今天天气怎么样");

            assertContains(log, "中文 [EXACT] 开机 -> POWER_ON");
            assertContains(log, "English [EXACT] two hours gas production -> GAS_2_HOURS");
            assertContains(log, "English [LOCAL_NATURAL] "
                    + "Please turn off the machine because I am finished -> POWER_OFF");
            assertContains(log, "English [LOCAL_NATURAL] "
                    + "Please turn on the machine for me -> POWER_ON");
            assertContains(log, "中文 [LOCAL_NATURAL] 请帮我打开设备 -> POWER_ON");
            assertContains(log, "中文 [LOCAL_NATURAL] 让设备运行半小时 -> GAS_30_MINUTES");
            assertContains(log, "中文 [REJECTED] 请不要开机");
            assertContains(log, "中文 [REJECTED] 让设备产气三个小时");
            assertContains(log, "中文 [REJECTED] 今天天气怎么样");
        }
    }

    private static String route(
            ActivityScenario<FunctionGemmaCommandActivity> scenario,
            boolean english,
            String input) throws Exception {
        scenario.onActivity(activity -> {
            activity.<RadioGroup>findViewById(R.id.functiongemma_language_group).check(
                    english
                            ? R.id.functiongemma_language_english
                            : R.id.functiongemma_language_chinese);
            activity.<EditText>findViewById(R.id.functiongemma_text_input).setText(input);
            activity.findViewById(R.id.functiongemma_parse_button).performClick();
        });

        return waitForText(
                scenario,
                R.id.functiongemma_log,
                input,
                INFERENCE_TIMEOUT_MILLIS);
    }

    private static String waitForText(
            ActivityScenario<FunctionGemmaCommandActivity> scenario,
            int viewId,
            String expected,
            long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String current = "";
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<String> text = new AtomicReference<>("");
            scenario.onActivity(activity -> text.set(
                    activity.<TextView>findViewById(viewId).getText().toString()));
            current = text.get();
            if (current.contains(expected)) {
                return current;
            }
            Thread.sleep(200L);
        }
        throw new AssertionError(
                "Timed out waiting for '" + expected + "'. Last text: " + current);
    }

    private static void assertContains(String text, String expected) {
        assertTrue("Expected '" + expected + "'. Full log:\n" + text, text.contains(expected));
    }
}
