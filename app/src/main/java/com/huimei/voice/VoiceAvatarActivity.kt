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
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieListener
import com.huimei.voice.audio.VoicePromptCatalog.rawResourceFor
import com.huimei.voice.audio.VoicePromptPlayer
import com.huimei.voice.avatar.LottieAvatarMotionController
import com.huimei.voice.avatar.VoiceAvatarStateMachine
import com.huimei.voice.model.CommandEvent
import com.huimei.voice.model.RecognitionAction
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.CommandCatalog
import com.huimei.voice.recognition.VoiceRecognitionController
import com.huimei.voice.recognition.VoiceRecognitionListener
import com.huimei.voice.ui.EventLogFormatter
import java.util.Locale

class VoiceAvatarActivity : AppCompatActivity(), VoiceRecognitionListener {
    private val logFormatter = EventLogFormatter()
    private val avatarState = VoiceAvatarStateMachine()
    private var controller: VoiceRecognitionController? = null
    private var promptPlayer: VoicePromptPlayer? = null
    private var animationView: LottieAnimationView? = null
    private var avatarMotionController: LottieAvatarMotionController? = null
    private var selectedLanguage = VoiceLanguage.CHINESE
    private var metadataText: TextView? = null
    private var statusText: TextView? = null
    private var countdownText: TextView? = null
    private var avatarStateText: TextView? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null
    private var listenButton: Button? = null
    private var hasLogLines = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_avatar)

        bindViews()
        controller = VoiceRecognitionController(this, this)
        promptPlayer = VoicePromptPlayer(this)
        avatarMotionController = LottieAvatarMotionController(animationView!!)
        animationView!!.setFailureListener(LottieListener { error: Throwable? ->
            onDiagnostic(
                "动画加载失败：" + Companion.safeMessage(
                    error!!
                )
            )
        })
        avatarMotionController!!.showIdleEyesOnly()
        animationView!!.playAnimation()
        updateLanguageMetadata()
        updateAvatarState()
        onStatus("等待麦克风权限")

        (findViewById<View?>(R.id.voice_avatar_language_group) as RadioGroup)
            .setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener { group: RadioGroup?, checkedId: Int ->
                selectLanguage(
                    checkedId
                )
            })
        listenButton!!.setOnClickListener(View.OnClickListener { view: View? -> toggleListening() })
        findViewById<View?>(R.id.voice_avatar_clear_log_button)!!
            .setOnClickListener(View.OnClickListener { view: View? -> clearLog() })

        ensureMicrophonePermissionAndStart()
    }

    private fun bindViews() {
        animationView = findViewById<LottieAnimationView?>(R.id.voice_avatar_animation)
        metadataText = findViewById<TextView>(R.id.voice_avatar_metadata)
        statusText = findViewById<TextView>(R.id.voice_avatar_status)
        countdownText = findViewById<TextView>(R.id.voice_avatar_countdown)
        avatarStateText = findViewById<TextView>(R.id.voice_avatar_animation_state)
        logText = findViewById<TextView>(R.id.voice_avatar_log_text)
        logScroll = findViewById<ScrollView>(R.id.voice_avatar_log_scroll)
        listenButton = findViewById<Button>(R.id.voice_avatar_listen_button)
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
        stopPromptAndResetAvatar()
        selectedLanguage = if (checkedId == R.id.voice_avatar_language_english)
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
        metadataText!!.setText(
            getString(
                R.string.voice_avatar_metadata_value,
                catalog.assetModelPath(),
                catalog.wakePhrase()
            )
        )
        countdownText!!.setText(
            getString(
                R.string.countdown_value,
                getString(R.string.countdown_sleeping)
            )
        )
    }

    private fun toggleListening() {
        if (controller!!.isRunning) {
            stopPromptAndResetAvatar()
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
        playPromptWithAvatar(language, action.match!!.event)
    }

    override fun onCommand(language: VoiceLanguage, action: RecognitionAction) {
        appendLog(logFormatter.eventLine(language, "命令", action))
        playPromptWithAvatar(language, action.match!!.event)
    }

    private fun playPromptWithAvatar(language: VoiceLanguage, event: CommandEvent) {
        val resourceId = rawResourceFor(language, event)
        if (resourceId == 0 || !controller!!.pauseForPrompt()) {
            return
        }

        avatarState.onPromptStarted()
        updateAvatarState()
        onDiagnostic("正在口播" + language.displayName + "提示音：" + event.name)
        promptPlayer!!.play(resourceId, object : VoicePromptPlayer.Listener {
            override fun onCompleted() {
                avatarState.onPromptFinished()
                updateAvatarState()
                controller!!.resumeAfterPrompt()
            }

            override fun onError(message: String?) {
                avatarState.onPromptFailed()
                updateAvatarState()
                onDiagnostic("提示音播放失败：" + message)
                controller!!.resumeAfterPrompt()
            }
        })
    }

    private fun stopPromptAndResetAvatar() {
        promptPlayer!!.stop()
        avatarState.onStopped()
        updateAvatarState()
    }

    private fun updateAvatarState() {
        if (avatarState.isTalking) {
            avatarMotionController!!.showTalking()
            avatarStateText!!.setText(R.string.voice_avatar_state_talking)
        } else {
            avatarMotionController!!.showIdleEyesOnly()
            avatarStateText!!.setText(R.string.voice_avatar_state_idle)
        }
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

    override fun onStart() {
        super.onStart()
        animationView!!.resumeAnimation()
    }

    override fun onStop() {
        animationView!!.pauseAnimation()
        super.onStop()
    }

    override fun onDestroy() {
        if (promptPlayer != null) {
            promptPlayer!!.close()
        }
        avatarState.onStopped()
        if (avatarMotionController != null) {
            updateAvatarState()
        }
        if (controller != null) {
            controller!!.close()
        }
        if (animationView != null) {
            animationView!!.cancelAnimation()
        }
        super.onDestroy()
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 1002

        private fun safeMessage(error: Throwable): String? {
            return if (error.message == null) error.javaClass.getSimpleName() else error.message
        }
    }
}
