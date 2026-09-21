package com.huimei.voice.voiceprint;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

interface VoiceprintRecorder {
    short[] capture() throws Exception;

    void cancel();
}

public final class VoiceprintAudioRecorder implements VoiceprintRecorder {
    public static final int SAMPLE_RATE = 16000;
    public static final int RECORDING_SECONDS = 5;

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile AudioRecord activeRecorder;

    @Override
    public short[] capture() {
        cancelled.set(false);
        int minimumBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimumBytes <= 0) {
            throw new IllegalStateException("Cannot determine microphone buffer size: " + minimumBytes);
        }
        int bufferBytes = Math.max(minimumBytes * 2, SAMPLE_RATE / 5 * 2);
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            throw new IllegalStateException("Microphone initialization failed");
        }

        short[] result = new short[SAMPLE_RATE * RECORDING_SECONDS];
        short[] buffer = new short[bufferBytes / 2];
        activeRecorder = recorder;
        try {
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Microphone did not start recording");
            }
            int offset = 0;
            while (offset < result.length) {
                if (cancelled.get()) {
                    throw new CancellationException("Voiceprint recording cancelled");
                }
                int requested = Math.min(buffer.length, result.length - offset);
                int count = recorder.read(buffer, 0, requested);
                if (count < 0) {
                    if (cancelled.get()) {
                        throw new CancellationException("Voiceprint recording cancelled");
                    }
                    throw new IllegalStateException("Microphone read failed: " + count);
                }
                if (count == 0) {
                    continue;
                }
                System.arraycopy(buffer, 0, result, offset, count);
                offset += count;
            }
            return result;
        } finally {
            activeRecorder = null;
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    recorder.stop();
                } catch (IllegalStateException ignored) {
                    // The cancellation path may already have stopped the recorder.
                }
            }
            recorder.release();
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        AudioRecord recorder = activeRecorder;
        if (recorder != null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
                // A concurrent capture cleanup may have stopped it first.
            }
        }
    }
}
