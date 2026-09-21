package com.huimei.voice;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huimei.voice.voiceprint.VoiceprintController;

import org.junit.Test;

public final class VoiceprintControlsTest {
    @Test
    public void profileActionsRequireIdlePermissionProfilesAndNoPendingEnrollment() {
        assertTrue(BaseVoiceprintActivity.profileActionsEnabled(
                VoiceprintController.State.IDLE, true, true, 0));
        assertFalse(BaseVoiceprintActivity.profileActionsEnabled(
                VoiceprintController.State.IDLE, true, true, 1));
        assertFalse(BaseVoiceprintActivity.profileActionsEnabled(
                VoiceprintController.State.RECORDING, true, true, 0));
        assertFalse(BaseVoiceprintActivity.profileActionsEnabled(
                VoiceprintController.State.IDLE, false, true, 0));
        assertFalse(BaseVoiceprintActivity.profileActionsEnabled(
                VoiceprintController.State.IDLE, true, false, 0));
    }
}
