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

import com.airbnb.lottie.LottieAnimationView;
import com.huimei.voice.audio.VoicePromptCatalog;
import com.huimei.voice.audio.VoicePromptPlayer;
import com.huimei.voice.avatar.LottieAvatarMotionController;
import com.huimei.voice.avatar.VoiceAvatarStateMachine;
import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.RecognitionAction;
import com.huimei.voice.model.VoiceLanguage;
import com.huimei.voice.recognition.CommandCatalog;
import com.huimei.voice.recognition.VoiceRecognitionController;
import com.huimei.voice.recognition.VoiceRecognitionListener;
import com.huimei.voice.ui.EventLogFormatter;

import java.util.Locale;

public final class VoiceAvatarActivity extends AppCompatActivity
        implements VoiceRecognitionListener {
    private static final int RECORD_AUDIO_PERMISSION_REQUEST = 1002;

    private final EventLogFormatter logFormatter = new EventLogFormatter();
    private final VoiceAvatarStateMachine avatarState = new VoiceAvatarStateMachine();
    private VoiceRecognitionController controller;
    private VoicePromptPlayer promptPlayer;
    private LottieAnimationView animationView;
    private LottieAvatarMotionController avatarMotionController;
    private VoiceLanguage selectedLanguage = VoiceLanguage.CHINESE;
    private TextView metadataText;
    private TextView statusText;
    private TextView countdownText;
    private TextView avatarStateText;
    private TextView logText;
    private ScrollView logScroll;
    private Button listenButton;
    private boolean hasLogLines;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_avatar);

        bindViews();
        controller = new VoiceRecognitionController(this, this);
        promptPlayer = new VoicePromptPlayer(this);
        avatarMotionController = new LottieAvatarMotionController(animationView);
        animationView.setFailureListener(error ->
                onDiagnostic("动画加载失败：" + safeMessage(error)));
        avatarMotionController.showIdleEyesOnly();
        animationView.playAnimation();
        updateLanguageMetadata();
        updateAvatarState();
        onStatus("等待麦克风权限");

        ((RadioGroup) findViewById(R.id.voice_avatar_language_group))
                .setOnCheckedChangeListener((group, checkedId) -> selectLanguage(checkedId));
        listenButton.setOnClickListener(view -> toggleListening());
        findViewById(R.id.voice_avatar_clear_log_button)
                .setOnClickListener(view -> clearLog());

        ensureMicrophonePermissionAndStart();
    }

    private void bindViews() {
        animationView = findViewById(R.id.voice_avatar_animation);
        metadataText = findViewById(R.id.voice_avatar_metadata);
        statusText = findViewById(R.id.voice_avatar_status);
        countdownText = findViewById(R.id.voice_avatar_countdown);
        avatarStateText = findViewById(R.id.voice_avatar_animation_state);
        logText = findViewById(R.id.voice_avatar_log_text);
        logScroll = findViewById(R.id.voice_avatar_log_scroll);
        listenButton = findViewById(R.id.voice_avatar_listen_button);
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
        stopPromptAndResetAvatar();
        selectedLanguage = checkedId == R.id.voice_avatar_language_english
                ? VoiceLanguage.ENGLISH
                : VoiceLanguage.CHINESE;
        updateLanguageMetadata();
        if (controller.isRunning()) {
            controller.switchLanguage(selectedLanguage);
        }
    }

    private void updateLanguageMetadata() {
        CommandCatalog catalog = CommandCatalog.forLanguage(selectedLanguage);
        metadataText.setText(getString(
                R.string.voice_avatar_metadata_value,
                catalog.assetModelPath(),
                catalog.wakePhrase()));
        countdownText.setText(getString(
                R.string.countdown_value,
                getString(R.string.countdown_sleeping)));
    }

    private void toggleListening() {
        if (controller.isRunning()) {
            stopPromptAndResetAvatar();
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
        statusText.setText(getString(R.string.status_value, status));
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
        countdownText.setText(getString(R.string.countdown_value, value));
    }

    @Override
    public void onWakeUp(VoiceLanguage language, RecognitionAction action) {
        appendLog(logFormatter.eventLine(language, "唤醒", action));
        playPromptWithAvatar(language, action.getMatch().getEvent());
    }

    @Override
    public void onCommand(VoiceLanguage language, RecognitionAction action) {
        appendLog(logFormatter.eventLine(language, "命令", action));
        playPromptWithAvatar(language, action.getMatch().getEvent());
    }

    private void playPromptWithAvatar(VoiceLanguage language, CommandEvent event) {
        int resourceId = VoicePromptCatalog.rawResourceFor(language, event);
        if (resourceId == 0 || !controller.pauseForPrompt()) {
            return;
        }

        avatarState.onPromptStarted();
        updateAvatarState();
        onDiagnostic("正在口播" + language.getDisplayName() + "提示音：" + event.name());
        promptPlayer.play(resourceId, new VoicePromptPlayer.Listener() {
            @Override
            public void onCompleted() {
                avatarState.onPromptFinished();
                updateAvatarState();
                controller.resumeAfterPrompt();
            }

            @Override
            public void onError(String message) {
                avatarState.onPromptFailed();
                updateAvatarState();
                onDiagnostic("提示音播放失败：" + message);
                controller.resumeAfterPrompt();
            }
        });
    }

    private void stopPromptAndResetAvatar() {
        promptPlayer.stop();
        avatarState.onStopped();
        updateAvatarState();
    }

    private void updateAvatarState() {
        if (avatarState.isTalking()) {
            avatarMotionController.showTalking();
            avatarStateText.setText(R.string.voice_avatar_state_talking);
        } else {
            avatarMotionController.showIdleEyesOnly();
            avatarStateText.setText(R.string.voice_avatar_state_idle);
        }
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

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    protected void onStart() {
        super.onStart();
        animationView.resumeAnimation();
    }

    @Override
    protected void onStop() {
        animationView.pauseAnimation();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (promptPlayer != null) {
            promptPlayer.close();
        }
        avatarState.onStopped();
        if (avatarMotionController != null) {
            updateAvatarState();
        }
        if (controller != null) {
            controller.close();
        }
        if (animationView != null) {
            animationView.cancelAnimation();
        }
        super.onDestroy();
    }
}
