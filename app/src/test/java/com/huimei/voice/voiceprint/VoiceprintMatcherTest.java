package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class VoiceprintMatcherTest {

    @Test
    public void cosineRejectsDimensionMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintMatcher.cosine(new float[]{1f}, new float[]{1f, 2f}));
    }

    @Test
    public void normalizeRejectsEmptyZeroAndNonFiniteVectors() {
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintMatcher.normalize(new float[0]));
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintMatcher.normalize(new float[]{0f, 0f}));
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintMatcher.normalize(new float[]{Float.NaN, 1f}));
        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintMatcher.normalize(new float[]{Float.POSITIVE_INFINITY, 1f}));
    }

    @Test
    public void averageNormalizesResultAndRejectsDimensionMismatch() {
        float[] value = VoiceprintMatcher.average(Arrays.asList(
                new float[]{2f, 0f}, new float[]{1f, 0f}));
        assertArrayEquals(new float[]{1f, 0f}, value, 0.0001f);

        assertThrows(IllegalArgumentException.class,
                () -> VoiceprintMatcher.average(Arrays.asList(
                        new float[]{1f}, new float[]{1f, 2f})));
    }

    @Test
    public void cosineReturnsHandCheckedSimilarity() {
        assertEquals(0f,
                VoiceprintMatcher.cosine(new float[]{1f, 0f}, new float[]{0f, 2f}),
                0.0001f);
        assertEquals(1f,
                VoiceprintMatcher.cosine(new float[]{3f, 4f}, new float[]{6f, 8f}),
                0.0001f);
    }

    @Test
    public void searchReturnsUnknownBelowThresholdWithoutLeakingProfile() {
        VoiceprintProfile alice = new VoiceprintProfile("id-a", "Alice", 3, 1L);
        Map<VoiceprintProfile, float[]> enrolled = new LinkedHashMap<>();
        enrolled.put(alice, new float[]{1f, 0f});

        VoiceprintMatch result = VoiceprintMatcher.search(
                enrolled, new float[]{0f, 1f}, 0.60f);

        assertFalse(result.isAccepted());
        assertNull(result.getProfile());
        assertEquals(0f, result.getScore(), 0.0001f);
        assertEquals(0.60f, result.getThreshold(), 0.0001f);
    }

    @Test
    public void searchReturnsBestProfileAtThreshold() {
        VoiceprintProfile alice = new VoiceprintProfile("id-a", "Alice", 3, 1L);
        VoiceprintProfile bob = new VoiceprintProfile("id-b", "Bob", 3, 2L);
        Map<VoiceprintProfile, float[]> enrolled = new LinkedHashMap<>();
        enrolled.put(alice, new float[]{1f, 0f});
        enrolled.put(bob, new float[]{0f, 1f});

        VoiceprintMatch result = VoiceprintMatcher.search(
                enrolled, new float[]{1f, 0f}, 1.0f);

        assertTrue(result.isAccepted());
        assertSame(alice, result.getProfile());
        assertEquals(1f, result.getScore(), 0.0001f);
    }
}
