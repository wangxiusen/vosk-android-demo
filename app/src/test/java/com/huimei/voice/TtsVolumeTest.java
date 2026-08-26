package com.huimei.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.huimei.voice.tts.TtsVolume;

import org.junit.Test;

public final class TtsVolumeTest {
    @Test
    public void exposesStandardEnhancedAndStrongTargets() {
        TtsVolume[] volumes = TtsVolume.values();
        String[] names = new String[volumes.length];
        float[] targets = new float[volumes.length];
        for (int index = 0; index < volumes.length; index++) {
            names[index] = volumes[index].name();
            targets[index] = volumes[index].targetRmsDbfs();
            assertFalse(volumes[index].displayName().trim().isEmpty());
        }

        assertArrayEquals(new String[]{"STANDARD", "ENHANCED", "STRONG"}, names);
        assertEquals(-20.0f, targets[0], 0.001f);
        assertEquals(-18.0f, targets[1], 0.001f);
        assertEquals(-16.0f, targets[2], 0.001f);
    }
}
