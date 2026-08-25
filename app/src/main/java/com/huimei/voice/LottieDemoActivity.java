package com.huimei.voice;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

public final class LottieDemoActivity extends AppCompatActivity {
    private LottieAnimationView animationView;
    private TextView statusText;
    private boolean shouldPlay = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lottie_demo);

        animationView = findViewById(R.id.medical_assistant_animation);
        statusText = findViewById(R.id.animation_status);
        animationView.setFailureListener(error ->
                statusText.setText(getString(R.string.lottie_status_error, error.getMessage())));

        findViewById(R.id.play_animation_button).setOnClickListener(view -> playAnimation());
        findViewById(R.id.pause_animation_button).setOnClickListener(view -> pauseAnimation());
        findViewById(R.id.replay_animation_button).setOnClickListener(view -> replayAnimation());

        playAnimation();
    }

    private void playAnimation() {
        shouldPlay = true;
        animationView.playAnimation();
        statusText.setText(R.string.lottie_status_playing);
    }

    private void pauseAnimation() {
        shouldPlay = false;
        animationView.pauseAnimation();
        statusText.setText(R.string.lottie_status_paused);
    }

    private void replayAnimation() {
        shouldPlay = true;
        animationView.setProgress(0.0f);
        animationView.playAnimation();
        statusText.setText(R.string.lottie_status_playing);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (shouldPlay) {
            animationView.resumeAnimation();
        }
    }

    @Override
    protected void onStop() {
        animationView.pauseAnimation();
        super.onStop();
    }
}
