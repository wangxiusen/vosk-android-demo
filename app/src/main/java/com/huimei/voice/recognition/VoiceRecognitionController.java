package com.huimei.voice.recognition;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.huimei.voice.model.ListeningState;
import com.huimei.voice.model.RecognitionAction;
import com.huimei.voice.model.VoiceLanguage;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.Optional;

public final class VoiceRecognitionController implements AutoCloseable {
    private static final float SAMPLE_RATE = 16_000.0f;
    private static final long COUNTDOWN_INTERVAL_MILLIS = 250L;

    private final Context context;
    private final VoiceRecognitionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TimeSource timeSource = SystemClock::elapsedRealtime;

    private VoiceLanguage language = VoiceLanguage.CHINESE;
    private CommandCatalog catalog = CommandCatalog.forLanguage(VoiceLanguage.CHINESE);
    private WakeCommandStateMachine stateMachine = new WakeCommandStateMachine(catalog, timeSource);
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private long generation;
    private boolean desiredRunning;
    private boolean closed;

    private final Runnable countdownTask = new Runnable() {
        @Override
        public void run() {
            if (closed || !desiredRunning) {
                return;
            }
            RecognitionAction action = stateMachine.pollTimeout();
            if (action.getType() == RecognitionAction.Type.TIMED_OUT) {
                listener.onDiagnostic("唤醒窗口已超时");
                listener.onStatus("休眠监听中");
                listener.onWakeWindowChanged(false, 0L);
                return;
            }
            if (stateMachine.state() == ListeningState.AWAKE) {
                listener.onWakeWindowChanged(true, stateMachine.remainingMillis());
                mainHandler.postDelayed(this, COUNTDOWN_INTERVAL_MILLIS);
            }
        }
    };

    public VoiceRecognitionController(Context context, VoiceRecognitionListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        LibVosk.setLogLevel(LogLevel.WARNINGS);
    }

    public void start(VoiceLanguage requestedLanguage) {
        if (closed) {
            return;
        }
        desiredRunning = true;
        language = requestedLanguage;
        catalog = CommandCatalog.forLanguage(requestedLanguage);
        stateMachine.reset(catalog);
        listener.onWakeWindowChanged(false, 0L);
        long requestGeneration = ++generation;
        releaseRecognitionResources();
        listener.onStatus("模型加载中");
        listener.onDiagnostic("正在加载" + requestedLanguage.getDisplayName() + "模型");

        StorageService.unpack(
                context,
                catalog.assetModelPath(),
                "cache-" + catalog.assetModelPath(),
                loadedModel -> onModelLoaded(requestGeneration, requestedLanguage, loadedModel),
                error -> onModelLoadFailed(requestGeneration, error));
    }

    public void switchLanguage(VoiceLanguage requestedLanguage) {
        listener.onDiagnostic("切换语言为 " + requestedLanguage.getDisplayName());
        start(requestedLanguage);
    }

    private void onModelLoaded(long requestGeneration, VoiceLanguage requestedLanguage, Model loadedModel) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            loadedModel.close();
            return;
        }
        model = loadedModel;
        try {
            recognizer = new Recognizer(model, SAMPLE_RATE, catalog.grammarJson());
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            if (!speechService.startListening(createRecognitionListener(requestGeneration, requestedLanguage))) {
                throw new IOException("识别服务已经在运行");
            }
            listener.onStatus("休眠监听中");
            listener.onDiagnostic(requestedLanguage.getDisplayName() + "模型已加载，开始监听");
        } catch (IOException error) {
            releaseRecognitionResources();
            desiredRunning = false;
            listener.onStatus("启动失败");
            listener.onDiagnostic("无法启动离线识别：" + safeMessage(error));
        }
    }

    private void onModelLoadFailed(long requestGeneration, IOException error) {
        if (requestGeneration != generation || closed) {
            return;
        }
        desiredRunning = false;
        releaseRecognitionResources();
        listener.onStatus("模型加载失败");
        listener.onDiagnostic("模型加载失败：" + safeMessage(error));
    }

    private RecognitionListener createRecognitionListener(
            long requestGeneration,
            VoiceLanguage requestedLanguage) {
        return new RecognitionListener() {
            @Override
            public void onPartialResult(String hypothesis) {
                // Partial hypotheses are intentionally not consumed. Commands are
                // acted on only after Vosk has detected an utterance boundary.
            }

            @Override
            public void onResult(String hypothesis) {
                processResult(requestGeneration, requestedLanguage, hypothesis);
            }

            @Override
            public void onFinalResult(String hypothesis) {
                processResult(requestGeneration, requestedLanguage, hypothesis);
            }

            @Override
            public void onError(Exception error) {
                if (!isCurrent(requestGeneration, requestedLanguage)) {
                    return;
                }
                desiredRunning = false;
                releaseRecognitionResources();
                listener.onStatus("识别错误");
                listener.onDiagnostic("录音或识别错误：" + safeMessage(error));
            }

            @Override
            public void onTimeout() {
                if (isCurrent(requestGeneration, requestedLanguage)) {
                    listener.onDiagnostic("录音服务超时");
                }
            }
        };
    }

    private void processResult(long requestGeneration, VoiceLanguage requestedLanguage, String json) {
        if (!isCurrent(requestGeneration, requestedLanguage)) {
            return;
        }
        Optional<String> text = RecognitionResultParser.parseText(json);
        if (!text.isPresent()) {
            return;
        }
        RecognitionAction action = stateMachine.accept(text.get());
        switch (action.getType()) {
            case WOKE_UP:
                listener.onStatus("已唤醒，请说命令");
                listener.onWakeUp(language, action);
                listener.onWakeWindowChanged(true, stateMachine.remainingMillis());
                mainHandler.removeCallbacks(countdownTask);
                mainHandler.postDelayed(countdownTask, COUNTDOWN_INTERVAL_MILLIS);
                break;
            case COMMAND:
                mainHandler.removeCallbacks(countdownTask);
                listener.onCommand(language, action);
                listener.onWakeWindowChanged(false, 0L);
                listener.onStatus("休眠监听中");
                break;
            case TIMED_OUT:
                mainHandler.removeCallbacks(countdownTask);
                listener.onDiagnostic("唤醒窗口已超时");
                listener.onWakeWindowChanged(false, 0L);
                listener.onStatus("休眠监听中");
                break;
            case NONE:
            default:
                break;
        }
    }

    public void stop() {
        if (closed) {
            return;
        }
        desiredRunning = false;
        generation++;
        mainHandler.removeCallbacks(countdownTask);
        stateMachine.reset(catalog);
        releaseRecognitionResources();
        listener.onWakeWindowChanged(false, 0L);
        listener.onStatus("已停止");
        listener.onDiagnostic("已停止监听");
    }

    private boolean isCurrent(long requestGeneration, VoiceLanguage requestedLanguage) {
        return !closed
                && desiredRunning
                && generation == requestGeneration
                && language == requestedLanguage;
    }

    private void releaseRecognitionResources() {
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
        if (model != null) {
            model.close();
            model = null;
        }
    }

    public boolean isRunning() {
        return desiredRunning;
    }

    public VoiceLanguage currentLanguage() {
        return language;
    }

    public String currentWakePhrase() {
        return catalog.wakePhrase();
    }

    public String currentModelPath() {
        return catalog.assetModelPath();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        desiredRunning = false;
        closed = true;
        generation++;
        mainHandler.removeCallbacks(countdownTask);
        releaseRecognitionResources();
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
