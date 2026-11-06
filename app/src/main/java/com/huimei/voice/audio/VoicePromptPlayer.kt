package com.huimei.voice.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import androidx.annotation.RawRes
import java.util.Objects

class VoicePromptPlayer(context: Context) : AutoCloseable {
    interface Listener {
        fun onCompleted()

        fun onError(message: String?)
    }

    private val context: Context?
    private var mediaPlayer: MediaPlayer? = null
    private var generation = 0
    private var closed = false

    init {
        this.context = context.getApplicationContext()
    }

    fun play(@RawRes resourceId: Int, listener: Listener?): Boolean {
        Objects.requireNonNull<Listener?>(listener, "listener")
        if (closed) {
            listener!!.onError("播放器已经关闭")
            return false
        }

        stop()
        val playbackGeneration = ++generation
        val created: MediaPlayer?
        try {
            created = MediaPlayer.create(context, resourceId)
        } catch (error: RuntimeException) {
            listener!!.onError(safeMessage(error))
            return false
        }
        if (created == null) {
            listener!!.onError("无法加载提示音资源")
            return false
        }

        mediaPlayer = created
        created.setOnCompletionListener(
            OnCompletionListener { player: MediaPlayer? ->
                finish(
                    playbackGeneration,
                    listener!!,
                    null
                )
            })
        created.setOnErrorListener(MediaPlayer.OnErrorListener { player: MediaPlayer?, what: Int, extra: Int ->
            finish(
                playbackGeneration,
                listener!!,
                "MediaPlayer error what=" + what + ", extra=" + extra
            )
            true
        })
        try {
            created.start()
            return true
        } catch (error: RuntimeException) {
            finish(playbackGeneration, listener!!, safeMessage(error))
            return false
        }
    }

    private fun finish(playbackGeneration: Int, listener: Listener, errorMessage: String?) {
        if (playbackGeneration != generation) {
            return
        }
        releaseCurrent()
        generation++
        if (errorMessage == null) {
            listener.onCompleted()
        } else {
            listener.onError(errorMessage)
        }
    }

    fun stop() {
        generation++
        releaseCurrent()
    }

    private fun releaseCurrent() {
        if (mediaPlayer == null) {
            return
        }
        mediaPlayer!!.setOnCompletionListener(null)
        mediaPlayer!!.setOnErrorListener(null)
        mediaPlayer!!.release()
        mediaPlayer = null
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        stop()
    }

    companion object {
        private fun safeMessage(error: RuntimeException): String? {
            return if (error.message == null) error.javaClass.getSimpleName() else error.message
        }
    }
}
