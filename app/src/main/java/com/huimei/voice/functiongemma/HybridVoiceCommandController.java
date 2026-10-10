package com.huimei.voice.functiongemma;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.CommandCatalog;
import com.huimei.voice.recognition.RecognitionResultParser;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class HybridVoiceCommandController implements AutoCloseable {
    private static final float SAMPLE_RATE = 16_000.0f;
    private static final long COUNTDOWN_INTERVAL_MILLIS = 250L;

    private final Context context;
    private final HybridVoiceCommandListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HybridVoiceSession session = new HybridVoiceSession(SystemClock::elapsedRealtime);

    private VoiceLanguage language = VoiceLanguage.CHINESE;
    private CommandCatalog catalog = CommandCatalog.forLanguage(language);
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private long generation;
    private boolean desiredRunning;
    private boolean closed;

    private final Runnable countdownTask = new Runnable() {
        @Override
        public void run() {
            if (closed || !desiredRunning || session.state() != HybridVoiceState.DICTATING) {
                return;
            }
            HybridVoiceSession.Result result = session.pollTimeout();
            if (result.action() == HybridVoiceSession.Action.TIMED_OUT) {
                listener.onWakeTimeout(language);
                listener.onDiagnostic("唤醒窗口已超时");
                startSleepingRecognizer(generation, language);
                return;
            }
            listener.onWake(language, session.remainingMillis());
            mainHandler.postDelayed(this, COUNTDOWN_INTERVAL_MILLIS);
        }
    };

    public HybridVoiceCommandController(Context context, HybridVoiceCommandListener listener) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.listener = Objects.requireNonNull(listener, "listener");
        LibVosk.setLogLevel(LogLevel.WARNINGS);
    }

    public void start(VoiceLanguage requestedLanguage) {
        runOnMain(() -> startInternal(requestedLanguage));
    }

    public void switchLanguage(VoiceLanguage requestedLanguage) {
        runOnMain(() -> {
            listener.onDiagnostic("切换语言为 " + requestedLanguage.getDisplayName());
            startInternal(requestedLanguage);
        });
    }

    public void completeInference(long utteranceId) {
        runOnMain(() -> {
            if (session.completeInference(utteranceId) && isCurrent(generation, language)) {
                startSleepingRecognizer(generation, language);
            }
        });
    }

    public void stop() {
        runOnMain(this::stopInternal);
    }

    @Override
    public void close() {
        runOnMain(() -> {
            if (closed) {
                return;
            }
            desiredRunning = false;
            closed = true;
            generation++;
            session.close();
            releaseAllResources();
        });
    }

    private void startInternal(VoiceLanguage requestedLanguage) {
        if (closed) {
            return;
        }
        language = Objects.requireNonNull(requestedLanguage, "requestedLanguage");
        catalog = CommandCatalog.forLanguage(language);
        desiredRunning = true;
        long requestGeneration = ++generation;
        session.start(language);
        releaseAllResources();
        listener.onStateChanged(HybridVoiceState.SLEEPING, "模型加载中");
        listener.onDiagnostic("正在加载" + language.getDisplayName() + " Vosk 模型");

        StorageService.unpack(
                context,
                catalog.assetModelPath(),
                "cache-" + catalog.assetModelPath(),
                loadedModel -> mainHandler.post(
                        () -> onModelLoaded(requestGeneration, requestedLanguage, loadedModel)),
                error -> mainHandler.post(
                        () -> onModelLoadFailed(requestGeneration, requestedLanguage, error)));
    }

    private void onModelLoaded(
            long requestGeneration,
            VoiceLanguage requestedLanguage,
            Model loadedModel) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            loadedModel.close();
            return;
        }
        model = loadedModel;
        startSleepingRecognizer(requestGeneration, requestedLanguage);
    }

    private void onModelLoadFailed(
            long requestGeneration,
            VoiceLanguage requestedLanguage,
            IOException error) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            return;
        }
        fail("模型加载失败：" + safeMessage(error));
    }

    private void startSleepingRecognizer(long requestGeneration, VoiceLanguage requestedLanguage) {
        if (!isCurrent(requestGeneration, requestedLanguage) || model == null) {
            return;
        }
        releaseAudioResources();
        try {
            recognizer = new Recognizer(model, SAMPLE_RATE, catalog.grammarJson());
            startSpeechService(requestGeneration, requestedLanguage);
            listener.onStateChanged(HybridVoiceState.SLEEPING, "休眠监听中");
        } catch (IOException error) {
            fail("无法启动离线识别：" + safeMessage(error));
        }
    }

    private void startDictationRecognizer(long requestGeneration, VoiceLanguage requestedLanguage) {
        if (!isCurrent(requestGeneration, requestedLanguage) || model == null) {
            return;
        }
        releaseAudioResources();
        try {
            recognizer = new Recognizer(model, SAMPLE_RATE);
            startSpeechService(requestGeneration, requestedLanguage);
            listener.onStateChanged(HybridVoiceState.DICTATING, "已唤醒，请说指令");
            listener.onWake(language, session.remainingMillis());
            mainHandler.postDelayed(countdownTask, COUNTDOWN_INTERVAL_MILLIS);
        } catch (IOException error) {
            fail("无法启动自由听写：" + safeMessage(error));
        }
    }

    private void startSpeechService(long requestGeneration, VoiceLanguage requestedLanguage)
            throws IOException {
        speechService = new SpeechService(recognizer, SAMPLE_RATE);
        if (!speechService.startListening(createRecognitionListener(
                requestGeneration, requestedLanguage))) {
            throw new IOException("识别服务已经在运行");
        }
    }

    private RecognitionListener createRecognitionListener(
            long requestGeneration,
            VoiceLanguage requestedLanguage) {
        return new RecognitionListener() {
            @Override
            public void onPartialResult(String hypothesis) {
                // Commands are accepted only after Vosk detects an utterance boundary.
            }

            @Override
            public void onResult(String hypothesis) {
                mainHandler.post(() -> processResult(
                        requestGeneration, requestedLanguage, hypothesis));
            }

            @Override
            public void onFinalResult(String hypothesis) {
                mainHandler.post(() -> processResult(
                        requestGeneration, requestedLanguage, hypothesis));
            }

            @Override
            public void onError(Exception error) {
                mainHandler.post(() -> {
                    if (isCurrent(requestGeneration, requestedLanguage)) {
                        fail("录音或识别错误：" + safeMessage(error));
                    }
                });
            }

            @Override
            public void onTimeout() {
                mainHandler.post(() -> {
                    if (isCurrent(requestGeneration, requestedLanguage)) {
                        listener.onDiagnostic("录音服务超时");
                    }
                });
            }
        };
    }

    private void processResult(
            long requestGeneration,
            VoiceLanguage requestedLanguage,
            String json) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            return;
        }
        Optional<String> text = RecognitionResultParser.parseText(json);
        if (!text.isPresent()) {
            return;
        }
        HybridVoiceSession.Result result = session.accept(text.get());
        switch (result.action()) {
            case WOKE:
                mainHandler.removeCallbacks(countdownTask);
                startDictationRecognizer(requestGeneration, requestedLanguage);
                break;
            case COMMAND:
                mainHandler.removeCallbacks(countdownTask);
                releaseAudioResources();
                listener.onStateChanged(HybridVoiceState.INFERENCING, "指令理解中");
                listener.onTranscript(
                        requestedLanguage, result.transcript(), result.utteranceId());
                break;
            case TIMED_OUT:
                mainHandler.removeCallbacks(countdownTask);
                listener.onWakeTimeout(requestedLanguage);
                startSleepingRecognizer(requestGeneration, requestedLanguage);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void stopInternal() {
        if (closed) {
            return;
        }
        desiredRunning = false;
        generation++;
        session.stop();
        releaseAllResources();
        listener.onStateChanged(HybridVoiceState.STOPPED, "已停止");
        listener.onDiagnostic("已停止监听");
    }

    private void fail(String message) {
        desiredRunning = false;
        generation++;
        session.stop();
        releaseAllResources();
        listener.onStateChanged(HybridVoiceState.STOPPED, "识别失败");
        listener.onError(message);
    }

    private boolean isCurrent(long requestGeneration, VoiceLanguage requestedLanguage) {
        return !closed
                && desiredRunning
                && generation == requestGeneration
                && language == requestedLanguage;
    }

    private void releaseAudioResources() {
        mainHandler.removeCallbacks(countdownTask);
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
    }

    private void releaseAllResources() {
        releaseAudioResources();
        if (model != null) {
            model.close();
            model = null;
        }
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
