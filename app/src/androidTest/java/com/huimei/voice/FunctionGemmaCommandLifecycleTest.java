package com.huimei.voice;

import static org.junit.Assert.assertTrue;

import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FunctionGemmaCommandLifecycleTest {
    @Test
    public void modelStateAndRecreationAreHandledWithoutCrash() {
        try (ActivityScenario<FunctionGemmaCommandActivity> scenario =
                     ActivityScenario.launch(FunctionGemmaCommandActivity.class)) {
            scenario.onActivity(activity -> {
                String status = activity.<TextView>findViewById(
                        R.id.functiongemma_model_status).getText().toString();
                assertTrue(
                        "Unexpected model status: " + status,
                        status.contains("模型未安装")
                                || status.contains("正在校验")
                                || status.contains("正在初始化")
                                || status.contains("模型已就绪")
                                || status.contains("模型初始化失败"));
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
        }
    }
}
