package com.huimei.voice.ui;

import com.huimei.voice.model.RecognitionAction;
import com.huimei.voice.model.VoiceLanguage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class EventLogFormatter {
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    public String eventLine(
            VoiceLanguage language,
            String type,
            RecognitionAction action) {
        return String.format(
                Locale.ROOT,
                "%s | %s | %s | %s | %s | %s",
                timeFormat.format(new Date()),
                language.getDisplayName(),
                type,
                action.getOriginalText(),
                action.getMatch().getDisplayPhrase(),
                action.getMatch().getEvent().name());
    }

    public String diagnosticLine(String message) {
        return timeFormat.format(new Date()) + " | 系统 | " + message;
    }
}
