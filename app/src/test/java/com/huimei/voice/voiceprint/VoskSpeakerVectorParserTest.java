package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class VoskSpeakerVectorParserTest {

    @Test
    public void parsesFiniteVectorWithEnoughFrames() {
        float[] value = VoskSpeakerVectorParser.parse(
                "{\"spk\":[0.1,-0.2],\"spk_frames\":240}", 200);
        assertArrayEquals(new float[]{0.1f, -0.2f}, value, 0.0001f);
    }

    @Test
    public void rejectsMissingOrEmptyVector() {
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse("{\"spk_frames\":240}", 200));
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse(
                        "{\"spk\":[],\"spk_frames\":240}", 200));
    }

    @Test
    public void rejectsInsufficientFramesAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse(
                        "{\"spk\":[1.0],\"spk_frames\":10}", 200));
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse(
                        "{\"spk\":[\"NaN\"],\"spk_frames\":240}", 200));
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse(
                        "{\"spk\":[\"Infinity\"],\"spk_frames\":240}", 200));
    }

    @Test
    public void rejectsMalformedJsonAndInvalidMinimumFrames() {
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse("not-json", 200));
        assertThrows(IllegalArgumentException.class,
                () -> VoskSpeakerVectorParser.parse(
                        "{\"spk\":[1.0],\"spk_frames\":240}", 0));
    }
}
