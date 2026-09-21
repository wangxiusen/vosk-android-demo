package com.huimei.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Debug;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.huimei.voice.voiceprint.SherpaVoiceprintEngine;
import com.huimei.voice.voiceprint.VoskVoiceprintEngine;
import com.huimei.voice.voiceprint.VoiceprintAudioQuality;
import com.huimei.voice.voiceprint.VoiceprintController;
import com.huimei.voice.voiceprint.VoiceprintEngine;
import com.huimei.voice.voiceprint.VoiceprintEngineType;
import com.huimei.voice.voiceprint.VoiceprintListener;
import com.huimei.voice.voiceprint.VoiceprintMatch;
import com.huimei.voice.voiceprint.VoiceprintProfile;
import com.huimei.voice.voiceprint.VoiceprintRepository;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public abstract class BaseVoiceprintActivity extends AppCompatActivity
        implements VoiceprintListener {
    private static final int MICROPHONE_PERMISSION_REQUEST = 2401;
    private static final int MAX_LOG_ENTRIES = 100;

    private final List<VoiceprintProfile> profiles = new ArrayList<>();
    private final List<String> logEntries = new ArrayList<>();
    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private TextView statusView;
    private TextView progressView;
    private TextView resultView;
    private TextView metricsView;
    private TextView logView;
    private EditText nameInput;
    private Spinner profileSpinner;
    private Button recordButton;
    private Button finishButton;
    private Button identifyButton;
    private Button verifyButton;
    private Button deleteButton;
    private Button clearButton;
    private ArrayAdapter<VoiceprintProfile> profileAdapter;
    private VoiceprintController controller;
    private int acceptedEnrollmentSamples;
    private long modelLoadMillis;

    protected abstract VoiceprintEngineType engineType();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voiceprint);
        bindViews();
        configurePage();
        createController();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasMicrophonePermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST);
        }
        if (controller != null && controller.getState() == VoiceprintController.State.NEW) {
            controller.initialize();
        }
        renderControls();
    }

    @Override
    protected void onStop() {
        if (controller != null) {
            controller.cancelActiveOperation();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.close();
            controller = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            if (!hasMicrophonePermission()) {
                onError(getString(R.string.voiceprint_microphone_required));
            }
            renderControls();
        }
    }

    @Override
    public void onStateChanged(VoiceprintController.State state) {
        switch (state) {
            case NEW:
                statusView.setText(R.string.voiceprint_status_waiting);
                break;
            case LOADING_MODEL:
                statusView.setText(R.string.voiceprint_status_loading);
                break;
            case RECORDING:
                statusView.setText(R.string.voiceprint_status_recording);
                break;
            case PROCESSING:
                statusView.setText(R.string.voiceprint_status_processing);
                break;
            case IDLE:
                statusView.setText(R.string.voiceprint_status_ready);
                break;
            case CLOSED:
                statusView.setText(R.string.voiceprint_status_closed);
                break;
            default:
                break;
        }
        renderControls();
    }

    @Override
    public void onModelReady(long loadMillis, int dimension) {
        modelLoadMillis = loadMillis;
        metricsView.setText(getString(
                R.string.voiceprint_metrics_model,
                loadMillis,
                dimension,
                Debug.getPss()));
        appendLog(getString(R.string.voiceprint_log_model_ready, loadMillis, dimension));
    }

    @Override
    public void onProfilesChanged(List<VoiceprintProfile> updatedProfiles) {
        profiles.clear();
        profiles.addAll(updatedProfiles);
        profileAdapter.notifyDataSetChanged();
        acceptedEnrollmentSamples = 0;
        progressView.setText(R.string.voiceprint_enrollment_progress_empty);
        renderControls();
    }

    @Override
    public void onEnrollmentProgress(
            int acceptedSamples,
            int requiredSamples,
            VoiceprintAudioQuality.Result quality) {
        acceptedEnrollmentSamples = acceptedSamples;
        progressView.setText(getString(
                R.string.voiceprint_enrollment_progress,
                acceptedSamples,
                requiredSamples,
                quality.getRmsDbfs(),
                quality.getClippingRatio() * 100.0));
        renderControls();
    }

    @Override
    public void onAudioRejected(VoiceprintAudioQuality.Result quality) {
        resultView.setText(getString(
                R.string.voiceprint_audio_rejected,
                quality.getReason().name(),
                quality.getRmsDbfs(),
                quality.getClippingRatio() * 100.0));
    }

    @Override
    public void onMatch(VoiceprintMatch match, long inferenceMillis) {
        if (match.isAccepted()) {
            resultView.setText(getString(
                    R.string.voiceprint_result_match,
                    match.getProfile().getDisplayName(),
                    match.getScore(),
                    match.getThreshold()));
        } else {
            resultView.setText(getString(
                    R.string.voiceprint_result_unknown,
                    match.getScore(),
                    match.getThreshold()));
        }
        metricsView.setText(getString(
                R.string.voiceprint_metrics_result,
                modelLoadMillis,
                inferenceMillis,
                Debug.getPss()));
    }

    @Override
    public void onError(String message) {
        statusView.setText(getString(R.string.voiceprint_status_error, message));
        appendLog(getString(R.string.voiceprint_log_error, message));
        renderControls();
    }

    @Override
    public void onLog(String message) {
        appendLog(message);
    }

    private void bindViews() {
        statusView = findViewById(R.id.voiceprint_status);
        progressView = findViewById(R.id.voiceprint_enrollment_progress);
        resultView = findViewById(R.id.voiceprint_result);
        metricsView = findViewById(R.id.voiceprint_metrics);
        logView = findViewById(R.id.voiceprint_log);
        nameInput = findViewById(R.id.voiceprint_name_input);
        profileSpinner = findViewById(R.id.voiceprint_profile_spinner);
        recordButton = findViewById(R.id.voiceprint_record_button);
        finishButton = findViewById(R.id.voiceprint_finish_button);
        identifyButton = findViewById(R.id.voiceprint_identify_button);
        verifyButton = findViewById(R.id.voiceprint_verify_button);
        deleteButton = findViewById(R.id.voiceprint_delete_button);
        clearButton = findViewById(R.id.voiceprint_clear_button);
    }

    private void configurePage() {
        setTitle(engineType().label());
        ((TextView) findViewById(R.id.voiceprint_engine_title)).setText(engineType().label());
        profileAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                profiles);
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(profileAdapter);

        recordButton.setOnClickListener(view -> recordEnrollmentSample());
        finishButton.setOnClickListener(view -> controller.finishEnrollment());
        identifyButton.setOnClickListener(view -> controller.identify());
        verifyButton.setOnClickListener(view -> {
            VoiceprintProfile selected = selectedProfile();
            if (selected != null) {
                controller.verify(selected.getId());
            }
        });
        deleteButton.setOnClickListener(view -> confirmDeleteSelected());
        clearButton.setOnClickListener(view -> confirmClearAll());
        renderControls();
    }

    private void createController() {
        try {
            VoiceprintRepository repository = new VoiceprintRepository(
                    new File(getFilesDir(), "voiceprint"));
            VoiceprintEngine engine = engineType() == VoiceprintEngineType.SHERPA
                    ? new SherpaVoiceprintEngine(this)
                    : new VoskVoiceprintEngine(this);
            controller = new VoiceprintController(this, repository, engine, this);
        } catch (Exception exception) {
            statusView.setText(getString(
                    R.string.voiceprint_status_error,
                    safeMessage(exception)));
            appendLog(getString(R.string.voiceprint_log_error, safeMessage(exception)));
        }
    }

    private void recordEnrollmentSample() {
        if (!hasMicrophonePermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST);
            return;
        }
        controller.recordEnrollment(nameInput.getText().toString());
    }

    private void confirmDeleteSelected() {
        VoiceprintProfile selected = selectedProfile();
        if (selected == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.voiceprint_delete_confirm_title)
                .setMessage(getString(
                        R.string.voiceprint_delete_confirm_message,
                        selected.getDisplayName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.voiceprint_delete_profile,
                        (dialog, which) -> controller.deleteProfile(selected.getId()))
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.voiceprint_clear_confirm_title)
                .setMessage(R.string.voiceprint_clear_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.voiceprint_clear_all,
                        (dialog, which) -> controller.clearAll())
                .show();
    }

    private VoiceprintProfile selectedProfile() {
        Object selected = profileSpinner.getSelectedItem();
        return selected instanceof VoiceprintProfile ? (VoiceprintProfile) selected : null;
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void renderControls() {
        VoiceprintController.State currentState = controller == null
                ? VoiceprintController.State.NEW
                : controller.getState();
        boolean ready = currentState == VoiceprintController.State.IDLE
                && hasMicrophonePermission();
        boolean hasProfiles = !profiles.isEmpty();
        recordButton.setEnabled(ready && acceptedEnrollmentSamples < 3);
        finishButton.setEnabled(ready && acceptedEnrollmentSamples == 3);
        nameInput.setEnabled(ready && acceptedEnrollmentSamples == 0);
        profileSpinner.setEnabled(ready && hasProfiles);
        identifyButton.setEnabled(ready && hasProfiles);
        verifyButton.setEnabled(ready && hasProfiles);
        deleteButton.setEnabled(ready && hasProfiles);
        clearButton.setEnabled(ready && hasProfiles);
    }

    private void appendLog(String message) {
        logEntries.add(logTimeFormat.format(new Date()) + "  " + message);
        while (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(0);
        }
        StringBuilder value = new StringBuilder();
        for (String entry : logEntries) {
            if (value.length() > 0) {
                value.append('\n');
            }
            value.append(entry);
        }
        logView.setText(value);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
