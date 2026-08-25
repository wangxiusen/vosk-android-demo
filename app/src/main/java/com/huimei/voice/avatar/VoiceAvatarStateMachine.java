package com.huimei.voice.avatar;

public final class VoiceAvatarStateMachine {
    private boolean talking;

    public boolean isTalking() {
        return talking;
    }

    public void onPromptStarted() {
        talking = true;
    }

    public void onPromptFinished() {
        talking = false;
    }

    public void onPromptFailed() {
        talking = false;
    }

    public void onStopped() {
        talking = false;
    }
}
