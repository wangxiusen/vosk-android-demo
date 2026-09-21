package com.huimei.voice.face;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class FaceMatcherTest {
    @Test
    public void normalizesAndSelectsBestAcceptedProfile() {
        FaceProfile alice = new FaceProfile("a", "Alice", 5, 1L);
        FaceProfile bob = new FaceProfile("b", "Bob", 5, 2L);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("a", new float[]{1f, 0f});
        vectors.put("b", new float[]{0f, 1f});

        FaceMatch match = FaceMatcher.best(
                new float[]{0.9f, 0.1f},
                Arrays.asList(alice, bob),
                vectors,
                0.363f);

        assertEquals("a", match.getProfile().getId());
        assertTrue(match.isAccepted());
        assertTrue(match.getScore() > 0.9f);
    }

    @Test
    public void averagesThenNormalizesEnrollmentSamples() {
        float[] value = FaceMatcher.averageNormalized(Arrays.asList(
                new float[]{1f, 0f},
                new float[]{0f, 1f}));

        float expected = (float) (1d / Math.sqrt(2d));
        assertArrayEquals(new float[]{expected, expected}, value, 0.0001f);
    }

    @Test
    public void returnsUnknownWhenNoProfilesOrScoreBelowThreshold() {
        assertFalse(FaceMatcher.best(
                new float[]{1f, 0f},
                Collections.emptyList(),
                Collections.emptyMap(),
                0.363f).isAccepted());

        FaceProfile profile = new FaceProfile("a", "Alice", 5, 1L);
        Map<String, float[]> vectors = Collections.singletonMap(
                "a", new float[]{0f, 1f});
        FaceMatch match = FaceMatcher.best(
                new float[]{1f, 0f},
                Collections.singletonList(profile),
                vectors,
                0.363f);
        assertEquals("a", match.getProfile().getId());
        assertFalse(match.isAccepted());
    }

    @Test
    public void rejectsInvalidVectorsDimensionsAndThresholds() {
        assertThrows(IllegalArgumentException.class,
                () -> FaceMatcher.normalize(new float[]{0f, 0f}));
        assertThrows(IllegalArgumentException.class,
                () -> FaceMatcher.normalize(new float[]{Float.NaN, 1f}));
        assertThrows(IllegalArgumentException.class,
                () -> FaceMatcher.cosine(new float[]{1f}, new float[]{1f, 0f}));
        assertThrows(IllegalArgumentException.class,
                () -> FaceMatcher.averageNormalized(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> FaceMatcher.best(new float[]{1f}, Collections.emptyList(),
                        Collections.emptyMap(), Float.NaN));
    }
}
