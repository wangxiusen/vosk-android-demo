package com.huimei.voice

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.huimei.voice.face.FaceFrameQuality
import com.huimei.voice.face.FaceMatch
import com.huimei.voice.face.FaceProfile
import com.huimei.voice.face.FaceRecognitionController
import com.huimei.voice.face.FaceRecognitionListener
import com.huimei.voice.face.FaceRepository
import com.huimei.voice.face.OpenCvFaceEngine
import com.huimei.voice.face.RgbaFrameConverter.convert
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class FaceRecognitionActivity : AppCompatActivity(), FaceRecognitionListener {
    private val profiles: MutableList<FaceProfile?> = ArrayList<FaceProfile?>()
    private val logEntries: MutableList<String?> = ArrayList<String?>()
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    private var previewView: PreviewView? = null
    private var statusView: TextView? = null
    private var permissionMessageView: TextView? = null
    private var qualityView: TextView? = null
    private var progressView: TextView? = null
    private var resultView: TextView? = null
    private var metricsView: TextView? = null
    private var logView: TextView? = null
    private var nameInput: EditText? = null
    private var profileSpinner: Spinner? = null
    private var permissionButton: Button? = null
    private var enrollButton: Button? = null
    private var recognizeButton: Button? = null
    private var stopButton: Button? = null
    private var deleteButton: Button? = null
    private var clearButton: Button? = null
    private var profileAdapter: ArrayAdapter<FaceProfile?>? = null
    private var controller: FaceRecognitionController? = null
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraBound = false
    private var cameraUnavailable = false
    private var destroyed = false
    private var modelLoadMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_recognition)
        setTitle(R.string.face_screen_title)
        bindViews()
        configurePage()
        createController()
    }

    override fun onStart() {
        super.onStart()
        if (controller != null && controller!!.state == FaceRecognitionController.State.NEW) {
            controller!!.initialize()
        }
        if (hasCameraPermission()) {
            bindCamera()
        }
        renderControls()
    }

    override fun onStop() {
        if (controller != null) {
            controller!!.stop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            cameraProvider = null
        }
        cameraBound = false
        if (analysisExecutor != null) {
            analysisExecutor!!.shutdownNow()
            analysisExecutor = null
        }
        if (controller != null) {
            controller!!.close()
            controller = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return
        }
        if (hasCameraPermission()) {
            bindCamera()
        } else {
            onError(getString(R.string.face_permission_required))
        }
        renderControls()
    }

    override fun onStateChanged(state: FaceRecognitionController.State?) {
        when (state) {
            FaceRecognitionController.State.NEW -> statusView!!.setText(R.string.face_status_waiting)
            FaceRecognitionController.State.LOADING -> statusView!!.setText(R.string.face_status_loading)
            FaceRecognitionController.State.IDLE -> if (cameraUnavailable) {
                statusView!!.setText(
                    getString(
                        R.string.face_status_error,
                        getString(R.string.face_no_camera)
                    )
                )
            } else {
                statusView!!.setText(R.string.face_status_ready)
            }

            FaceRecognitionController.State.ENROLLING -> statusView!!.setText(R.string.face_status_enrolling)
            FaceRecognitionController.State.RECOGNIZING -> statusView!!.setText(R.string.face_status_recognizing)
            FaceRecognitionController.State.CLOSED -> statusView!!.setText(R.string.face_status_waiting)
            else -> {}
        }
        renderControls()
    }

    override fun onModelReady(loadMillis: Long, dimension: Int) {
        modelLoadMillis = loadMillis
        metricsView!!.setText(
            getString(
                R.string.face_metrics_model, loadMillis, dimension, Debug.getPss()
            )
        )
        appendLog(getString(R.string.face_log_model_ready, loadMillis, dimension))
    }

    override fun onProfilesChanged(updatedProfiles: MutableList<FaceProfile>) {
        profiles.clear()
        profiles.addAll(updatedProfiles)
        profileAdapter!!.notifyDataSetChanged()
        progressView!!.setText(R.string.face_enrollment_progress_empty)
        renderControls()
    }

    override fun onEnrollmentProgress(accepted: Int, required: Int) {
        progressView!!.setText(getString(R.string.face_enrollment_progress, accepted, required))
    }

    override fun onFrameRejected(reason: FaceFrameQuality.Reason?) {
        if (reason == null) return
        qualityView!!.setText(getString(R.string.face_quality_hint, reason.message()))
    }

    override fun onCandidate(match: FaceMatch?, inferenceMillis: Long) {
        if (match == null) return
        if (match.isUnknown) {
            resultView!!.setText(R.string.face_result_unknown)
        } else {
            resultView!!.setText(
                getString(
                    R.string.face_result_candidate,
                    match.profile!!.displayName,
                    match.score
                )
            )
        }
        qualityView!!.setText(
            getString(
                R.string.face_quality_hint,
                FaceFrameQuality.Reason.ACCEPTED.message()
            )
        )
        metricsView!!.setText(
            getString(
                R.string.face_metrics_result,
                modelLoadMillis,
                inferenceMillis,
                Debug.getPss()
            )
        )
    }

    override fun onMatchConfirmed(match: FaceMatch?) {
        if (match == null) return
        resultView!!.setText(
            getString(
                R.string.face_result_confirmed,
                match.profile!!.displayName,
                match.score
            )
        )
    }

    override fun onLog(message: String?) {
        appendLog(message)
    }

    override fun onError(message: String?) {
        statusView!!.setText(getString(R.string.face_status_error, message))
        appendLog(getString(R.string.face_log_error, message))
        renderControls()
    }

    private fun bindViews() {
        previewView = findViewById<PreviewView>(R.id.face_preview)
        statusView = findViewById<TextView>(R.id.face_status)
        permissionMessageView = findViewById<TextView>(R.id.face_permission_message)
        qualityView = findViewById<TextView>(R.id.face_quality)
        progressView = findViewById<TextView>(R.id.face_enrollment_progress)
        resultView = findViewById<TextView>(R.id.face_result)
        metricsView = findViewById<TextView>(R.id.face_metrics)
        logView = findViewById<TextView>(R.id.face_log)
        nameInput = findViewById<EditText>(R.id.face_name_input)
        profileSpinner = findViewById<Spinner>(R.id.face_profile_spinner)
        permissionButton = findViewById<Button>(R.id.face_permission_button)
        enrollButton = findViewById<Button>(R.id.face_enroll_button)
        recognizeButton = findViewById<Button>(R.id.face_recognize_button)
        stopButton = findViewById<Button>(R.id.face_stop_button)
        deleteButton = findViewById<Button>(R.id.face_delete_button)
        clearButton = findViewById<Button>(R.id.face_clear_button)
    }

    private fun configurePage() {
        qualityView!!.setText(getString(R.string.face_quality_hint, "等待画面"))
        profileAdapter = ArrayAdapter<FaceProfile?>(
            this, android.R.layout.simple_spinner_item, profiles
        )
        profileAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner!!.setAdapter(profileAdapter)

        permissionButton!!.setOnClickListener(View.OnClickListener { view: View? ->
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        })
        enrollButton!!.setOnClickListener(View.OnClickListener { view: View? ->
            if (controller != null) {
                controller!!.startEnrollment(nameInput!!.getText().toString())
            }
        })
        recognizeButton!!.setOnClickListener(View.OnClickListener { view: View? ->
            if (controller != null) {
                controller!!.startRecognition()
            }
        })
        stopButton!!.setOnClickListener(View.OnClickListener { view: View? ->
            if (controller != null) {
                controller!!.stop()
            }
        })
        deleteButton!!.setOnClickListener(View.OnClickListener { view: View? -> confirmDeleteSelected() })
        clearButton!!.setOnClickListener(View.OnClickListener { view: View? -> confirmClearAll() })
        renderControls()
    }

    private fun createController() {
        try {
            val repository = FaceRepository(
                File(getFilesDir(), "face-recognition")
            )
            controller = FaceRecognitionController(
                repository, OpenCvFaceEngine(this), this
            )
        } catch (exception: Exception) {
            onError(visibleMessage(exception))
        }
    }

    private fun bindCamera() {
        if (destroyed || cameraBound || !hasCameraPermission()) {
            return
        }
        if (analysisExecutor == null || analysisExecutor!!.isShutdown()) {
            analysisExecutor = Executors.newSingleThreadExecutor()
        }
        val future =
            ProcessCameraProvider.getInstance(this)
        future.addListener(Runnable {
            if (destroyed) {
                return@Runnable
            }
            try {
                val provider = future.get()
                val selector = selectCamera(provider)
                if (selector == null) {
                    showNoCamera()
                    return@Runnable
                }
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView!!.getSurfaceProvider())
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(
                    analysisExecutor!!,
                    ImageAnalysis.Analyzer { image: ImageProxy? -> this.analyzeImage(image!!) })
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
                cameraProvider = provider
                cameraBound = true
                cameraUnavailable = false
                renderControls()
            } catch (exception: Exception) {
                showNoCamera()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @kotlin.Throws(Exception::class)
    private fun selectCamera(provider: ProcessCameraProvider): CameraSelector? {
        if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            return CameraSelector.DEFAULT_FRONT_CAMERA
        }
        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            return CameraSelector.DEFAULT_BACK_CAMERA
        }
        return null
    }

    private fun analyzeImage(image: ImageProxy) {
        try {
            val currentController = controller
            if (currentController == null) {
                return
            }
            val state = currentController.state
            if (state != FaceRecognitionController.State.ENROLLING
                && state != FaceRecognitionController.State.RECOGNIZING
            ) {
                return
            }
            val plane = image.getPlanes()[0]
            val buffer = plane.getBuffer().duplicate()
            buffer.rewind()
            val source = ByteArray(buffer.remaining())
            buffer.get(source)
            val frame = convert(
                source,
                image.getWidth(),
                image.getHeight(),
                plane.getRowStride(),
                plane.getPixelStride(),
                image.getImageInfo().getRotationDegrees()
            )
            currentController.acceptFrame(frame, SystemClock.elapsedRealtime())
        } catch (exception: Exception) {
            runOnUiThread(Runnable { onError(visibleMessage(exception)) })
        } finally {
            image.close()
        }
    }

    private fun showNoCamera() {
        cameraBound = false
        cameraUnavailable = true
        onError(getString(R.string.face_no_camera))
    }

    private fun confirmDeleteSelected() {
        val selected = selectedProfile()
        if (selected == null || controller == null) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.face_delete_confirm_title)
            .setMessage(
                getString(
                    R.string.face_delete_confirm_message, selected.displayName
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    controller!!.deleteProfile(
                        selected.id
                    )
                })
            .show()
    }

    private fun confirmClearAll() {
        if (controller == null || profiles.isEmpty()) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.face_clear_confirm_title)
            .setMessage(R.string.face_clear_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> controller!!.clearAll() })
            .show()
    }

    private fun selectedProfile(): FaceProfile? {
        val selected = profileSpinner!!.getSelectedItem()
        return if (selected is FaceProfile) selected else null
    }

    private fun renderControls() {
        val hasPermission = hasCameraPermission()
        permissionMessageView!!.setVisibility(if (hasPermission) View.GONE else View.VISIBLE)
        permissionButton!!.setVisibility(if (hasPermission) View.GONE else View.VISIBLE)

        val state = if (controller == null)
            FaceRecognitionController.State.NEW
        else
            controller!!.state
        val idle = state == FaceRecognitionController.State.IDLE
        val active = state == FaceRecognitionController.State.ENROLLING
                || state == FaceRecognitionController.State.RECOGNIZING
        val readyForCamera = hasPermission && cameraBound
        val hasProfiles = !profiles.isEmpty()
        enrollButton!!.setEnabled(idle && readyForCamera)
        recognizeButton!!.setEnabled(idle && readyForCamera && hasProfiles)
        stopButton!!.setEnabled(active)
        nameInput!!.setEnabled(idle)
        profileSpinner!!.setEnabled(idle && hasProfiles)
        deleteButton!!.setEnabled(idle && hasProfiles)
        clearButton!!.setEnabled(idle && hasProfiles)
    }

    private fun hasCameraPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun appendLog(message: String?) {
        logEntries.add(0, logTimeFormat.format(Date()) + "  " + message)
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.removeAt(logEntries.size - 1)
        }
        val output = StringBuilder()
        for (entry in logEntries) {
            if (output.length > 0) {
                output.append('\n')
            }
            output.append(entry)
        }
        logView!!.setText(
            if (output.length == 0)
                getString(R.string.face_log_empty)
            else
                output.toString()
        )
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 2501
        private const val MAX_LOG_ENTRIES = 100

        private fun visibleMessage(exception: Exception): String {
            val message = exception.message
            return if (message == null || message.trim { it <= ' ' }.isEmpty())
                exception.javaClass.getSimpleName()
            else
                message
        }
    }
}
