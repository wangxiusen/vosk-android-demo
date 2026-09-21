package com.huimei.voice.voiceprint;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public final class WavIO {
    private static final int HEADER_BYTES = 44;
    private static final int PCM_FORMAT = 1;
    private static final int MONO_CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private WavIO() {
    }

    public static void writeMono16(File file, short[] samples, int sampleRate) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("WAV file is required");
        }
        if (samples == null) {
            throw new IllegalArgumentException("PCM samples are required");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        long dataSizeLong = (long) samples.length * 2L;
        if (dataSizeLong > Integer.MAX_VALUE - 36L) {
            throw new IllegalArgumentException("PCM data is too large for a WAV file");
        }
        int dataSize = (int) dataSizeLong;
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            writeAscii(output, "RIFF");
            writeIntLe(output, 36 + dataSize);
            writeAscii(output, "WAVE");
            writeAscii(output, "fmt ");
            writeIntLe(output, 16);
            writeShortLe(output, PCM_FORMAT);
            writeShortLe(output, MONO_CHANNELS);
            writeIntLe(output, sampleRate);
            writeIntLe(output, sampleRate * 2);
            writeShortLe(output, 2);
            writeShortLe(output, BITS_PER_SAMPLE);
            writeAscii(output, "data");
            writeIntLe(output, dataSize);
            for (short sample : samples) {
                writeShortLe(output, sample);
            }
        }
    }

    public static Audio readMono16(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("WAV file is required");
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            requireAscii(input, "RIFF");
            int riffSize = readIntLe(input);
            requireAscii(input, "WAVE");
            requireAscii(input, "fmt ");
            int formatSize = readIntLe(input);
            int format = readUnsignedShortLe(input);
            int channels = readUnsignedShortLe(input);
            int sampleRate = readIntLe(input);
            int byteRate = readIntLe(input);
            int blockAlign = readUnsignedShortLe(input);
            int bitsPerSample = readUnsignedShortLe(input);
            requireAscii(input, "data");
            int dataSize = readIntLe(input);

            if (formatSize != 16
                    || format != PCM_FORMAT
                    || channels != MONO_CHANNELS
                    || sampleRate <= 0
                    || byteRate != sampleRate * 2
                    || blockAlign != 2
                    || bitsPerSample != BITS_PER_SAMPLE
                    || dataSize < 0
                    || (dataSize & 1) != 0
                    || riffSize != 36 + dataSize
                    || file.length() != HEADER_BYTES + (long) dataSize) {
                throw new IOException("Unsupported or inconsistent PCM WAV header");
            }

            short[] samples = new short[dataSize / 2];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) readUnsignedShortLe(input);
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected data after WAV payload");
            }
            return new Audio(sampleRate, samples);
        } catch (EOFException exception) {
            throw new IOException("Truncated WAV file", exception);
        }
    }

    private static void requireAscii(InputStream input, String expected) throws IOException {
        byte[] actual = new byte[expected.length()];
        readFully(input, actual);
        byte[] expectedBytes = expected.getBytes("US-ASCII");
        if (!Arrays.equals(expectedBytes, actual)) {
            throw new IOException("Unexpected WAV chunk");
        }
    }

    private static void readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = input.read(buffer, offset, buffer.length - offset);
            if (count < 0) {
                throw new EOFException();
            }
            offset += count;
        }
    }

    private static int readUnsignedShortLe(InputStream input) throws IOException {
        int low = input.read();
        int high = input.read();
        if ((low | high) < 0) {
            throw new EOFException();
        }
        return low | (high << 8);
    }

    private static int readIntLe(InputStream input) throws IOException {
        int first = input.read();
        int second = input.read();
        int third = input.read();
        int fourth = input.read();
        if ((first | second | third | fourth) < 0) {
            throw new EOFException();
        }
        return first | (second << 8) | (third << 16) | (fourth << 24);
    }

    private static void writeAscii(OutputStream output, String value) throws IOException {
        output.write(value.getBytes("US-ASCII"));
    }

    private static void writeShortLe(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void writeIntLe(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    public static final class Audio {
        private final int sampleRate;
        private final short[] samples;

        private Audio(int sampleRate, short[] samples) {
            this.sampleRate = sampleRate;
            this.samples = samples;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public short[] getSamples() {
            return samples.clone();
        }
    }
}
