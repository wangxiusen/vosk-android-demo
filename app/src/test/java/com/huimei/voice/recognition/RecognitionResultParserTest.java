package com.huimei.voice.recognition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class RecognitionResultParserTest {

    @Test
    public void extractsFinalText() {
        assertEquals("开机", RecognitionResultParser.parseText("{\"text\":\"开机\"}").orElse(null));
        assertEquals(
                "hello \"medical\"",
                RecognitionResultParser.parseText("{\"text\":\"hello \\\"medical\\\"\"}").orElse(null));
    }

    @Test
    public void ignoresPartialEmptyAndMalformedResults() {
        assertFalse(RecognitionResultParser.parseText("{\"partial\":\"开\"}").isPresent());
        assertFalse(RecognitionResultParser.parseText("{\"text\":\"   \"}").isPresent());
        assertFalse(RecognitionResultParser.parseText("not-json").isPresent());
        assertFalse(RecognitionResultParser.parseText("").isPresent());
        assertFalse(RecognitionResultParser.parseText(null).isPresent());
    }
}
