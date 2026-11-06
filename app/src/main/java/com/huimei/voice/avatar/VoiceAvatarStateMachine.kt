package com.huimei.voice.avatar

class VoiceAvatarStateMachine {
    var isTalking: Boolean = false
        private set

    fun onPromptStarted() {
        this.isTalking = true
    }

    fun onPromptFinished() {
        this.isTalking = false
    }

    fun onPromptFailed() {
        this.isTalking = false
    }

    fun onStopped() {
        this.isTalking = false
    }
}
