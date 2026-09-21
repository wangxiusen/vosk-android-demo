package com.huimei.voice.face;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RgbaFrameConverterTest {
    @Test
    public void removesRowPaddingAndPreservesRgbaOrder() {
        byte[] source = new byte[]{
                1, 2, 3, 4, 5, 6, 7, 8, 99, 99, 99, 99,
                9, 10, 11, 12, 13, 14, 15, 16, 99, 99, 99, 99
        };

        FaceFrame frame = RgbaFrameConverter.convert(source, 2, 2, 12, 4, 0);

        assertEquals(2, frame.width());
        assertEquals(2, frame.height());
        assertArrayEquals(new byte[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16
        }, frame.rgba());
    }

    @Test
    public void rotatesClockwiseByNinetyDegrees() {
        byte[] source = pixels(1, 2, 3, 4, 5, 6);

        FaceFrame frame = RgbaFrameConverter.convert(source, 3, 2, 12, 4, 90);

        assertEquals(2, frame.width());
        assertEquals(3, frame.height());
        assertArrayEquals(pixels(4, 1, 5, 2, 6, 3), frame.rgba());
    }

    @Test
    public void rotatesByOneHundredEightyAndTwoHundredSeventyDegrees() {
        byte[] source = pixels(1, 2, 3, 4, 5, 6);

        assertArrayEquals(
                pixels(6, 5, 4, 3, 2, 1),
                RgbaFrameConverter.convert(source, 3, 2, 12, 4, 180).rgba());
        FaceFrame rotated = RgbaFrameConverter.convert(source, 3, 2, 12, 4, 270);
        assertEquals(2, rotated.width());
        assertEquals(3, rotated.height());
        assertArrayEquals(pixels(3, 6, 2, 5, 1, 4), rotated.rgba());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedRotation() {
        RgbaFrameConverter.convert(pixels(1), 1, 1, 4, 4, 45);
    }

    private static byte[] pixels(int... redValues) {
        byte[] bytes = new byte[redValues.length * 4];
        for (int index = 0; index < redValues.length; index++) {
            bytes[index * 4] = (byte) redValues[index];
            bytes[index * 4 + 1] = 10;
            bytes[index * 4 + 2] = 20;
            bytes[index * 4 + 3] = (byte) 255;
        }
        return bytes;
    }
}
