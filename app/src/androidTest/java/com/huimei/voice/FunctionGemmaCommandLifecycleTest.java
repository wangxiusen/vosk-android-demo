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
    public void missingModelAndRecreationAreHandledWithoutLoadingNativeRuntime() {
        try (ActivityScenario<FunctionGemmaCommandActivity> scenario =
                     ActivityScenario.launch(FunctionGemmaCommandActivity.class)) {
            scenario.onActivity(activity -> assertTrue(
                    activity.<TextView>findViewById(R.id.functiongemma_model_status)
                            .getText().toString().contains("模型未安装")));
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.recreate();
        }
    }
}
