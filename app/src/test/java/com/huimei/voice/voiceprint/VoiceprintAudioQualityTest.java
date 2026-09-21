package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class VoiceprintAudioQualityTest {

    @Test
    public void rejectsDigitalSilence() {
        short[] samples = new short[5 * 16000];

        VoiceprintAudioQuality.Result result =
                VoiceprintAudioQuality.analyze(samples, 16000);

        assertFalse(result.isAccepted());
        assertEquals(VoiceprintAudioQuality.Reason.TOO_QUIET, result.getReason());
        assertEquals(Double.NEGATIVE_INFINITY, result.getRmsDbfs(), 0.0);
    }

    @Test
    public void rejectsShortLongAndClippedAudio() {
        assertEquals(VoiceprintAudioQuality.Reason.TOO_SHORT,
                VoiceprintAudioQuality.analyze(new short[16000], 16000).getReason());
        assertEquals(VoiceprintAudioQuality.Reason.TOO_LONG,
                VoiceprintAudioQuality.analyze(new short[8 * 16000], 16000).getReason());

        short[] clipped = new short[5 * 16000];
        Arrays.fill(clipped, Short.MAX_VALUE);
        VoiceprintAudioQuality.Result result = VoiceprintAudioQuality.analyze(clipped, 16000);
        assertEquals(VoiceprintAudioQuality.Reason.CLIPPED, result.getReason());
        assertEquals(1.0, result.getClippingRatio(), 0.0001);
    }

    @Test
    public void acceptsAudibleFiveSecondSignalAndReportsMetrics() {
        short[] samples = sineWave(440.0, 0.15, 5, 16000);

        VoiceprintAudioQuality.Result result =
                VoiceprintAudioQuality.analyze(samples, 16000);

        assertTrue(result.isAccepted());
        assertEquals(VoiceprintAudioQuality.Reason.NONE, result.getReason());
        assertEquals(5.0, result.getDurationSeconds(), 0.0001);
        assertEquals(-19.49, result.getRmsDbfs(), 0.1);
        assertEquals(-16.48, result.getPeakDbfs(), 0.1);
        assertEquals(0.0, result.getClippingRatio(), 0.0001);
    }

    @Test
    public void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintAudioQuality.analyze(null, 16000));
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintAudioQuality.analyze(new short[1], 0));
    }

    private static short[] sineWave(double frequency, double amplitude, int seconds, int sampleRate) {
        short[] samples = new short[seconds * sampleRate];
        for (int i = 0; i < samples.length; i++) {
            double phase = 2.0 * Math.PI * frequency * i / sampleRate;
            samples[i] = (short) Math.round(Math.sin(phase) * amplitude * Short.MAX_VALUE);
        }
        return samples;
    }
}
