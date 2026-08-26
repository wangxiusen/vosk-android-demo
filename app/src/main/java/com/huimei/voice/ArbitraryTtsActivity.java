package com.huimei.voice;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.huimei.voice.avatar.LottieAvatarMotionController;
import com.huimei.voice.avatar.MouthShape;
import com.huimei.voice.tts.OfflineTtsPlayer;
import com.huimei.voice.tts.TtsLanguage;
import com.huimei.voice.tts.TtsPause;
import com.huimei.voice.tts.TtsSpeed;
import com.huimei.voice.tts.TtsVoiceCatalog;
import com.huimei.voice.tts.TtsVoiceOption;

import java.util.ArrayList;
import java.util.List;

public final class ArbitraryTtsActivity extends AppCompatActivity
        implements OfflineTtsPlayer.Listener {
    private OfflineTtsPlayer player;
    private LottieAnimationView animationView;
    private LottieAvatarMotionController avatarController;
    private RadioGroup languageGroup;
    private Spinner voiceSpinner;
    private Spinner speedSpinner;
    private Spinner pauseSpinner;
    private EditText textInput;
    private Button playButton;
    private Button stopButton;
    private TextView statusText;
    private List<TtsVoiceOption> currentVoices;
    private TtsLanguage currentLanguage = TtsLanguage.CHINESE;
    private boolean modelReady;
    private boolean modelLoading;
    private boolean playing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arbitrary_tts);
        bindViews();
        configureOptions();

        avatarController = new LottieAvatarMotionController(animationView);
        animationView.setFailureListener(error ->
                onError("动画加载失败：" + safeMessage(error)));
        avatarController.showIdleEyesOnly();
        animationView.playAnimation();

        player = new OfflineTtsPlayer(this, this);
        modelLoading = true;
        player.initialize(currentLanguage);
    }

    private void bindViews() {
        animationView = findViewById(R.id.tts_avatar_animation);
        languageGroup = findViewById(R.id.tts_language_group);
        voiceSpinner = findViewById(R.id.tts_voice_spinner);
        speedSpinner = findViewById(R.id.tts_speed_spinner);
        pauseSpinner = findViewById(R.id.tts_pause_spinner);
        textInput = findViewById(R.id.tts_text_input);
        playButton = findViewById(R.id.tts_play_button);
        stopButton = findViewById(R.id.tts_stop_button);
        statusText = findViewById(R.id.tts_status);
    }

    private void configureOptions() {
        updateLanguage(TtsLanguage.CHINESE);

        List<String> speedLabels = new ArrayList<>();
        for (TtsSpeed speed : TtsSpeed.values()) {
            speedLabels.add(speed.displayName());
        }
        speedSpinner.setAdapter(createAdapter(speedLabels));
        speedSpinner.setSelection(TtsSpeed.NORMAL.ordinal());

        List<String> pauseLabels = new ArrayList<>();
        for (TtsPause pause : TtsPause.values()) {
            pauseLabels.add(pause.displayName());
        }
        pauseSpinner.setAdapter(createAdapter(pauseLabels));
        pauseSpinner.setSelection(TtsPause.NATURAL.ordinal());

        languageGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            TtsLanguage language = checkedId == R.id.tts_language_english
                    ? TtsLanguage.ENGLISH
                    : TtsLanguage.CHINESE;
            if (language == currentLanguage) {
                return;
            }
            if (playing) {
                player.stop();
            }
            currentLanguage = language;
            modelReady = false;
            modelLoading = true;
            playing = false;
            updateLanguage(language);
            statusText.setText(getString(
                    R.string.tts_status_switching,
                    language == TtsLanguage.CHINESE ? "中文" : "英文"));
            updateControlState();
            player.loadLanguage(language);
        });
        playButton.setOnClickListener(view -> startSpeaking());
        stopButton.setOnClickListener(view -> player.stop());
    }

    private void updateLanguage(TtsLanguage language) {
        currentVoices = TtsVoiceCatalog.voicesFor(language);
        List<String> voiceLabels = new ArrayList<>();
        for (TtsVoiceOption voice : currentVoices) {
            voiceLabels.add(voice.displayName());
        }
        voiceSpinner.setAdapter(createAdapter(voiceLabels));
        voiceSpinner.setSelection(0);
        textInput.setText(language.sampleText());
        textInput.setSelection(textInput.length());
    }

    private ArrayAdapter<String> createAdapter(List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void startSpeaking() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.tts_empty_text, Toast.LENGTH_SHORT).show();
            return;
        }
        TtsVoiceOption voice = currentVoices.get(voiceSpinner.getSelectedItemPosition());
        TtsSpeed speed = TtsSpeed.values()[speedSpinner.getSelectedItemPosition()];
        TtsPause pause = TtsPause.values()[pauseSpinner.getSelectedItemPosition()];
        if (!player.speak(text, voice, speed, pause)) {
            onError("模型尚未就绪，暂时无法口播");
        }
    }

    @Override
    public void onReady(int numberOfSpeakers) {
        modelLoading = false;
        modelReady = true;
        statusText.setText(getString(R.string.tts_status_ready, numberOfSpeakers));
        updateControlState();
    }

    @Override
    public void onStarted() {
        playing = true;
        TtsVoiceOption voice = currentVoices.get(voiceSpinner.getSelectedItemPosition());
        TtsSpeed speed = TtsSpeed.values()[speedSpinner.getSelectedItemPosition()];
        TtsPause pause = TtsPause.values()[pauseSpinner.getSelectedItemPosition()];
        statusText.setText(getString(
                R.string.tts_status_playing,
                voice.displayName(),
                speed.displayName(),
                pause.displayName()));
        updateControlState();
    }

    @Override
    public void onMouthShape(MouthShape shape) {
        avatarController.showMouth(shape);
    }

    @Override
    public void onCompleted() {
        playing = false;
        statusText.setText(R.string.tts_status_completed);
        updateControlState();
    }

    @Override
    public void onStopped() {
        playing = false;
        statusText.setText(R.string.tts_status_stopped);
        updateControlState();
    }

    @Override
    public void onError(String message) {
        playing = false;
        if (modelLoading) {
            modelLoading = false;
            modelReady = false;
        }
        statusText.setText(getString(R.string.tts_status_error, message));
        avatarController.showMouth(MouthShape.CLOSED);
        updateControlState();
    }

    private void updateControlState() {
        languageGroup.setEnabled(!playing);
        setRadioChildrenEnabled(languageGroup, !playing);
        voiceSpinner.setEnabled(!playing);
        speedSpinner.setEnabled(!playing);
        pauseSpinner.setEnabled(!playing);
        textInput.setEnabled(!playing);
        playButton.setEnabled(modelReady && !playing);
        stopButton.setEnabled(playing);
    }

    private static void setRadioChildrenEnabled(RadioGroup group, boolean enabled) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            child.setEnabled(enabled);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.close();
        }
        if (animationView != null) {
            animationView.cancelAnimation();
        }
        super.onDestroy();
    }
}
