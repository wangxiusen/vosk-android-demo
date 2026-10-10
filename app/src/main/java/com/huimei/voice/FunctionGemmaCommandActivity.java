package com.huimei.voice;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.huimei.voice.functiongemma.FunctionGemmaDeviceSupport;
import com.huimei.voice.functiongemma.FunctionGemmaEngine;
import com.huimei.voice.functiongemma.FunctionGemmaModelDownloader;
import com.huimei.voice.functiongemma.FunctionGemmaModelManifest;
import com.huimei.voice.functiongemma.FunctionGemmaModelStore;
import com.huimei.voice.functiongemma.HybridCommandResult;
import com.huimei.voice.functiongemma.HybridCommandRouter;
import com.huimei.voice.functiongemma.HybridVoiceCommandController;
import com.huimei.voice.functiongemma.HybridVoiceCommandListener;
import com.huimei.voice.functiongemma.HybridVoiceState;
import com.huimei.voice.functiongemma.LatestOperationGate;
import com.huimei.voice.functiongemma.LiteRtFunctionGemmaEngine;
import com.huimei.voice.model.VoiceLanguage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FunctionGemmaCommandActivity extends AppCompatActivity
        implements HybridVoiceCommandListener {
    private static final int RECORD_AUDIO_PERMISSION_REQUEST = 4101;
    private static final int IMPORT_MODEL_REQUEST = 4102;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LatestOperationGate operationGate = new LatestOperationGate();
    private final SimpleDateFormat logTime = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private FunctionGemmaModelStore modelStore;
    private FunctionGemmaEngine engine;
    private HybridCommandRouter router;
    private HybridVoiceCommandController voiceController;
    private VoiceLanguage selectedLanguage = VoiceLanguage.CHINESE;
    private volatile boolean engineReady;
    private boolean busy;
    private boolean listening;
    private boolean hasLogLines;

    private TextView deviceStatus;
    private TextView modelStatus;
    private TextView status;
    private TextView progress;
    private TextView log;
    private EditText textInput;
    private EditText urlInput;
    private Button listenButton;
    private Button parseButton;
    private Button downloadButton;
    private Button importButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_functiongemma_command);
        bindViews();
        bindActions();

        try {
            modelStore = new FunctionGemmaModelStore(
                    new File(getFilesDir(), "functiongemma"),
                    FunctionGemmaModelManifest.MODEL);
        } catch (IOException error) {
            showFatal("无法创建模型目录：" + safeMessage(error));
            return;
        }

        if (!FunctionGemmaDeviceSupport.isSupported()) {
            deviceStatus.setText(R.string.functiongemma_device_unsupported);
            showFatal("当前系统或 CPU ABI 不支持 LiteRT-LM");
            return;
        }

        deviceStatus.setText(R.string.functiongemma_device_supported);
        voiceController = new HybridVoiceCommandController(this, this);
        updateControls();
        checkAndInitializeModel();
    }

    private void bindViews() {
        deviceStatus = findViewById(R.id.functiongemma_device_status);
        modelStatus = findViewById(R.id.functiongemma_model_status);
        status = findViewById(R.id.functiongemma_status);
        progress = findViewById(R.id.functiongemma_progress);
        log = findViewById(R.id.functiongemma_log);
        textInput = findViewById(R.id.functiongemma_text_input);
        urlInput = findViewById(R.id.functiongemma_url_input);
        listenButton = findViewById(R.id.functiongemma_listen_button);
        parseButton = findViewById(R.id.functiongemma_parse_button);
        downloadButton = findViewById(R.id.functiongemma_download_button);
        importButton = findViewById(R.id.functiongemma_import_button);
        deleteButton = findViewById(R.id.functiongemma_delete_button);
    }

    private void bindActions() {
        ((RadioGroup) findViewById(R.id.functiongemma_language_group))
                .setOnCheckedChangeListener((group, checkedId) -> {
                    VoiceLanguage language = checkedId == R.id.functiongemma_language_english
                            ? VoiceLanguage.ENGLISH
                            : VoiceLanguage.CHINESE;
                    if (language == selectedLanguage) {
                        return;
                    }
                    selectedLanguage = language;
                    if (busy && engineReady) {
                        operationGate.next();
                        setBusy(false);
                        status.setText(R.string.functiongemma_status_ready);
                        appendLog("语言已切换，忽略上一次指令的迟到结果");
                    }
                    if (listening && voiceController != null) {
                        voiceController.switchLanguage(selectedLanguage);
                    }
                    updateControls();
                });
        listenButton.setOnClickListener(view -> toggleListening());
        parseButton.setOnClickListener(view -> routeText(
                textInput.getText().toString(), 0L));
        downloadButton.setOnClickListener(view -> downloadModel());
        importButton.setOnClickListener(view -> chooseModelFile());
        deleteButton.setOnClickListener(view -> confirmDeleteModel());
        findViewById(R.id.functiongemma_clear_log_button)
                .setOnClickListener(view -> clearLog());
    }

    private void checkAndInitializeModel() {
        if (!modelStore.installedFile().isFile()) {
            showModelMissing();
            return;
        }
        modelStatus.setText(R.string.functiongemma_model_checking);
        setBusy(true);
        long token = operationGate.next();
        worker.execute(() -> {
            if (!modelStore.isInstalled()) {
                postIfCurrent(token, this::showModelMissing);
                return;
            }
            initializeVerifiedModel(token);
        });
    }

    private void initializeVerifiedModel(long token) {
        postIfCurrent(token, () -> modelStatus.setText(R.string.functiongemma_model_loading));
        closeEngine();
        LiteRtFunctionGemmaEngine candidate = new LiteRtFunctionGemmaEngine(
                new File(getCacheDir(), "functiongemma"));
        try {
            candidate.initialize(modelStore.installedFile());
        } catch (Throwable error) {
            candidate.close();
            postIfCurrent(token, () -> {
                setBusy(false);
                engineReady = false;
                modelStatus.setText("模型初始化失败");
                appendLog("错误：模型初始化失败：" + safeMessage(error));
                updateControls();
            });
            return;
        }
        if (!operationGate.isCurrent(token)) {
            candidate.close();
            return;
        }
        engine = candidate;
        router = new HybridCommandRouter(engine, SystemClock::elapsedRealtime);
        engineReady = true;
        runOnUiThread(() -> {
            if (!operationGate.isCurrent(token)) {
                return;
            }
            setBusy(false);
            modelStatus.setText(R.string.functiongemma_model_ready);
            status.setText(R.string.functiongemma_status_ready);
            progress.setText("");
            appendLog("FunctionGemma 模型初始化完成");
            updateControls();
        });
    }

    private void routeText(String text, long utteranceId) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            appendLog("请输入需要解析的文字");
            completeVoiceInference(utteranceId);
            return;
        }
        if (!engineReady || router == null) {
            appendLog("模型尚未就绪，无法解析");
            completeVoiceInference(utteranceId);
            return;
        }

        VoiceLanguage language = selectedLanguage;
        HybridCommandRouter activeRouter = router;
        long token = operationGate.next();
        status.setText("状态：指令理解中");
        setBusy(true);
        worker.execute(() -> {
            HybridCommandResult result = activeRouter.route(language, trimmed);
            postIfCurrent(token, () -> {
                setBusy(false);
                status.setText(R.string.functiongemma_status_ready);
                appendResult(language, trimmed, result);
                updateControls();
                completeVoiceInference(utteranceId);
            });
        });
    }

    private void appendResult(
            VoiceLanguage language,
            String transcript,
            HybridCommandResult result) {
        if (result.event().isPresent()) {
            appendLog(language.getDisplayName()
                    + " [" + result.route().name() + "] "
                    + transcript + " -> " + result.event().get().name()
                    + "（" + result.elapsedMillis() + " ms）");
        } else {
            String calls = result.inference().isPresent()
                    ? result.inference().get().toolCalls().toString()
                    : "[]";
            String raw = result.inference().isPresent()
                    ? oneLine(result.inference().get().rawOutput())
                    : "";
            appendLog(language.getDisplayName()
                    + " [REJECTED] " + transcript + "（" + result.reason()
                    + "，calls=" + calls + "，raw=" + raw + "）");
        }
    }

    private void downloadModel() {
        URL url;
        try {
            url = new URL(urlInput.getText().toString().trim());
        } catch (MalformedURLException error) {
            appendLog("请输入有效的 HTTPS 下载地址");
            return;
        }
        stopListening();
        engineReady = false;
        setBusy(true);
        status.setText("状态：正在下载模型");
        long token = operationGate.next();
        worker.execute(() -> {
            closeEngine();
            try {
                new FunctionGemmaModelDownloader().download(
                        url,
                        modelStore,
                        (downloaded, declared) -> postDownloadProgress(
                                token, downloaded, declared));
                if (operationGate.isCurrent(token)) {
                    initializeVerifiedModel(token);
                }
            } catch (IOException error) {
                postIfCurrent(token, () -> {
                    setBusy(false);
                    status.setText("状态：下载失败");
                    appendLog("模型下载或校验失败：" + safeMessage(error));
                    updateControls();
                });
            }
        });
    }

    private void postDownloadProgress(long token, long downloaded, long declared) {
        runOnUiThread(() -> {
            if (!operationGate.isCurrent(token)) {
                return;
            }
            if (declared > 0L) {
                long percent = Math.min(100L, downloaded * 100L / declared);
                progress.setText("下载进度：" + percent + "%");
            } else {
                progress.setText(String.format(
                        Locale.ROOT, "已下载：%.1f MiB", downloaded / 1048576.0));
            }
        });
    }

    private void chooseModelFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        startActivityForResult(intent, IMPORT_MODEL_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_MODEL_REQUEST
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }
        importModel(data.getData());
    }

    private void importModel(Uri uri) {
        stopListening();
        engineReady = false;
        setBusy(true);
        status.setText("状态：正在导入并校验模型");
        long token = operationGate.next();
        worker.execute(() -> {
            closeEngine();
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("无法读取所选文件");
                }
                modelStore.install(input);
                if (operationGate.isCurrent(token)) {
                    initializeVerifiedModel(token);
                }
            } catch (IOException error) {
                postIfCurrent(token, () -> {
                    setBusy(false);
                    status.setText("状态：导入失败");
                    appendLog("模型导入或校验失败：" + safeMessage(error));
                    updateControls();
                });
            }
        });
    }

    private void confirmDeleteModel() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.functiongemma_delete_title)
                .setMessage(R.string.functiongemma_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteModel())
                .show();
    }

    private void deleteModel() {
        stopListening();
        engineReady = false;
        setBusy(true);
        long token = operationGate.next();
        worker.execute(() -> {
            closeEngine();
            try {
                modelStore.delete();
                postIfCurrent(token, () -> {
                    appendLog("本地 FunctionGemma 模型已删除");
                    showModelMissing();
                });
            } catch (IOException error) {
                postIfCurrent(token, () -> {
                    setBusy(false);
                    appendLog("删除模型失败：" + safeMessage(error));
                    updateControls();
                });
            }
        });
    }

    private void toggleListening() {
        if (listening) {
            stopListening();
            return;
        }
        if (!engineReady || voiceController == null) {
            appendLog("模型尚未就绪，无法开始语音指令");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_REQUEST);
            return;
        }
        listening = true;
        voiceController.start(selectedLanguage);
        updateControls();
    }

    private void stopListening() {
        if (voiceController != null) {
            voiceController.stop();
        }
        listening = false;
        updateControls();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleListening();
        } else if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST) {
            appendLog("麦克风权限被拒绝");
        }
    }

    private void showModelMissing() {
        engineReady = false;
        setBusy(false);
        modelStatus.setText(R.string.functiongemma_model_missing);
        status.setText(R.string.functiongemma_status_waiting);
        progress.setText("");
        updateControls();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        parseButton.setEnabled(!busy && engineReady);
        listenButton.setEnabled(!busy && engineReady);
        downloadButton.setEnabled(!busy);
        importButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy && modelStore != null
                && modelStore.installedFile().isFile());
    }

    private void updateControls() {
        parseButton.setEnabled(!busy && engineReady);
        listenButton.setEnabled(!busy && engineReady);
        listenButton.setText(listening
                ? R.string.functiongemma_stop_listening
                : R.string.functiongemma_start_listening);
        downloadButton.setEnabled(!busy);
        importButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy && modelStore != null
                && modelStore.installedFile().isFile());
    }

    private void showFatal(String message) {
        engineReady = false;
        status.setText("状态：" + message);
        downloadButton.setEnabled(false);
        importButton.setEnabled(false);
        deleteButton.setEnabled(false);
        listenButton.setEnabled(false);
        parseButton.setEnabled(false);
        appendLog("错误：" + message);
    }

    private void clearLog() {
        hasLogLines = false;
        log.setText(R.string.functiongemma_log_empty);
    }

    private void appendLog(String message) {
        String line = "[" + logTime.format(new Date()) + "] " + message;
        if (!hasLogLines) {
            log.setText(line);
            hasLogLines = true;
        } else {
            log.append("\n" + line);
        }
    }

    private void postIfCurrent(long token, Runnable action) {
        runOnUiThread(() -> {
            if (operationGate.isCurrent(token)) {
                action.run();
            }
        });
    }

    private void completeVoiceInference(long utteranceId) {
        if (utteranceId != 0L && voiceController != null) {
            voiceController.completeInference(utteranceId);
        }
    }

    private void closeEngine() {
        FunctionGemmaEngine active = engine;
        engine = null;
        router = null;
        engineReady = false;
        if (active != null) {
            active.close();
        }
    }

    @Override
    public void onStateChanged(HybridVoiceState state, String message) {
        listening = state != HybridVoiceState.STOPPED;
        status.setText("状态：" + message);
        updateControls();
    }

    @Override
    public void onWake(VoiceLanguage language, long remainingMillis) {
        status.setText(String.format(
                Locale.ROOT,
                "状态：已唤醒，请说指令（%.1f 秒）",
                remainingMillis / 1000.0));
    }

    @Override
    public void onTranscript(VoiceLanguage language, String transcript, long utteranceId) {
        appendLog(language.getDisplayName() + " 语音转写：" + transcript);
        routeText(transcript, utteranceId);
    }

    @Override
    public void onWakeTimeout(VoiceLanguage language) {
        appendLog(language.getDisplayName() + " 唤醒窗口超时");
    }

    @Override
    public void onDiagnostic(String message) {
        appendLog(message);
    }

    @Override
    public void onError(String message) {
        listening = false;
        appendLog("语音识别错误：" + message);
        updateControls();
    }

    @Override
    protected void onDestroy() {
        operationGate.close();
        if (voiceController != null) {
            voiceController.close();
        }
        worker.execute(this::closeEngine);
        worker.shutdown();
        super.onDestroy();
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static String oneLine(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "…";
    }
}
