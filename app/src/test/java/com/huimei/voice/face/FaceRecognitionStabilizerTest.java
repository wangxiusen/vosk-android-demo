package com.huimei.voice.face;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class FaceRecognitionStabilizerTest {
    @Test
    public void confirmsOnlyThirdConsecutiveAcceptedMatch() {
        FaceRecognitionStabilizer value = new FaceRecognitionStabilizer(3);
        FaceMatch alice = accepted("a", 0.8f);

        assertFalse(value.accept(alice).isPresent());
        assertFalse(value.accept(alice).isPresent());
        assertEquals("a", value.accept(alice).get().getProfile().getId());
        assertFalse(value.accept(alice).isPresent());
    }

    @Test
    public void unknownOrCandidateChangeResetsProgress() {
        FaceRecognitionStabilizer value = new FaceRecognitionStabilizer(3);
        value.accept(accepted("a", 0.8f));
        value.accept(FaceMatch.unknown());
        value.accept(accepted("a", 0.8f));
        assertFalse(value.accept(accepted("a", 0.8f)).isPresent());
        value.accept(accepted("b", 0.9f));
        assertFalse(value.accept(accepted("a", 0.8f)).isPresent());
    }

    @Test
    public void rejectsInvalidRequiredFrameCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new FaceRecognitionStabilizer(0));
    }

    private static FaceMatch accepted(String id, float score) {
        return new FaceMatch(new FaceProfile(id, id, 5, 1L), score, true);
    }
}
