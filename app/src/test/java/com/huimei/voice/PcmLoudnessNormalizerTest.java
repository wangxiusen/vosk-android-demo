package com.huimei.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huimei.voice.tts.PcmLoudnessNormalizer;

import org.junit.Test;

public final class PcmLoudnessNormalizerTest {
    private static final float PEAK_LIMIT = 0.8913f;

    @Test
    public void quietSpeechIsAmplifiedTowardTheTargetRms() {
        float[] samples = {0.02f, -0.02f, 0.04f, -0.04f};

        PcmLoudnessNormalizer.Result result =
                PcmLoudnessNormalizer.analyze(samples, -18.0f);

        assertTrue(result.hasActiveAudio());
        assertFalse(result.isPeakLimited());
        assertEquals(3.981f, result.gain(), 0.01f);
        assertEquals(-18.0f, result.outputActiveRmsDbfs(), 0.05f);
        assertTrue(result.outputPeak() < PEAK_LIMIT);
    }

    @Test
    public void isolatedPeakLimitsGainBeforeClipping() {
        float[] samples = new float[101];
        samples[0] = 0.9f;
        for (int index = 1; index < samples.length; index++) {
            samples[index] = index % 2 == 0 ? 0.01f : -0.01f;
        }

        PcmLoudnessNormalizer.Result result =
                PcmLoudnessNormalizer.analyze(samples, -16.0f);

        assertTrue(result.isPeakLimited());
        assertEquals(PEAK_LIMIT / 0.9f, result.gain(), 0.001f);
        assertEquals(PEAK_LIMIT, result.outputPeak(), 0.0001f);
    }

    @Test
    public void veryQuietActiveAudioUsesAtMostEighteenDbOfGain() {
        float[] samples = {0.006f, -0.006f};

        PcmLoudnessNormalizer.Result result =
                PcmLoudnessNormalizer.analyze(samples, -16.0f);

        assertTrue(result.hasActiveAudio());
        assertEquals(7.943f, result.gain(), 0.01f);
        assertTrue(result.outputActiveRmsDbfs() < -26.0f);
    }

    @Test
    public void silenceRemainsFiniteAndUnchanged() {
        float[] samples = {0f, 0f, 0f};
        float[] output = new float[3];

        PcmLoudnessNormalizer.Result result =
                PcmLoudnessNormalizer.analyze(samples, -18.0f);
        PcmLoudnessNormalizer.copyWithGain(
                samples,
                0,
                samples.length,
                result.gain(),
                result.gain(),
                output);

        assertFalse(result.hasActiveAudio());
        assertEquals(1.0f, result.gain(), 0f);
        assertTrue(Float.isFinite(result.inputActiveRmsDbfs()));
        assertTrue(Float.isFinite(result.outputActiveRmsDbfs()));
        assertArrayEquals(new float[]{0f, 0f, 0f}, output, 0f);
    }

    @Test
    public void scalingCopiesSamplesWithoutMutatingMouthInput() {
        float[] samples = {0.1f, -0.2f, 0.3f};
        float[] original = samples.clone();
        float[] output = new float[3];

        PcmLoudnessNormalizer.copyWithGain(
                samples,
                0,
                samples.length,
                1.0f,
                2.0f,
                output);

        assertArrayEquals(original, samples, 0f);
        assertEquals(0.1f, output[0], 0.0001f);
        assertEquals(-0.3f, output[1], 0.0001f);
        assertEquals(0.6f, output[2], 0.0001f);
    }
}
