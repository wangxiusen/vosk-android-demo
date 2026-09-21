package com.huimei.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FaceRecognitionNavigationTest {
    @Test
    public void pageExplainsCameraPermissionBeforeItIsGranted() {
        try (ActivityScenario<FaceRecognitionActivity> scenario =
                     ActivityScenario.launch(FaceRecognitionActivity.class)) {
            scenario.onActivity(activity -> {
                TextView permissionMessage = activity.findViewById(R.id.face_permission_message);
                Button permissionButton = activity.findViewById(R.id.face_permission_button);
                if (permissionButton.getVisibility() == View.VISIBLE) {
                    assertTrue(permissionMessage.getText().toString().contains("摄像头权限"));
                    assertEquals("授权摄像头", permissionButton.getText().toString());
                }
            });
            scenario.recreate();
        }
    }
}
