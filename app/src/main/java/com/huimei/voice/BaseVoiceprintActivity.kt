package com.huimei.voice

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Debug
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.huimei.voice.voiceprint.SherpaVoiceprintEngine
import com.huimei.voice.voiceprint.VoiceprintAudioQuality
import com.huimei.voice.voiceprint.VoiceprintController
import com.huimei.voice.voiceprint.VoiceprintEngineType
import com.huimei.voice.voiceprint.VoiceprintListener
import com.huimei.voice.voiceprint.VoiceprintMatch
import com.huimei.voice.voiceprint.VoiceprintProfile
import com.huimei.voice.voiceprint.VoiceprintRepository
import com.huimei.voice.voiceprint.VoskVoiceprintEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

abstract class BaseVoiceprintActivity : AppCompatActivity(), VoiceprintListener {
    private val profiles: MutableList<VoiceprintProfile?> = ArrayList<VoiceprintProfile?>()
    private val logEntries: MutableList<String?> = ArrayList<String?>()
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    private var statusView: TextView? = null
    private var progressView: TextView? = null
    private var resultView: TextView? = null
    private var metricsView: TextView? = null
    private var logView: TextView? = null
    private var nameInput: EditText? = null
    private var profileSpinner: Spinner? = null
    private var recordButton: Button? = null
    private var finishButton: Button? = null
    private var identifyButton: Button? = null
    private var verifyButton: Button? = null
    private var deleteButton: Button? = null
    private var clearButton: Button? = null
    private var profileAdapter: ArrayAdapter<VoiceprintProfile?>? = null
    private var controller: VoiceprintController? = null
    private var acceptedEnrollmentSamples = 0
    private var modelLoadMillis: Long = 0

    protected abstract fun engineType(): VoiceprintEngineType?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voiceprint)
        bindViews()
        configurePage()
        createController()
    }

    override fun onStart() {
        super.onStart()
        if (!hasMicrophonePermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_REQUEST
            )
        }
        if (controller != null && controller!!.state == VoiceprintController.State.NEW) {
            controller!!.initialize()
        }
        renderControls()
    }

    override fun onStop() {
        if (controller != null) {
            controller!!.cancelActiveOperation()
        }
        super.onStop()
    }

    override fun onDestroy() {
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
        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            if (!hasMicrophonePermission()) {
                onError(getString(R.string.voiceprint_microphone_required))
            }
            renderControls()
        }
    }

    override fun onStateChanged(state: VoiceprintController.State?) {
        when (state) {
            VoiceprintController.State.NEW -> statusView!!.setText(R.string.voiceprint_status_waiting)
            VoiceprintController.State.LOADING_MODEL -> statusView!!.setText(R.string.voiceprint_status_loading)
            VoiceprintController.State.RECORDING -> statusView!!.setText(R.string.voiceprint_status_recording)
            VoiceprintController.State.PROCESSING -> statusView!!.setText(R.string.voiceprint_status_processing)
            VoiceprintController.State.IDLE -> statusView!!.setText(R.string.voiceprint_status_ready)
            VoiceprintController.State.CLOSED -> statusView!!.setText(R.string.voiceprint_status_closed)
            else -> {}
        }
        renderControls()
    }

    override fun onModelReady(loadMillis: Long, dimension: Int) {
        modelLoadMillis = loadMillis
        metricsView!!.setText(
            getString(
                R.string.voiceprint_metrics_model,
                loadMillis,
                dimension,
                Debug.getPss()
            )
        )
        appendLog(getString(R.string.voiceprint_log_model_ready, loadMillis, dimension))
    }

    override fun onProfilesChanged(updatedProfiles: MutableList<VoiceprintProfile>) {
        profiles.clear()
        profiles.addAll(updatedProfiles)
        profileAdapter!!.notifyDataSetChanged()
        acceptedEnrollmentSamples = 0
        progressView!!.setText(R.string.voiceprint_enrollment_progress_empty)
        renderControls()
    }

    override fun onEnrollmentProgress(
        acceptedSamples: Int,
        requiredSamples: Int,
        quality: VoiceprintAudioQuality.Result
    ) {
        acceptedEnrollmentSamples = acceptedSamples
        progressView!!.setText(
            getString(
                R.string.voiceprint_enrollment_progress,
                acceptedSamples,
                requiredSamples,
                quality.rmsDbfs,
                quality.clippingRatio * 100.0
            )
        )
        renderControls()
    }

    override fun onAudioRejected(quality: VoiceprintAudioQuality.Result) {
        resultView!!.setText(
            getString(
                R.string.voiceprint_audio_rejected,
                quality.reason!!.name,
                quality.rmsDbfs,
                quality.clippingRatio * 100.0
            )
        )
    }

    override fun onMatch(match: VoiceprintMatch, inferenceMillis: Long) {
        if (match.isAccepted) {
            resultView!!.setText(
                getString(
                    R.string.voiceprint_result_match,
                    match.profile!!.displayName,
                    match.score,
                    match.threshold
                )
            )
        } else {
            resultView!!.setText(
                getString(
                    R.string.voiceprint_result_unknown,
                    match.score,
                    match.threshold
                )
            )
        }
        metricsView!!.setText(
            getString(
                R.string.voiceprint_metrics_result,
                modelLoadMillis,
                inferenceMillis,
                Debug.getPss()
            )
        )
    }

    override fun onError(message: String?) {
        statusView!!.setText(getString(R.string.voiceprint_status_error, message))
        appendLog(getString(R.string.voiceprint_log_error, message))
        renderControls()
    }

    override fun onLog(message: String?) {
        appendLog(message)
    }

    private fun bindViews() {
        statusView = findViewById<TextView>(R.id.voiceprint_status)
        progressView = findViewById<TextView>(R.id.voiceprint_enrollment_progress)
        resultView = findViewById<TextView>(R.id.voiceprint_result)
        metricsView = findViewById<TextView>(R.id.voiceprint_metrics)
        logView = findViewById<TextView>(R.id.voiceprint_log)
        nameInput = findViewById<EditText>(R.id.voiceprint_name_input)
        profileSpinner = findViewById<Spinner>(R.id.voiceprint_profile_spinner)
        recordButton = findViewById<Button>(R.id.voiceprint_record_button)
        finishButton = findViewById<Button>(R.id.voiceprint_finish_button)
        identifyButton = findViewById<Button>(R.id.voiceprint_identify_button)
        verifyButton = findViewById<Button>(R.id.voiceprint_verify_button)
        deleteButton = findViewById<Button>(R.id.voiceprint_delete_button)
        clearButton = findViewById<Button>(R.id.voiceprint_clear_button)
    }

    private fun configurePage() {
        setTitle(engineType()!!.label())
        (findViewById<View?>(R.id.voiceprint_engine_title) as TextView).setText(engineType()!!.label())
        profileAdapter = ArrayAdapter<VoiceprintProfile?>(
            this,
            android.R.layout.simple_spinner_item,
            profiles
        )
        profileAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner!!.setAdapter(profileAdapter)

        recordButton!!.setOnClickListener(View.OnClickListener { view: View? -> recordEnrollmentSample() })
        finishButton!!.setOnClickListener(View.OnClickListener { view: View? -> controller!!.finishEnrollment() })
        identifyButton!!.setOnClickListener(View.OnClickListener { view: View? -> controller!!.identify() })
        verifyButton!!.setOnClickListener(View.OnClickListener { view: View? ->
            val selected = selectedProfile()
            if (selected != null) {
                controller!!.verify(selected.id)
            }
        })
        deleteButton!!.setOnClickListener(View.OnClickListener { view: View? -> confirmDeleteSelected() })
        clearButton!!.setOnClickListener(View.OnClickListener { view: View? -> confirmClearAll() })
        renderControls()
    }

    private fun createController() {
        try {
            val repository = VoiceprintRepository(
                File(getFilesDir(), "voiceprint")
            )
            val engine = if (engineType() == VoiceprintEngineType.SHERPA)
                SherpaVoiceprintEngine(this)
            else
                VoskVoiceprintEngine(this)
            controller = VoiceprintController(this, repository, engine, this)
        } catch (exception: Exception) {
            statusView!!.setText(
                getString(
                    R.string.voiceprint_status_error,
                    safeMessage(exception)
                )
            )
            appendLog(getString(R.string.voiceprint_log_error, safeMessage(exception)))
        }
    }

    private fun recordEnrollmentSample() {
        if (!hasMicrophonePermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_REQUEST
            )
            return
        }
        controller!!.recordEnrollment(nameInput!!.getText().toString())
    }

    private fun confirmDeleteSelected() {
        val selected = selectedProfile()
        if (selected == null) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.voiceprint_delete_confirm_title)
            .setMessage(
                getString(
                    R.string.voiceprint_delete_confirm_message,
                    selected.displayName
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                R.string.voiceprint_delete_profile,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    controller!!.deleteProfile(
                        selected.id
                    )
                })
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.voiceprint_clear_confirm_title)
            .setMessage(R.string.voiceprint_clear_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                R.string.voiceprint_clear_all,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> controller!!.clearAll() })
            .show()
    }

    private fun selectedProfile(): VoiceprintProfile? {
        val selected = profileSpinner!!.getSelectedItem()
        return if (selected is VoiceprintProfile) selected else null
    }

    private fun hasMicrophonePermission(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun renderControls() {
        val currentState = if (controller == null)
            VoiceprintController.State.NEW
        else
            controller!!.state
        val microphonePermission = hasMicrophonePermission()
        val ready = currentState == VoiceprintController.State.IDLE
                && microphonePermission
        val hasProfiles = !profiles.isEmpty()
        val profileActionsEnabled: Boolean = profileActionsEnabled(
            currentState,
            microphonePermission,
            hasProfiles,
            acceptedEnrollmentSamples
        )
        recordButton!!.setEnabled(ready && acceptedEnrollmentSamples < 3)
        finishButton!!.setEnabled(ready && acceptedEnrollmentSamples == 3)
        nameInput!!.setEnabled(ready && acceptedEnrollmentSamples == 0)
        profileSpinner!!.setEnabled(profileActionsEnabled)
        identifyButton!!.setEnabled(profileActionsEnabled)
        verifyButton!!.setEnabled(profileActionsEnabled)
        deleteButton!!.setEnabled(profileActionsEnabled)
        clearButton!!.setEnabled(profileActionsEnabled)
    }

    private fun appendLog(message: String?) {
        logEntries.add(logTimeFormat.format(Date()) + "  " + message)
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.removeAt(0)
        }
        val value = StringBuilder()
        for (entry in logEntries) {
            if (value.length > 0) {
                value.append('\n')
            }
            value.append(entry)
        }
        logView!!.setText(value)
    }

    companion object {
        private const val MICROPHONE_PERMISSION_REQUEST = 2401
        private const val MAX_LOG_ENTRIES = 100

        @JvmStatic
        fun profileActionsEnabled(
            state: VoiceprintController.State?,
            microphonePermission: Boolean,
            hasProfiles: Boolean,
            acceptedEnrollmentSamples: Int
        ): Boolean {
            return state == VoiceprintController.State.IDLE && microphonePermission
                    && hasProfiles
                    && acceptedEnrollmentSamples == 0
        }

        private fun safeMessage(exception: Exception): String {
            val message = exception.message
            return if (message == null || message.trim { it <= ' ' }.isEmpty())
                exception.javaClass.getSimpleName()
            else
                message
        }
    }
}
