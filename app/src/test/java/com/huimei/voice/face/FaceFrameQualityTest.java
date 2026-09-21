package com.huimei.voice.face;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FaceFrameQualityTest {
    @Test
    public void rejectsMissingAndMultipleFaces() {
        assertEquals(FaceFrameQuality.Reason.NO_FACE,
                evaluate(0, 1f, 1f, 100d, 100d).reason());
        assertEquals(FaceFrameQuality.Reason.MULTIPLE_FACES,
                evaluate(2, 1f, 1f, 100d, 100d).reason());
    }

    @Test
    public void rejectsEachQualityMeasureBelowOrOutsideBoundary() {
        assertEquals(FaceFrameQuality.Reason.LOW_CONFIDENCE,
                evaluate(1, 0.899f, 0.5f, 100d, 100d).reason());
        assertEquals(FaceFrameQuality.Reason.FACE_TOO_SMALL,
                evaluate(1, 0.9f, 0.199f, 100d, 100d).reason());
        assertEquals(FaceFrameQuality.Reason.TOO_DARK,
                evaluate(1, 0.9f, 0.2f, 44.9d, 100d).reason());
        assertEquals(FaceFrameQuality.Reason.TOO_BRIGHT,
                evaluate(1, 0.9f, 0.2f, 220.1d, 100d).reason());
        assertEquals(FaceFrameQuality.Reason.BLURRY,
                evaluate(1, 0.9f, 0.2f, 45d, 59.9d).reason());
    }

    @Test
    public void acceptsExactQualityBoundaries() {
        FaceFrameQuality.Decision darkBoundary =
                evaluate(1, 0.9f, 0.2f, 45d, 60d);
        FaceFrameQuality.Decision brightBoundary =
                evaluate(1, 0.9f, 0.2f, 220d, 60d);

        assertTrue(darkBoundary.accepted());
        assertEquals(FaceFrameQuality.Reason.ACCEPTED, darkBoundary.reason());
        assertTrue(brightBoundary.accepted());
        assertFalse(evaluate(1, 0.9f, 0.2f, Double.NaN, 60d).accepted());
    }

    private static FaceFrameQuality.Decision evaluate(
            int faceCount,
            float confidence,
            float faceWidthRatio,
            double brightness,
            double sharpness) {
        return FaceFrameQuality.evaluate(
                faceCount, confidence, faceWidthRatio, brightness, sharpness);
    }
}
