package com.huimei.voice.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.huimei.voice.avatar.AudioEnergyMouthMapper;
import com.huimei.voice.avatar.MouthShape;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.jvm.functions.Function1;

public final class OfflineTtsPlayer implements AutoCloseable {
    private static final String PERFORMANCE_TAG = "HuimeiTtsPerf";
    private static final int FRAME_DURATION_MILLIS = 20;
    private static final int PCM_QUEUE_CAPACITY = 64;

    public interface Listener {
        void onReady(int numberOfSpeakers);

        void onStarted();

        void onMouthShape(MouthShape shape);

        void onCompleted();

        void onStopped();

        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService modelWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService playbackWorker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger playbackGeneration = new AtomicInteger();
    private final AtomicInteger modelGeneration = new AtomicInteger();
    private volatile OfflineTts tts;
    private volatile TtsLanguage loadedLanguage;
    private volatile PlaybackSession activeSession;
    private volatile AudioTrack audioTrack;

    public OfflineTtsPlayer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void initialize() {
        initialize(TtsLanguage.CHINESE);
    }

    public void initialize(TtsLanguage language) {
        loadLanguage(language);
    }

    public void loadLanguage(TtsLanguage language) {
        if (closed.get() || language == null) {
            return;
        }
        int loadGeneration = modelGeneration.incrementAndGet();
        cancelPlayback(false);
        loadedLanguage = null;
        long requestedAtMillis = SystemClock.elapsedRealtime();
        Log.i(PERFORMANCE_TAG,
                "model_load_request generation=" + loadGeneration
                        + " language=" + language.name());
        modelWorker.execute(() -> loadModel(
                loadGeneration,
                language,
                requestedAtMillis));
    }

    public boolean speak(
            String text,
            TtsVoiceOption voice,
            TtsSpeed speed,
            TtsPause pause) {
        OfflineTts currentTts = tts;
        TtsLanguage currentLanguage = loadedLanguage;
        if (closed.get()
                || currentTts == null
                || currentLanguage == null
                || text == null
                || text.trim().isEmpty()) {
            return false;
        }

        cancelPlayback(false);
        int generation = playbackGeneration.incrementAndGet();
        String normalizedText = text.trim();
        PlaybackSession session = new PlaybackSession(
                generation,
                normalizedText,
                voice.speakerId(),
                speed.rate(),
                pause.silenceScale(),
                currentTts.sampleRate(),
                SystemClock.elapsedRealtime());
        activeSession = session;

        Log.i(PERFORMANCE_TAG,
                "request generation=" + generation
                        + " language=" + currentLanguage.name()
                        + " chars=" + normalizedText.length()
                        + " punctuation=" + countPunctuation(normalizedText)
                        + " speaker=" + voice.speakerId()
                        + " speed=" + speed.rate()
                        + " silence_scale=" + pause.silenceScale());
        modelWorker.execute(() -> synthesize(session, currentTts));
        playbackWorker.execute(() -> play(session));
        return true;
    }

    public void stop() {
        cancelPlayback(true);
    }

    private void loadModel(
            int generation,
            TtsLanguage language,
            long requestedAtMillis) {
        if (!isCurrentModelLoad(generation)) {
            return;
        }
        Log.i(PERFORMANCE_TAG,
                "model_load_start generation=" + generation
                        + " language=" + language.name());
        OfflineTts created = null;
        try {
            OfflineTts previous = tts;
            tts = null;
            if (previous != null) {
                previous.release();
            }

            String dataDirectory = "";
            if (language == TtsLanguage.ENGLISH) {
                dataDirectory = AssetDirectoryCopier.copyOnce(
                        context,
                        LightweightTtsConfigFactory.ENGLISH_DATA_DIRECTORY);
            }
            created = new OfflineTts(
                    context.getAssets(),
                    LightweightTtsConfigFactory.create(language, dataDirectory));
            if (!isCurrentModelLoad(generation)) {
                created.release();
                return;
            }
            tts = created;
            loadedLanguage = language;
            Log.i(PERFORMANCE_TAG,
                    "model_load_end generation=" + generation
                            + " language=" + language.name()
                            + " load_ms=" + elapsedSince(requestedAtMillis)
                            + " sample_rate=" + created.sampleRate()
                            + " speakers=" + created.numSpeakers());
            OfflineTts readyModel = created;
            post(() -> {
                if (isCurrentModelLoad(generation)) {
                    listener.onReady(readyModel.numSpeakers());
                }
            });
        } catch (Throwable error) {
            if (created != null && created != tts) {
                created.release();
            }
            if (isCurrentModelLoad(generation)) {
                postError("TTS 模型初始化失败：" + safeMessage(error));
            }
        }
    }

    private void synthesize(PlaybackSession session, OfflineTts currentTts) {
        if (!isCurrent(session)) {
            return;
        }
        Log.i(PERFORMANCE_TAG,
                "worker_start generation=" + session.generation
                        + " after_request_ms=" + elapsedSince(session.requestedAtMillis));
        try {
            GenerationConfig config = new GenerationConfig();
            config.setSid(session.speakerId);
            config.setSpeed(session.speed);
            config.setSilenceScale(session.silenceScale);

            Log.i(PERFORMANCE_TAG,
                    "generate_start generation=" + session.generation
                            + " after_request_ms="
                            + elapsedSince(session.requestedAtMillis));
            GeneratedAudio generated = currentTts.generateWithConfigAndCallback(
                    session.text,
                    config,
                    new StreamingCallback(session));
            float[] generatedSamples = generated.getSamples();
            long generationMillis = elapsedSince(session.requestedAtMillis);
            long audioMillis = samplesToMillis(
                    generatedSamples.length,
                    session.sampleRate);
            Log.i(PERFORMANCE_TAG,
                    "generate_end generation=" + session.generation
                            + " after_request_ms=" + generationMillis
                            + " audio_ms=" + audioMillis
                            + " rtf=" + formatRtf(generationMillis, audioMillis)
                            + " longest_quiet_ms=" + longestQuietRunMillis(
                                    generatedSamples,
                                    session.samplesPerFrame));
            if (isCurrent(session) && session.enqueuedSamples.get() == 0) {
                session.fail("没有生成可播放的音频");
            }
        } catch (Throwable error) {
            if (isCurrent(session)) {
                session.fail(safeMessage(error));
            }
        } finally {
            session.queue.finish();
        }
    }

    private void play(PlaybackSession session) {
        AudioTrack track = null;
        try {
            int chunkIndex = 0;
            while (isCurrent(session)) {
                long waitStartedAtMillis = SystemClock.elapsedRealtime();
                float[] samples = session.queue.take();
                long waitMillis = elapsedSince(waitStartedAtMillis);
                if (samples == null) {
                    return;
                }
                if (samples == PcmChunkQueue.END_OF_STREAM) {
                    break;
                }
                chunkIndex++;
                Log.i(PERFORMANCE_TAG,
                        "playback_chunk generation=" + session.generation
                                + " index=" + chunkIndex
                                + " queue_wait_ms=" + waitMillis
                                + " audio_ms=" + samplesToMillis(
                                        samples.length,
                                        session.sampleRate));
                if (track == null) {
                    track = createAudioTrack(session.sampleRate);
                    audioTrack = track;
                    int initialLength = Math.min(
                            session.samplesPerFrame,
                            samples.length);
                    writeFrame(session, track, samples, 0, initialLength);
                    if (!isCurrent(session)) {
                        return;
                    }
                    track.play();
                    Log.i(PERFORMANCE_TAG,
                            "playback_started generation=" + session.generation
                                    + " after_request_ms="
                                    + elapsedSince(session.requestedAtMillis));
                    postIfCurrent(session, listener::onStarted);
                    streamSamples(session, track, samples, initialLength);
                } else {
                    streamSamples(session, track, samples, 0);
                }
            }

            if (!isCurrent(session)) {
                return;
            }
            if (session.failureMessage != null) {
                postIfCurrent(session, () -> {
                    listener.onMouthShape(MouthShape.CLOSED);
                    listener.onError("TTS 播放失败：" + session.failureMessage);
                });
                return;
            }
            if (track == null || session.writtenSamples.get() == 0) {
                postIfCurrent(session, () -> listener.onError("TTS 播放失败：没有可播放音频"));
                return;
            }

            waitForPlaybackToDrain(
                    session,
                    track,
                    session.writtenSamples.get(),
                    session.sampleRate);
            if (isCurrent(session)) {
                Log.i(PERFORMANCE_TAG,
                        "playback_end generation=" + session.generation
                                + " after_request_ms="
                                + elapsedSince(session.requestedAtMillis)
                                + " underruns=" + underrunCount(track));
                postIfCurrent(session, () -> {
                    listener.onMouthShape(MouthShape.CLOSED);
                    listener.onCompleted();
                });
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            if (isCurrent(session)) {
                postIfCurrent(session, () -> {
                    listener.onMouthShape(MouthShape.CLOSED);
                    listener.onError("TTS 播放失败：" + safeMessage(error));
                });
            }
        } finally {
            if (track != null) {
                releaseTrack(track);
            }
        }
    }

    private void streamSamples(
            PlaybackSession session,
            AudioTrack track,
            float[] samples,
            int startOffset) {
        for (int offset = startOffset;
             offset < samples.length;
             offset += session.samplesPerFrame) {
            if (!isCurrent(session)) {
                return;
            }
            int length = Math.min(session.samplesPerFrame, samples.length - offset);
            writeFrame(session, track, samples, offset, length);
        }
    }

    private void writeFrame(
            PlaybackSession session,
            AudioTrack track,
            float[] samples,
            int offset,
            int length) {
        MouthShape shape = AudioEnergyMouthMapper.shapeFor(samples, offset, length);
        if (shape != session.lastShape) {
            session.lastShape = shape;
            postIfCurrent(session, () -> listener.onMouthShape(shape));
        }
        int written = track.write(samples, offset, length, AudioTrack.WRITE_BLOCKING);
        if (written < 0) {
            throw new IllegalStateException("AudioTrack 写入失败：" + written);
        }
        if (session.writtenSamples.getAndAdd(written) == 0) {
            Log.i(PERFORMANCE_TAG,
                    "first_pcm_written generation=" + session.generation
                            + " after_request_ms="
                            + elapsedSince(session.requestedAtMillis));
        }
    }

    private final class StreamingCallback implements Function1<float[], Integer> {
        private final PlaybackSession session;
        private final PcmChunkQueue queue;
        private int callbackCount;
        private long previousCallbackEndedAtMillis;

        private StreamingCallback(PlaybackSession session) {
            this.session = session;
            this.queue = session.queue;
        }

        @Override
        public Integer invoke(float[] samples) {
            if (!isCurrent(session)) {
                return 0;
            }
            int callbackIndex = ++callbackCount;
            long callbackStartedAtMillis = SystemClock.elapsedRealtime();
            long gapMillis = previousCallbackEndedAtMillis == 0L
                    ? -1L
                    : callbackStartedAtMillis - previousCallbackEndedAtMillis;
            Log.i(PERFORMANCE_TAG,
                    "callback_start generation=" + session.generation
                            + " index=" + callbackIndex
                            + " after_request_ms="
                            + elapsedSince(session.requestedAtMillis)
                            + " gap_ms=" + gapMillis
                            + " audio_ms=" + samplesToMillis(
                                    samples.length,
                                    session.sampleRate));
            if (!queue.offer(samples)) {
                session.fail("PCM 缓冲区已满，请缩短口播文字");
                return 0;
            }
            if (session.enqueuedSamples.getAndAdd(samples.length) == 0) {
                Log.i(PERFORMANCE_TAG,
                        "first_pcm_enqueued generation=" + session.generation
                                + " after_request_ms="
                                + elapsedSince(session.requestedAtMillis));
            }
            previousCallbackEndedAtMillis = SystemClock.elapsedRealtime();
            Log.i(PERFORMANCE_TAG,
                    "callback_end generation=" + session.generation
                            + " index=" + callbackIndex
                            + " callback_ms="
                            + (previousCallbackEndedAtMillis - callbackStartedAtMillis));
            return 1;
        }
    }

    private void cancelPlayback(boolean notifyStopped) {
        int canceledGeneration = playbackGeneration.incrementAndGet();
        PlaybackSession session = activeSession;
        activeSession = null;
        if (session != null) {
            session.queue.cancel();
        }
        AudioTrack track = audioTrack;
        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (IllegalStateException ignored) {
                // The playback worker owns final release.
            }
        }
        if (notifyStopped) {
            post(() -> {
                if (!closed.get() && playbackGeneration.get() == canceledGeneration) {
                    listener.onMouthShape(MouthShape.CLOSED);
                    listener.onStopped();
                }
            });
        }
    }

    private boolean isCurrent(PlaybackSession session) {
        return !closed.get()
                && activeSession == session
                && playbackGeneration.get() == session.generation;
    }

    private boolean isCurrentModelLoad(int generation) {
        return !closed.get() && modelGeneration.get() == generation;
    }

    private void postIfCurrent(PlaybackSession session, Runnable action) {
        post(() -> {
            if (isCurrent(session)) {
                action.run();
            }
        });
    }

    private void postError(String message) {
        post(() -> {
            if (!closed.get()) {
                listener.onError(message);
            }
        });
    }

    private void post(Runnable action) {
        mainHandler.post(action);
    }

    private void waitForPlaybackToDrain(
            PlaybackSession session,
            AudioTrack track,
            int writtenSamples,
            int sampleRate) {
        long timeout = SystemClock.elapsedRealtime()
                + samplesToMillis(writtenSamples, sampleRate)
                + 2000L;
        while (isCurrent(session)
                && Integer.toUnsignedLong(track.getPlaybackHeadPosition()) < writtenSamples
                && SystemClock.elapsedRealtime() < timeout) {
            SystemClock.sleep(10L);
        }
    }

    private void releaseTrack(AudioTrack track) {
        if (audioTrack == track) {
            audioTrack = null;
        }
        try {
            track.stop();
        } catch (IllegalStateException ignored) {
            // A canceled track may already be stopped.
        }
        track.release();
    }

    private static AudioTrack createAudioTrack(int sampleRate) {
        int minimumBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT);
        if (minimumBuffer <= 0) {
            throw new IllegalStateException("AudioTrack 不支持采样率 " + sampleRate);
        }
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(sampleRate)
                .build();
        AudioTrack track = new AudioTrack(
                attributes,
                format,
                minimumBuffer,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException("AudioTrack 初始化失败");
        }
        return track;
    }

    private static long elapsedSince(long startedAtMillis) {
        return SystemClock.elapsedRealtime() - startedAtMillis;
    }

    private static long samplesToMillis(int sampleCount, int sampleRate) {
        return sampleCount * 1000L / sampleRate;
    }

    private static String formatRtf(long generationMillis, long audioMillis) {
        if (audioMillis <= 0L) {
            return "n/a";
        }
        return String.format(java.util.Locale.US, "%.3f",
                generationMillis / (double) audioMillis);
    }

    private static int countPunctuation(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '，'
                    || character == '。'
                    || character == '！'
                    || character == '？'
                    || character == ','
                    || character == '.'
                    || character == '!'
                    || character == '?') {
                count++;
            }
        }
        return count;
    }

    private static long longestQuietRunMillis(float[] samples, int samplesPerFrame) {
        int longestFrames = 0;
        int currentFrames = 0;
        for (int offset = 0; offset < samples.length; offset += samplesPerFrame) {
            int length = Math.min(samplesPerFrame, samples.length - offset);
            if (AudioEnergyMouthMapper.shapeFor(samples, offset, length)
                    == MouthShape.CLOSED) {
                currentFrames++;
                longestFrames = Math.max(longestFrames, currentFrames);
            } else {
                currentFrames = 0;
            }
        }
        return longestFrames * FRAME_DURATION_MILLIS;
    }

    private static int underrunCount(AudioTrack track) {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                ? track.getUnderrunCount()
                : -1;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        modelGeneration.incrementAndGet();
        playbackGeneration.incrementAndGet();
        PlaybackSession session = activeSession;
        activeSession = null;
        if (session != null) {
            session.queue.cancel();
        }
        AudioTrack track = audioTrack;
        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (IllegalStateException ignored) {
                // The playback worker owns final release.
            }
        }
        modelWorker.execute(() -> {
            OfflineTts current = tts;
            tts = null;
            loadedLanguage = null;
            if (current != null) {
                current.release();
            }
        });
        modelWorker.shutdown();
        playbackWorker.shutdown();
    }

    private static final class PlaybackSession {
        private final int generation;
        private final String text;
        private final int speakerId;
        private final float speed;
        private final float silenceScale;
        private final int sampleRate;
        private final int samplesPerFrame;
        private final long requestedAtMillis;
        private final PcmChunkQueue queue = new PcmChunkQueue(PCM_QUEUE_CAPACITY);
        private final AtomicInteger enqueuedSamples = new AtomicInteger();
        private final AtomicInteger writtenSamples = new AtomicInteger();
        private volatile String failureMessage;
        private volatile MouthShape lastShape;

        private PlaybackSession(
                int generation,
                String text,
                int speakerId,
                float speed,
                float silenceScale,
                int sampleRate,
                long requestedAtMillis) {
            this.generation = generation;
            this.text = text;
            this.speakerId = speakerId;
            this.speed = speed;
            this.silenceScale = silenceScale;
            this.sampleRate = sampleRate;
            this.samplesPerFrame = Math.max(
                    1,
                    sampleRate * FRAME_DURATION_MILLIS / 1000);
            this.requestedAtMillis = requestedAtMillis;
        }

        private void fail(String message) {
            if (failureMessage == null) {
                failureMessage = message;
            }
        }
    }
}
