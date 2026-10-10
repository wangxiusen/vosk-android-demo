package com.huimei.voice;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.huimei.voice.functiongemma.FunctionGemmaModelManifest;
import com.huimei.voice.functiongemma.HybridCommandResult;
import com.huimei.voice.functiongemma.HybridCommandRouter;
import com.huimei.voice.functiongemma.LiteRtFunctionGemmaEngine;
import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.VoiceLanguage;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class FunctionGemmaEvaluationTest {
    private static final String TAG = "FunctionGemmaEval";

    @Test
    public void evaluatesParaphrasesAndRejectsUnsafeInputs() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File model = new File(
                new File(
                        new File(
                                new File(context.getFilesDir(), "functiongemma"),
                                "models"),
                        FunctionGemmaModelManifest.MODEL.version()),
                FunctionGemmaModelManifest.MODEL.filename());
        Assume.assumeTrue("FunctionGemma model is not installed", model.isFile());

        EvaluationCase[] paraphrases = {
                item(VoiceLanguage.CHINESE, "请帮我开机", CommandEvent.POWER_ON),
                item(VoiceLanguage.CHINESE, "帮我把设备打开", CommandEvent.POWER_ON),
                item(VoiceLanguage.CHINESE, "现在启动机器", CommandEvent.POWER_ON),
                item(VoiceLanguage.CHINESE, "请把机器关掉", CommandEvent.POWER_OFF),
                item(VoiceLanguage.CHINESE, "停止设备运行", CommandEvent.POWER_OFF),
                item(VoiceLanguage.CHINESE, "开始产气半小时", CommandEvent.GAS_30_MINUTES),
                item(VoiceLanguage.CHINESE, "请产气三十分钟", CommandEvent.GAS_30_MINUTES),
                item(VoiceLanguage.CHINESE, "让设备产气一小时", CommandEvent.GAS_1_HOUR),
                item(VoiceLanguage.CHINESE, "开始两个小时的产气", CommandEvent.GAS_2_HOURS),
                item(VoiceLanguage.CHINESE, "请连续产气八小时", CommandEvent.GAS_8_HOURS),
                item(VoiceLanguage.ENGLISH, "Please turn on the machine", CommandEvent.POWER_ON),
                item(VoiceLanguage.ENGLISH, "Start the device", CommandEvent.POWER_ON),
                item(VoiceLanguage.ENGLISH, "Power the machine on", CommandEvent.POWER_ON),
                item(VoiceLanguage.ENGLISH, "Please turn off the machine", CommandEvent.POWER_OFF),
                item(VoiceLanguage.ENGLISH, "Stop the device", CommandEvent.POWER_OFF),
                item(VoiceLanguage.ENGLISH, "Start gas production for half an hour",
                        CommandEvent.GAS_30_MINUTES),
                item(VoiceLanguage.ENGLISH, "Produce gas for thirty minutes",
                        CommandEvent.GAS_30_MINUTES),
                item(VoiceLanguage.ENGLISH, "Run gas production for one hour",
                        CommandEvent.GAS_1_HOUR),
                item(VoiceLanguage.ENGLISH, "Start two hours of gas production",
                        CommandEvent.GAS_2_HOURS),
                item(VoiceLanguage.ENGLISH, "Produce gas for eight hours",
                        CommandEvent.GAS_8_HOURS)
        };
        EvaluationCase[] rejected = {
                reject(VoiceLanguage.CHINESE, "今天天气怎么样"),
                reject(VoiceLanguage.CHINESE, "现在几点了"),
                reject(VoiceLanguage.CHINESE, "讲一个笑话"),
                reject(VoiceLanguage.CHINESE, "你好"),
                reject(VoiceLanguage.CHINESE, "把声音调大"),
                reject(VoiceLanguage.ENGLISH, "What is the weather today"),
                reject(VoiceLanguage.ENGLISH, "What time is it"),
                reject(VoiceLanguage.ENGLISH, "Tell me a joke"),
                reject(VoiceLanguage.ENGLISH, "Play some music"),
                reject(VoiceLanguage.ENGLISH, "Connect to the network"),
                reject(VoiceLanguage.CHINESE, "让设备产气三个小时"),
                reject(VoiceLanguage.CHINESE, "产气四十五分钟"),
                reject(VoiceLanguage.CHINESE, "先开机然后关机"),
                reject(VoiceLanguage.ENGLISH, "Turn the machine on and then off"),
                reject(VoiceLanguage.ENGLISH,
                        "Start gas production for one hour and eight hours")
        };

        LiteRtFunctionGemmaEngine engine = new LiteRtFunctionGemmaEngine(
                new File(context.getCacheDir(), "functiongemma-evaluation"));
        try {
            engine.initialize(model);
            HybridCommandRouter router = new HybridCommandRouter(
                    engine, System::currentTimeMillis);
            int correct = 0;
            for (EvaluationCase evaluation : paraphrases) {
                HybridCommandResult result = router.route(evaluation.language, evaluation.input);
                if (result.event().orElse(null) == evaluation.expected) {
                    correct++;
                }
                log(evaluation, result);
            }
            Log.i(TAG, "PARAPHRASE_SCORE " + correct + "/" + paraphrases.length);

            for (EvaluationCase evaluation : rejected) {
                HybridCommandResult result = router.route(evaluation.language, evaluation.input);
                log(evaluation, result);
                assertEquals(
                        "Unsafe input was accepted: " + evaluation.input,
                        HybridCommandResult.Route.REJECTED,
                        result.route());
            }
            Log.i(TAG, "UNSAFE_REJECTION_SCORE " + rejected.length + "/" + rejected.length);
        } finally {
            engine.close();
        }
    }

    private static void log(EvaluationCase evaluation, HybridCommandResult result) {
        Log.i(TAG, evaluation.language.name()
                + " | input=" + evaluation.input
                + " | expected=" + (evaluation.expected == null
                ? "REJECTED" : evaluation.expected.name())
                + " | route=" + result.route().name()
                + " | event=" + (result.event().isPresent()
                ? result.event().get().name() : "NONE")
                + " | latencyMs=" + result.elapsedMillis()
                + " | calls=" + (result.inference().isPresent()
                ? result.inference().get().toolCalls() : "[]"));
    }

    private static EvaluationCase item(
            VoiceLanguage language,
            String input,
            CommandEvent expected) {
        return new EvaluationCase(language, input, expected);
    }

    private static EvaluationCase reject(VoiceLanguage language, String input) {
        return new EvaluationCase(language, input, null);
    }

    private static final class EvaluationCase {
        final VoiceLanguage language;
        final String input;
        final CommandEvent expected;

        EvaluationCase(VoiceLanguage language, String input, CommandEvent expected) {
            this.language = language;
            this.input = input;
            this.expected = expected;
        }
    }
}
