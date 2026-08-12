package com.huimei.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.huimei.voice.model.RecognitionAction;
import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.CommandCatalog;
import com.huimei.voice.recognition.VoiceRecognitionController;
import com.huimei.voice.recognition.VoiceRecognitionListener;
import com.huimei.voice.ui.EventLogFormatter;

import java.util.Locale;

public final class MainActivity extends AppCompatActivity implements VoiceRecognitionListener {
    private static final int RECORD_AUDIO_PERMISSION_REQUEST = 1001;

    private final EventLogFormatter logFormatter = new EventLogFormatter();
    private VoiceRecognitionController controller;
    private VoiceLanguage selectedLanguage = VoiceLanguage.CHINESE;
    private TextView modelText;
    private TextView wakePhraseText;
    private TextView statusText;
    private TextView countdownText;
    private TextView logText;
    private ScrollView logScroll;
    private Button listenButton;
    private boolean hasLogLines;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        controller = new VoiceRecognitionController(this, this);
        updateLanguageMetadata();
        onStatus("等待麦克风权限");

        ((RadioGroup) findViewById(R.id.language_group)).setOnCheckedChangeListener(
                (group, checkedId) -> selectLanguage(checkedId));
        listenButton.setOnClickListener(view -> toggleListening());
        findViewById(R.id.clear_log_button).setOnClickListener(view -> clearLog());

        ensureMicrophonePermissionAndStart();
    }

    private void bindViews() {
        modelText = findViewById(R.id.model_text);
        wakePhraseText = findViewById(R.id.wake_phrase_text);
        statusText = findViewById(R.id.status_text);
        countdownText = findViewById(R.id.countdown_text);
        logText = findViewById(R.id.log_text);
        logScroll = findViewById(R.id.log_scroll);
        listenButton = findViewById(R.id.listen_button);
    }

    private void ensureMicrophonePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            controller.start(selectedLanguage);
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_AUDIO_PERMISSION_REQUEST);
    }

    private void selectLanguage(int checkedId) {
        selectedLanguage = checkedId == R.id.language_english
                ? VoiceLanguage.ENGLISH
                : VoiceLanguage.CHINESE;
        updateLanguageMetadata();
        if (controller.isRunning()) {
            controller.switchLanguage(selectedLanguage);
        }
    }

    private void updateLanguageMetadata() {
        CommandCatalog catalog = CommandCatalog.forLanguage(selectedLanguage);
        modelText.setText(getString(R.string.model_label) + "：" + catalog.assetModelPath());
        wakePhraseText.setText(getString(R.string.wake_phrase_label) + "：" + catalog.wakePhrase());
        countdownText.setText(getString(R.string.countdown_label) + "：" + getString(R.string.countdown_sleeping));
    }

    private void toggleListening() {
        if (controller.isRunning()) {
            controller.stop();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            controller.start(selectedLanguage);
        } else {
            ensureMicrophonePermissionAndStart();
        }
    }

    private void clearLog() {
        hasLogLines = false;
        logText.setText(R.string.event_log_empty);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != RECORD_AUDIO_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            controller.start(selectedLanguage);
        } else {
            onStatus("麦克风权限被拒绝");
            onDiagnostic("无法录音；请在系统设置中授予麦克风权限");
        }
    }

    @Override
    public void onStatus(String status) {
        statusText.setText(getString(R.string.status_label) + "：" + status);
        if (controller != null) {
            listenButton.setText(controller.isRunning()
                    ? R.string.stop_listening
                    : R.string.start_listening);
        }
    }

    @Override
    public void onDiagnostic(String message) {
        appendLog(logFormatter.diagnosticLine(message));
    }

    @Override
    public void onWakeWindowChanged(boolean awake, long remainingMillis) {
        String value = awake
                ? String.format(Locale.ROOT, "%.1f 秒", remainingMillis / 1000.0)
                : getString(R.string.countdown_sleeping);
        countdownText.setText(getString(R.string.countdown_label) + "：" + value);
    }

    @Override
    public void onWakeUp(VoiceLanguage language, RecognitionAction action) {
        appendLog(logFormatter.eventLine(language, "唤醒", action));
    }

    @Override
    public void onCommand(VoiceLanguage language, RecognitionAction action) {
        appendLog(logFormatter.eventLine(language, "命令", action));
    }

    private void appendLog(String line) {
        if (!hasLogLines) {
            logText.setText(line);
            hasLogLines = true;
        } else {
            logText.append("\n" + line);
        }
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.close();
        }
        super.onDestroy();
    }
}
