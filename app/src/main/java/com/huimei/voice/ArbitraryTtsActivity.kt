package com.huimei.voice

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieListener
import com.huimei.voice.avatar.LottieAvatarMotionController
import com.huimei.voice.avatar.MouthShape
import com.huimei.voice.tts.OfflineTtsPlayer
import com.huimei.voice.tts.TtsLanguage
import com.huimei.voice.tts.TtsPause
import com.huimei.voice.tts.TtsSpeed
import com.huimei.voice.tts.TtsVoiceCatalog
import com.huimei.voice.tts.TtsVoiceOption
import com.huimei.voice.tts.TtsVolume
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class ArbitraryTtsActivity : AppCompatActivity(), OfflineTtsPlayer.Listener {
    private var player: OfflineTtsPlayer? = null
    private var animationView: LottieAnimationView? = null
    private var avatarController: LottieAvatarMotionController? = null
    private var languageGroup: RadioGroup? = null
    private var voiceSpinner: Spinner? = null
    private var speedSpinner: Spinner? = null
    private var pauseSpinner: Spinner? = null
    private var volumeSpinner: Spinner? = null
    private var textInput: EditText? = null
    private var playButton: Button? = null
    private var stopButton: Button? = null
    private var statusText: TextView? = null
    private var currentVoices: List<TtsVoiceOption>? = null
    private var currentLanguage = TtsLanguage.CHINESE
    private var modelReady = false
    private var modelLoading = false
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arbitrary_tts)
        bindViews()
        configureOptions()

        avatarController = LottieAvatarMotionController(animationView!!)
        animationView!!.setFailureListener(LottieListener { error: Throwable? ->
            onError(
                "动画加载失败：" + Companion.safeMessage(
                    error!!
                )
            )
        })
        avatarController!!.showIdleEyesOnly()
        animationView!!.playAnimation()

        player = OfflineTtsPlayer(this, this)
        modelLoading = true
        player!!.initialize(currentLanguage)
    }

    private fun bindViews() {
        animationView = findViewById<LottieAnimationView?>(R.id.tts_avatar_animation)
        languageGroup = findViewById<RadioGroup>(R.id.tts_language_group)
        voiceSpinner = findViewById<Spinner>(R.id.tts_voice_spinner)
        speedSpinner = findViewById<Spinner>(R.id.tts_speed_spinner)
        pauseSpinner = findViewById<Spinner>(R.id.tts_pause_spinner)
        volumeSpinner = findViewById<Spinner>(R.id.tts_volume_spinner)
        textInput = findViewById<EditText>(R.id.tts_text_input)
        playButton = findViewById<Button>(R.id.tts_play_button)
        stopButton = findViewById<Button>(R.id.tts_stop_button)
        statusText = findViewById<TextView>(R.id.tts_status)
    }

    private fun configureOptions() {
        updateLanguage(TtsLanguage.CHINESE)

        val speedLabels: MutableList<String?> = ArrayList<String?>()
        for (speed in TtsSpeed.entries) {
            speedLabels.add(speed.displayName())
        }
        speedSpinner!!.setAdapter(createAdapter(speedLabels))
        speedSpinner!!.setSelection(TtsSpeed.NORMAL.ordinal)

        val pauseLabels: MutableList<String?> = ArrayList<String?>()
        for (pause in TtsPause.entries) {
            pauseLabels.add(pause.displayName())
        }
        pauseSpinner!!.setAdapter(createAdapter(pauseLabels))
        pauseSpinner!!.setSelection(TtsPause.NATURAL.ordinal)

        val volumeLabels: MutableList<String?> = ArrayList<String?>()
        for (volume in TtsVolume.entries) {
            volumeLabels.add(volume.displayName())
        }
        volumeSpinner!!.setAdapter(createAdapter(volumeLabels))
        volumeSpinner!!.setSelection(TtsVolume.ENHANCED.ordinal)

        languageGroup!!.setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener listener@{ group: RadioGroup?, checkedId: Int ->
            if (checkedId == View.NO_ID) {
                return@listener
            }
            val language = if (checkedId == R.id.tts_language_english)
                TtsLanguage.ENGLISH
            else
                TtsLanguage.CHINESE
            if (language == currentLanguage) {
                return@listener
            }
            if (playing) {
                player!!.stop()
            }
            currentLanguage = language
            modelReady = false
            modelLoading = true
            playing = false
            updateLanguage(language)
            statusText!!.setText(
                getString(
                    R.string.tts_status_switching,
                    if (language == TtsLanguage.CHINESE) "中文" else "英文"
                )
            )
            updateControlState()
            player!!.loadLanguage(language)
        })
        playButton!!.setOnClickListener(View.OnClickListener { view: View? -> startSpeaking() })
        stopButton!!.setOnClickListener(View.OnClickListener { view: View? -> player!!.stop() })
    }

    private fun updateLanguage(language: TtsLanguage) {
        currentVoices = TtsVoiceCatalog.voicesFor(language)
        val voiceLabels: MutableList<String?> = ArrayList<String?>()
        for (voice in currentVoices!!) {
            voiceLabels.add(voice.displayName())
        }
        voiceSpinner!!.setAdapter(createAdapter(voiceLabels))
        voiceSpinner!!.setSelection(0)
        textInput!!.setText(language.sampleText())
        textInput!!.setSelection(textInput!!.length())
    }

    private fun createAdapter(values: MutableList<String?>): ArrayAdapter<String?> {
        val adapter = ArrayAdapter<String?>(
            this,
            android.R.layout.simple_spinner_item,
            values
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun startSpeaking() {
        val text: String = textInput!!.getText().toString().trim { it <= ' ' }
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.tts_empty_text, Toast.LENGTH_SHORT).show()
            return
        }
        val voice = currentVoices!!.get(voiceSpinner!!.getSelectedItemPosition())
        val speed: TtsSpeed = TtsSpeed.entries[speedSpinner!!.getSelectedItemPosition()]
        val pause: TtsPause = TtsPause.entries[pauseSpinner!!.getSelectedItemPosition()]
        val volume: TtsVolume = TtsVolume.entries[volumeSpinner!!.getSelectedItemPosition()]
        if (!player!!.speak(text, voice, speed, pause, volume)) {
            onError("模型尚未就绪，暂时无法口播")
        }
    }

    override fun onReady(numberOfSpeakers: Int) {
        modelLoading = false
        modelReady = true
        statusText!!.setText(getString(R.string.tts_status_ready, numberOfSpeakers))
        updateControlState()
    }

    override fun onStarted() {
        playing = true
        val voice = currentVoices!!.get(voiceSpinner!!.getSelectedItemPosition())
        val speed: TtsSpeed = TtsSpeed.entries[speedSpinner!!.getSelectedItemPosition()]
        val pause: TtsPause = TtsPause.entries[pauseSpinner!!.getSelectedItemPosition()]
        val volume: TtsVolume = TtsVolume.entries[volumeSpinner!!.getSelectedItemPosition()]
        statusText!!.setText(
            getString(
                R.string.tts_status_playing,
                voice.displayName(),
                speed.displayName(),
                pause.displayName(),
                volume.displayName()
            )
        )
        updateControlState()
    }

    override fun onMouthShape(shape: MouthShape?) {
        avatarController!!.showMouth(shape)
    }

    override fun onCompleted() {
        playing = false
        statusText!!.setText(R.string.tts_status_completed)
        updateControlState()
    }

    override fun onStopped() {
        playing = false
        statusText!!.setText(R.string.tts_status_stopped)
        updateControlState()
    }

    override fun onError(message: String?) {
        playing = false
        if (modelLoading) {
            modelLoading = false
            modelReady = false
        }
        statusText!!.setText(getString(R.string.tts_status_error, message))
        avatarController!!.showMouth(MouthShape.CLOSED)
        updateControlState()
    }

    private fun updateControlState() {
        languageGroup!!.setEnabled(!playing)
        Companion.setRadioChildrenEnabled(languageGroup!!, !playing)
        voiceSpinner!!.setEnabled(!playing)
        speedSpinner!!.setEnabled(!playing)
        pauseSpinner!!.setEnabled(!playing)
        volumeSpinner!!.setEnabled(!playing)
        textInput!!.setEnabled(!playing)
        playButton!!.setEnabled(modelReady && !playing)
        stopButton!!.setEnabled(playing)
    }

    override fun onDestroy() {
        if (player != null) {
            player!!.close()
        }
        if (animationView != null) {
            animationView!!.cancelAnimation()
        }
        super.onDestroy()
    }

    companion object {
        private fun setRadioChildrenEnabled(group: RadioGroup, enabled: Boolean) {
            for (index in 0..<group.getChildCount()) {
                val child = group.getChildAt(index)
                child.setEnabled(enabled)
            }
        }

        private fun safeMessage(error: Throwable): String {
            val message = error.message
            return if (message == null || message.trim { it <= ' ' }.isEmpty())
                error.javaClass.getSimpleName()
            else
                message
        }
    }
}
