package com.huimei.voice.audio;

import android.content.Context;
import android.media.MediaPlayer;

import androidx.annotation.RawRes;

import java.util.Objects;

public final class VoicePromptPlayer implements AutoCloseable {
    public interface Listener {
        void onCompleted();

        void onError(String message);
    }

    private final Context context;
    private MediaPlayer mediaPlayer;
    private int generation;
    private boolean closed;

    public VoicePromptPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean play(@RawRes int resourceId, Listener listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            listener.onError("播放器已经关闭");
            return false;
        }

        stop();
        int playbackGeneration = ++generation;
        MediaPlayer created;
        try {
            created = MediaPlayer.create(context, resourceId);
        } catch (RuntimeException error) {
            listener.onError(safeMessage(error));
            return false;
        }
        if (created == null) {
            listener.onError("无法加载提示音资源");
            return false;
        }

        mediaPlayer = created;
        created.setOnCompletionListener(
                player -> finish(playbackGeneration, listener, null));
        created.setOnErrorListener((player, what, extra) -> {
            finish(
                    playbackGeneration,
                    listener,
                    "MediaPlayer error what=" + what + ", extra=" + extra);
            return true;
        });
        try {
            created.start();
            return true;
        } catch (RuntimeException error) {
            finish(playbackGeneration, listener, safeMessage(error));
            return false;
        }
    }

    private void finish(int playbackGeneration, Listener listener, String errorMessage) {
        if (playbackGeneration != generation) {
            return;
        }
        releaseCurrent();
        generation++;
        if (errorMessage == null) {
            listener.onCompleted();
        } else {
            listener.onError(errorMessage);
        }
    }

    public void stop() {
        generation++;
        releaseCurrent();
    }

    private void releaseCurrent() {
        if (mediaPlayer == null) {
            return;
        }
        mediaPlayer.setOnCompletionListener(null);
        mediaPlayer.setOnErrorListener(null);
        mediaPlayer.release();
        mediaPlayer = null;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        stop();
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
