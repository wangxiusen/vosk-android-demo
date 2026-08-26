package com.huimei.voice;

import static org.junit.Assert.assertEquals;

import com.huimei.voice.tts.OfflineTtsPlayer;
import com.huimei.voice.tts.TtsPause;
import com.huimei.voice.tts.TtsSpeed;
import com.huimei.voice.tts.TtsVoiceOption;

import org.junit.Test;

import java.lang.reflect.Method;

public final class TtsPlaybackOptionsTest {
    @Test
    public void speakAcceptsTheSelectedPunctuationPause() throws Exception {
        Method speak = OfflineTtsPlayer.class.getMethod(
                "speak",
                String.class,
                TtsVoiceOption.class,
                TtsSpeed.class,
                TtsPause.class);

        assertEquals(boolean.class, speak.getReturnType());
    }
}
