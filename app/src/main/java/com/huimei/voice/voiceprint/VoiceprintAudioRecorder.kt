package com.huimei.voice.voiceprint

import kotlin.math.max
import kotlin.math.min

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

internal interface VoiceprintRecorder {
    @kotlin.Throws(Exception::class)
    fun capture(): ShortArray?

    fun cancel()
}

class VoiceprintAudioRecorder : VoiceprintRecorder {
    private val cancelled = AtomicBoolean()

    @kotlin.concurrent.Volatile
    private var activeRecorder: AudioRecord? = null

    override fun capture(): ShortArray {
        cancelled.set(false)
        val minimumBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minimumBytes > 0) { "Cannot determine microphone buffer size: " + minimumBytes }
        val bufferBytes: Int = max(minimumBytes * 2, SAMPLE_RATE / 5 * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes
        )
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone initialization failed")
        }

        val result = ShortArray(SAMPLE_RATE * RECORDING_SECONDS)
        val buffer = ShortArray(bufferBytes / 2)
        activeRecorder = recorder
        try {
            recorder.startRecording()
            check(recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start recording" }
            var offset = 0
            while (offset < result.size) {
                if (cancelled.get()) {
                    throw CancellationException("Voiceprint recording cancelled")
                }
                val requested: Int = min(buffer.size, result.size - offset)
                val count = recorder.read(buffer, 0, requested)
                if (count < 0) {
                    if (cancelled.get()) {
                        throw CancellationException("Voiceprint recording cancelled")
                    }
                    throw IllegalStateException("Microphone read failed: " + count)
                }
                if (count == 0) {
                    continue
                }
                System.arraycopy(buffer, 0, result, offset, count)
                offset += count
            }
            return result
        } finally {
            activeRecorder = null
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    recorder.stop()
                } catch (ignored: IllegalStateException) {
                    // The cancellation path may already have stopped the recorder.
                }
            }
            recorder.release()
        }
    }

    override fun cancel() {
        cancelled.set(true)
        val recorder = activeRecorder
        if (recorder != null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                recorder.stop()
            } catch (ignored: IllegalStateException) {
                // A concurrent capture cleanup may have stopped it first.
            }
        }
    }

    companion object {
        const val SAMPLE_RATE: Int = 16000
        const val RECORDING_SECONDS: Int = 5
    }
}
