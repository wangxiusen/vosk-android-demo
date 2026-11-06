package com.huimei.voice

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieListener

class LottieDemoActivity : AppCompatActivity() {
    private var animationView: LottieAnimationView? = null
    private var statusText: TextView? = null
    private var shouldPlay = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lottie_demo)

        animationView = findViewById<LottieAnimationView>(R.id.medical_assistant_animation)
        statusText = findViewById<TextView>(R.id.animation_status)
        animationView!!.setFailureListener(LottieListener { error: Throwable? ->
            statusText!!.setText(
                getString(R.string.lottie_status_error, error!!.message)
            )
        })

        findViewById<View?>(R.id.play_animation_button)!!.setOnClickListener(View.OnClickListener { view: View? -> playAnimation() })
        findViewById<View?>(R.id.pause_animation_button)!!.setOnClickListener(View.OnClickListener { view: View? -> pauseAnimation() })
        findViewById<View?>(R.id.replay_animation_button)!!.setOnClickListener(View.OnClickListener { view: View? -> replayAnimation() })

        playAnimation()
    }

    private fun playAnimation() {
        shouldPlay = true
        animationView!!.playAnimation()
        statusText!!.setText(R.string.lottie_status_playing)
    }

    private fun pauseAnimation() {
        shouldPlay = false
        animationView!!.pauseAnimation()
        statusText!!.setText(R.string.lottie_status_paused)
    }

    private fun replayAnimation() {
        shouldPlay = true
        animationView!!.setProgress(0.0f)
        animationView!!.playAnimation()
        statusText!!.setText(R.string.lottie_status_playing)
    }

    override fun onStart() {
        super.onStart()
        if (shouldPlay) {
            animationView!!.resumeAnimation()
        }
    }

    override fun onStop() {
        animationView!!.pauseAnimation()
        super.onStop()
    }
}
