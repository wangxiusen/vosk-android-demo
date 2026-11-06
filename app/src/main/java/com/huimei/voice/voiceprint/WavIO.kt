package com.huimei.voice.voiceprint

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object WavIO {
    private const val HEADER_BYTES = 44
    private const val PCM_FORMAT = 1
    private const val MONO_CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    @kotlin.Throws(IOException::class)
    @JvmStatic
    fun writeMono16(file: File?, samples: ShortArray?, sampleRate: Int) {
        val file = requireNotNull(file) { "WAV file is required" }
        val samples = requireNotNull(samples) { "PCM samples are required" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        val dataSizeLong = samples.size.toLong() * 2L
        require(dataSizeLong <= Int.Companion.MAX_VALUE - 36L) { "PCM data is too large for a WAV file" }
        val dataSize = dataSizeLong.toInt()
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            writeAscii(output, "RIFF")
            writeIntLe(output, 36 + dataSize)
            writeAscii(output, "WAVE")
            writeAscii(output, "fmt ")
            writeIntLe(output, 16)
            writeShortLe(output, PCM_FORMAT)
            writeShortLe(output, MONO_CHANNELS)
            writeIntLe(output, sampleRate)
            writeIntLe(output, sampleRate * 2)
            writeShortLe(output, 2)
            writeShortLe(output, BITS_PER_SAMPLE)
            writeAscii(output, "data")
            writeIntLe(output, dataSize)
            for (sample in samples) {
                writeShortLe(output, sample.toInt())
            }
        }
    }

    @kotlin.Throws(IOException::class)
    @JvmStatic
    fun readMono16(file: File?): Audio {
        val file = requireNotNull(file) { "WAV file is required" }
        try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                requireAscii(input, "RIFF")
                val riffSize = readIntLe(input)
                requireAscii(input, "WAVE")
                requireAscii(input, "fmt ")
                val formatSize = readIntLe(input)
                val format = readUnsignedShortLe(input)
                val channels = readUnsignedShortLe(input)
                val sampleRate = readIntLe(input)
                val byteRate = readIntLe(input)
                val blockAlign = readUnsignedShortLe(input)
                val bitsPerSample = readUnsignedShortLe(input)
                requireAscii(input, "data")
                val dataSize = readIntLe(input)

                if (formatSize != 16 || format != PCM_FORMAT || channels != MONO_CHANNELS || sampleRate <= 0 || byteRate != sampleRate * 2 || blockAlign != 2 || bitsPerSample != BITS_PER_SAMPLE || dataSize < 0 || (dataSize and 1) != 0 || riffSize != 36 + dataSize || file.length() != HEADER_BYTES + dataSize.toLong()) {
                    throw IOException("Unsupported or inconsistent PCM WAV header")
                }

                val samples = ShortArray(dataSize / 2)
                for (i in samples.indices) {
                    samples[i] = readUnsignedShortLe(input).toShort()
                }
                if (input.read() != -1) {
                    throw IOException("Unexpected data after WAV payload")
                }
                return WavIO.Audio(sampleRate, samples)
            }
        } catch (exception: EOFException) {
            throw IOException("Truncated WAV file", exception)
        }
    }

    @kotlin.Throws(IOException::class)
    private fun requireAscii(input: InputStream, expected: String) {
        val actual = ByteArray(expected.length)
        readFully(input, actual)
        val expectedBytes: ByteArray? = expected.toByteArray(charset("US-ASCII"))
        if (!expectedBytes.contentEquals(actual)) {
            throw IOException("Unexpected WAV chunk")
        }
    }

    @kotlin.Throws(IOException::class)
    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                throw EOFException()
            }
            offset += count
        }
    }

    @kotlin.Throws(IOException::class)
    private fun readUnsignedShortLe(input: InputStream): Int {
        val low = input.read()
        val high = input.read()
        if ((low or high) < 0) {
            throw EOFException()
        }
        return low or (high shl 8)
    }

    @kotlin.Throws(IOException::class)
    private fun readIntLe(input: InputStream): Int {
        val first = input.read()
        val second = input.read()
        val third = input.read()
        val fourth = input.read()
        if ((first or second or third or fourth) < 0) {
            throw EOFException()
        }
        return first or (second shl 8) or (third shl 16) or (fourth shl 24)
    }

    @kotlin.Throws(IOException::class)
    private fun writeAscii(output: OutputStream, value: String) {
        output.write(value.toByteArray(charset("US-ASCII")))
    }

    @kotlin.Throws(IOException::class)
    private fun writeShortLe(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    @kotlin.Throws(IOException::class)
    private fun writeIntLe(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    class Audio internal constructor(val sampleRate: Int, private val samples: ShortArray) {
        fun getSamples(): ShortArray {
            return samples.clone()
        }
    }
}
