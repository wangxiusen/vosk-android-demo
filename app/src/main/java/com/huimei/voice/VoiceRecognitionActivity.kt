package com.huimei.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.huimei.voice.audio.VoicePromptCatalog.rawResourceFor
import com.huimei.voice.audio.VoicePromptPlayer
import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.RecognitionAction
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.CommandCatalog
import com.huimei.voice.recognition.VoiceRecognitionController
import com.huimei.voice.recognition.VoiceRecognitionListener
import com.huimei.voice.ui.EventLogFormatter
import java.util.Locale

class VoiceRecognitionActivity : AppCompatActivity(), VoiceRecognitionListener {
    private val logFormatter = EventLogFormatter()
    private var controller: VoiceRecognitionController? = null
    private var promptPlayer: VoicePromptPlayer? = null
    private var selectedLanguage = VoiceLanguage.CHINESE
    private var modelText: TextView? = null
    private var wakePhraseText: TextView? = null
    private var statusText: TextView? = null
    private var countdownText: TextView? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null
    private var listenButton: Button? = null
    private var hasLogLines = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recognition)

        bindViews()
        controller = VoiceRecognitionController(this, this)
        promptPlayer = VoicePromptPlayer(this)
        updateLanguageMetadata()
        onStatus("等待麦克风权限")

        (findViewById<View?>(R.id.language_group) as RadioGroup).setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { group: RadioGroup?, checkedId: Int ->
                selectLanguage(
                    checkedId
                )
            })
        listenButton!!.setOnClickListener(View.OnClickListener { view: View? -> toggleListening() })
        findViewById<View?>(R.id.clear_log_button)!!.setOnClickListener(View.OnClickListener { view: View? -> clearLog() })

        ensureMicrophonePermissionAndStart()
    }

    private fun bindViews() {
        modelText = findViewById<TextView>(R.id.model_text)
        wakePhraseText = findViewById<TextView>(R.id.wake_phrase_text)
        statusText = findViewById<TextView>(R.id.status_text)
        countdownText = findViewById<TextView>(R.id.countdown_text)
        logText = findViewById<TextView>(R.id.log_text)
        logScroll = findViewById<ScrollView>(R.id.log_scroll)
        listenButton = findViewById<Button>(R.id.listen_button)
    }

    private fun ensureMicrophonePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            controller!!.start(selectedLanguage)
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf<String>(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_REQUEST
        )
    }

    private fun selectLanguage(checkedId: Int) {
        promptPlayer!!.stop()
        selectedLanguage = if (checkedId == R.id.language_english)
            VoiceLanguage.ENGLISH
        else
            VoiceLanguage.CHINESE
        updateLanguageMetadata()
        if (controller!!.isRunning) {
            controller!!.switchLanguage(selectedLanguage)
        }
    }

    private fun updateLanguageMetadata() {
        val catalog = CommandCatalog.forLanguage(selectedLanguage)
        modelText!!.setText(getString(R.string.model_value, catalog.assetModelPath()))
        wakePhraseText!!.setText(getString(R.string.wake_phrase_value, catalog.wakePhrase()))
        countdownText!!.setText(
            getString(
                R.string.countdown_value,
                getString(R.string.countdown_sleeping)
            )
        )
    }

    private fun toggleListening() {
        if (controller!!.isRunning) {
            promptPlayer!!.stop()
            controller!!.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            controller!!.start(selectedLanguage)
        } else {
            ensureMicrophonePermissionAndStart()
        }
    }

    private fun clearLog() {
        hasLogLines = false
        logText!!.setText(R.string.event_log_empty)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != RECORD_AUDIO_PERMISSION_REQUEST) {
            return
        }
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            controller!!.start(selectedLanguage)
        } else {
            onStatus("麦克风权限被拒绝")
            onDiagnostic("无法录音；请在系统设置中授予麦克风权限")
        }
    }

    override fun onStatus(status: String?) {
        statusText!!.setText(getString(R.string.status_value, status))
        if (controller != null) {
            listenButton!!.setText(
                if (controller!!.isRunning)
                    R.string.stop_listening
                else
                    R.string.start_listening
            )
        }
    }

    override fun onDiagnostic(message: String?) {
        appendLog(logFormatter.diagnosticLine(message))
    }

    override fun onWakeWindowChanged(awake: Boolean, remainingMillis: Long) {
        val value = if (awake) String.format(Locale.ROOT, "%.1f 秒", remainingMillis / 1000.0) else
            getString(R.string.countdown_sleeping)
        countdownText!!.setText(getString(R.string.countdown_value, value))
    }

    override fun onWakeUp(language: VoiceLanguage, action: RecognitionAction) {
        appendLog(logFormatter.eventLine(language, "唤醒", action))
        playPrompt(language, action.match!!.event)
    }

    override fun onCommand(language: VoiceLanguage, action: RecognitionAction) {
        appendLog(logFormatter.eventLine(language, "命令", action))
        playPrompt(language, action.match!!.event)
    }

    private fun playPrompt(language: VoiceLanguage, event: CommandEvent) {
        val resourceId = rawResourceFor(language, event)
        if (resourceId == 0 || !controller!!.pauseForPrompt()) {
            return
        }
        onDiagnostic("正在播放" + language.displayName + "提示音：" + event.name)
        promptPlayer!!.play(resourceId, object : VoicePromptPlayer.Listener {
            override fun onCompleted() {
                controller!!.resumeAfterPrompt()
            }

            override fun onError(message: String?) {
                onDiagnostic("提示音播放失败：" + message)
                controller!!.resumeAfterPrompt()
            }
        })
    }

    private fun appendLog(line: String?) {
        if (!hasLogLines) {
            logText!!.setText(line)
            hasLogLines = true
        } else {
            logText!!.append("\n" + line)
        }
        logScroll!!.post(Runnable { logScroll!!.fullScroll(ScrollView.FOCUS_DOWN) })
    }

    override fun onDestroy() {
        if (promptPlayer != null) {
            promptPlayer!!.close()
        }
        if (controller != null) {
            controller!!.close()
        }
        super.onDestroy()
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 1001
    }
}
