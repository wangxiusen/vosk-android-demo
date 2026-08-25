package com.huimei.voice.avatar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;

public final class VoiceAvatarStateMachineTest {
    @Test
    public void talksOnlyWhilePromptIsPlaying() throws Exception {
        Object stateMachine = newStateMachine();
        Method isTalking = stateMachine.getClass().getMethod("isTalking");

        assertFalse((Boolean) isTalking.invoke(stateMachine));
        stateMachine.getClass().getMethod("onPromptStarted").invoke(stateMachine);
        assertTrue((Boolean) isTalking.invoke(stateMachine));
        stateMachine.getClass().getMethod("onPromptFinished").invoke(stateMachine);
        assertFalse((Boolean) isTalking.invoke(stateMachine));
    }

    @Test
    public void returnsToIdleAfterPromptFailureOrStop() throws Exception {
        Object stateMachine = newStateMachine();
        Method isTalking = stateMachine.getClass().getMethod("isTalking");

        stateMachine.getClass().getMethod("onPromptStarted").invoke(stateMachine);
        stateMachine.getClass().getMethod("onPromptFailed").invoke(stateMachine);
        assertFalse((Boolean) isTalking.invoke(stateMachine));

        stateMachine.getClass().getMethod("onPromptStarted").invoke(stateMachine);
        stateMachine.getClass().getMethod("onStopped").invoke(stateMachine);
        assertFalse((Boolean) isTalking.invoke(stateMachine));
    }

    private Object newStateMachine() throws Exception {
        try {
            return Class.forName("com.huimei.voice.avatar.VoiceAvatarStateMachine")
                    .getConstructor()
                    .newInstance();
        } catch (ClassNotFoundException error) {
            fail("VoiceAvatarStateMachine is required for prompt-driven mouth state");
            throw error;
        }
    }
}
