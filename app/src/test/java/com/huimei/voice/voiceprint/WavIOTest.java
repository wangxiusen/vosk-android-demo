package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class WavIOTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void mono16RoundTripsExactly() throws Exception {
        File file = temporaryFolder.newFile("round-trip.wav");
        short[] expected = new short[]{Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};

        WavIO.writeMono16(file, expected, 16000);
        WavIO.Audio actual = WavIO.readMono16(file);

        assertEquals(16000, actual.getSampleRate());
        assertArrayEquals(expected, actual.getSamples());
        assertEquals(54L, file.length());
    }

    @Test
    public void rejectsStereoAndTruncatedWav() throws Exception {
        File stereo = temporaryFolder.newFile("stereo.wav");
        WavIO.writeMono16(stereo, new short[]{1, 2}, 16000);
        try (RandomAccessFile randomAccess = new RandomAccessFile(stereo, "rw")) {
            randomAccess.seek(22);
            randomAccess.write(2);
            randomAccess.write(0);
        }

        File truncated = temporaryFolder.newFile("truncated.wav");
        try (FileOutputStream output = new FileOutputStream(truncated)) {
            output.write(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'});
        }

        assertThrows(IOException.class, () -> WavIO.readMono16(stereo));
        assertThrows(IOException.class, () -> WavIO.readMono16(truncated));
    }

    @Test
    public void rejectsInvalidArguments() throws Exception {
        File file = temporaryFolder.newFile("invalid.wav");
        assertThrows(IllegalArgumentException.class,
                () -> WavIO.writeMono16(file, null, 16000));
        assertThrows(IllegalArgumentException.class,
                () -> WavIO.writeMono16(file, new short[]{1}, 0));
    }
}
