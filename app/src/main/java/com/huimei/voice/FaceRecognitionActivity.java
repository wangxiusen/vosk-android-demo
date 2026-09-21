package com.huimei.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.huimei.voice.face.FaceFrame;
import com.huimei.voice.face.FaceFrameQuality;
import com.huimei.voice.face.FaceMatch;
import com.huimei.voice.face.FaceProfile;
import com.huimei.voice.face.FaceRecognitionController;
import com.huimei.voice.face.FaceRecognitionListener;
import com.huimei.voice.face.FaceRepository;
import com.huimei.voice.face.OpenCvFaceEngine;
import com.huimei.voice.face.RgbaFrameConverter;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FaceRecognitionActivity extends AppCompatActivity
        implements FaceRecognitionListener {
    private static final int CAMERA_PERMISSION_REQUEST = 2501;
    private static final int MAX_LOG_ENTRIES = 100;

    private final List<FaceProfile> profiles = new ArrayList<>();
    private final List<String> logEntries = new ArrayList<>();
    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    private PreviewView previewView;
    private TextView statusView;
    private TextView permissionMessageView;
    private TextView qualityView;
    private TextView progressView;
    private TextView resultView;
    private TextView metricsView;
    private TextView logView;
    private EditText nameInput;
    private Spinner profileSpinner;
    private Button permissionButton;
    private Button enrollButton;
    private Button recognizeButton;
    private Button stopButton;
    private Button deleteButton;
    private Button clearButton;
    private ArrayAdapter<FaceProfile> profileAdapter;
    private FaceRecognitionController controller;
    private ExecutorService analysisExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean cameraBound;
    private boolean destroyed;
    private long modelLoadMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognition);
        setTitle(R.string.face_screen_title);
        bindViews();
        configurePage();
        createController();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (controller != null && controller.getState() == FaceRecognitionController.State.NEW) {
            controller.initialize();
        }
        if (hasCameraPermission()) {
            bindCamera();
        }
        renderControls();
    }

    @Override
    protected void onStop() {
        if (controller != null) {
            controller.stop();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        cameraBound = false;
        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
            analysisExecutor = null;
        }
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
        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return;
        }
        if (hasCameraPermission()) {
            bindCamera();
        } else {
            onError(getString(R.string.face_permission_required));
        }
        renderControls();
    }

    @Override
    public void onStateChanged(FaceRecognitionController.State state) {
        switch (state) {
            case NEW:
                statusView.setText(R.string.face_status_waiting);
                break;
            case LOADING:
                statusView.setText(R.string.face_status_loading);
                break;
            case IDLE:
                statusView.setText(R.string.face_status_ready);
                break;
            case ENROLLING:
                statusView.setText(R.string.face_status_enrolling);
                break;
            case RECOGNIZING:
                statusView.setText(R.string.face_status_recognizing);
                break;
            case CLOSED:
                statusView.setText(R.string.face_status_waiting);
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
                R.string.face_metrics_model, loadMillis, dimension, Debug.getPss()));
        appendLog(getString(R.string.face_log_model_ready, loadMillis, dimension));
    }

    @Override
    public void onProfilesChanged(List<FaceProfile> updatedProfiles) {
        profiles.clear();
        profiles.addAll(updatedProfiles);
        profileAdapter.notifyDataSetChanged();
        progressView.setText(R.string.face_enrollment_progress_empty);
        renderControls();
    }

    @Override
    public void onEnrollmentProgress(int accepted, int required) {
        progressView.setText(getString(R.string.face_enrollment_progress, accepted, required));
    }

    @Override
    public void onFrameRejected(FaceFrameQuality.Reason reason) {
        qualityView.setText(getString(R.string.face_quality_hint, reason.message()));
    }

    @Override
    public void onCandidate(FaceMatch match, long inferenceMillis) {
        if (match.isUnknown()) {
            resultView.setText(R.string.face_result_unknown);
        } else {
            resultView.setText(getString(
                    R.string.face_result_candidate,
                    match.getProfile().getDisplayName(),
                    match.getScore()));
        }
        qualityView.setText(getString(
                R.string.face_quality_hint,
                FaceFrameQuality.Reason.ACCEPTED.message()));
        metricsView.setText(getString(
                R.string.face_metrics_result,
                modelLoadMillis,
                inferenceMillis,
                Debug.getPss()));
    }

    @Override
    public void onMatchConfirmed(FaceMatch match) {
        resultView.setText(getString(
                R.string.face_result_confirmed,
                match.getProfile().getDisplayName(),
                match.getScore()));
    }

    @Override
    public void onLog(String message) {
        appendLog(message);
    }

    @Override
    public void onError(String message) {
        statusView.setText(getString(R.string.face_status_error, message));
        appendLog(getString(R.string.face_log_error, message));
        renderControls();
    }

    private void bindViews() {
        previewView = findViewById(R.id.face_preview);
        statusView = findViewById(R.id.face_status);
        permissionMessageView = findViewById(R.id.face_permission_message);
        qualityView = findViewById(R.id.face_quality);
        progressView = findViewById(R.id.face_enrollment_progress);
        resultView = findViewById(R.id.face_result);
        metricsView = findViewById(R.id.face_metrics);
        logView = findViewById(R.id.face_log);
        nameInput = findViewById(R.id.face_name_input);
        profileSpinner = findViewById(R.id.face_profile_spinner);
        permissionButton = findViewById(R.id.face_permission_button);
        enrollButton = findViewById(R.id.face_enroll_button);
        recognizeButton = findViewById(R.id.face_recognize_button);
        stopButton = findViewById(R.id.face_stop_button);
        deleteButton = findViewById(R.id.face_delete_button);
        clearButton = findViewById(R.id.face_clear_button);
    }

    private void configurePage() {
        qualityView.setText(getString(R.string.face_quality_hint, "等待画面"));
        profileAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, profiles);
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(profileAdapter);

        permissionButton.setOnClickListener(view -> ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST));
        enrollButton.setOnClickListener(view -> {
            if (controller != null) {
                controller.startEnrollment(nameInput.getText().toString());
            }
        });
        recognizeButton.setOnClickListener(view -> {
            if (controller != null) {
                controller.startRecognition();
            }
        });
        stopButton.setOnClickListener(view -> {
            if (controller != null) {
                controller.stop();
            }
        });
        deleteButton.setOnClickListener(view -> confirmDeleteSelected());
        clearButton.setOnClickListener(view -> confirmClearAll());
        renderControls();
    }

    private void createController() {
        try {
            FaceRepository repository = new FaceRepository(
                    new File(getFilesDir(), "face-recognition"));
            controller = new FaceRecognitionController(
                    repository, new OpenCvFaceEngine(this), this);
        } catch (Exception exception) {
            onError(visibleMessage(exception));
        }
    }

    private void bindCamera() {
        if (destroyed || cameraBound || !hasCameraPermission()) {
            return;
        }
        if (analysisExecutor == null || analysisExecutor.isShutdown()) {
            analysisExecutor = Executors.newSingleThreadExecutor();
        }
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (destroyed) {
                return;
            }
            try {
                ProcessCameraProvider provider = future.get();
                CameraSelector selector = selectCamera(provider);
                if (selector == null) {
                    showNoCamera();
                    return;
                }
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyzeImage);
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, analysis);
                cameraProvider = provider;
                cameraBound = true;
                renderControls();
            } catch (Exception exception) {
                onError(visibleMessage(exception));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private CameraSelector selectCamera(ProcessCameraProvider provider) throws Exception {
        if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            return CameraSelector.DEFAULT_FRONT_CAMERA;
        }
        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
        return null;
    }

    private void analyzeImage(ImageProxy image) {
        try {
            FaceRecognitionController currentController = controller;
            if (currentController == null) {
                return;
            }
            FaceRecognitionController.State state = currentController.getState();
            if (state != FaceRecognitionController.State.ENROLLING
                    && state != FaceRecognitionController.State.RECOGNIZING) {
                return;
            }
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer().duplicate();
            buffer.rewind();
            byte[] source = new byte[buffer.remaining()];
            buffer.get(source);
            FaceFrame frame = RgbaFrameConverter.convert(
                    source,
                    image.getWidth(),
                    image.getHeight(),
                    plane.getRowStride(),
                    plane.getPixelStride(),
                    image.getImageInfo().getRotationDegrees());
            currentController.acceptFrame(frame, SystemClock.elapsedRealtime());
        } catch (Exception exception) {
            runOnUiThread(() -> onError(visibleMessage(exception)));
        } finally {
            image.close();
        }
    }

    private void showNoCamera() {
        cameraBound = false;
        onError(getString(R.string.face_no_camera));
    }

    private void confirmDeleteSelected() {
        FaceProfile selected = selectedProfile();
        if (selected == null || controller == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.face_delete_confirm_title)
                .setMessage(getString(
                        R.string.face_delete_confirm_message, selected.getDisplayName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> controller.deleteProfile(selected.getId()))
                .show();
    }

    private void confirmClearAll() {
        if (controller == null || profiles.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.face_clear_confirm_title)
                .setMessage(R.string.face_clear_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> controller.clearAll())
                .show();
    }

    private FaceProfile selectedProfile() {
        Object selected = profileSpinner.getSelectedItem();
        return selected instanceof FaceProfile ? (FaceProfile) selected : null;
    }

    private void renderControls() {
        boolean hasPermission = hasCameraPermission();
        permissionMessageView.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
        permissionButton.setVisibility(hasPermission ? View.GONE : View.VISIBLE);

        FaceRecognitionController.State state = controller == null
                ? FaceRecognitionController.State.NEW : controller.getState();
        boolean idle = state == FaceRecognitionController.State.IDLE;
        boolean active = state == FaceRecognitionController.State.ENROLLING
                || state == FaceRecognitionController.State.RECOGNIZING;
        boolean readyForCamera = hasPermission && cameraBound;
        boolean hasProfiles = !profiles.isEmpty();
        enrollButton.setEnabled(idle && readyForCamera);
        recognizeButton.setEnabled(idle && readyForCamera && hasProfiles);
        stopButton.setEnabled(active);
        nameInput.setEnabled(idle);
        profileSpinner.setEnabled(idle && hasProfiles);
        deleteButton.setEnabled(idle && hasProfiles);
        clearButton.setEnabled(idle && hasProfiles);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void appendLog(String message) {
        logEntries.add(0, logTimeFormat.format(new Date()) + "  " + message);
        while (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(logEntries.size() - 1);
        }
        StringBuilder output = new StringBuilder();
        for (String entry : logEntries) {
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(entry);
        }
        logView.setText(output.length() == 0
                ? getString(R.string.face_log_empty) : output.toString());
    }

    private static String visibleMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName() : message;
    }
}
