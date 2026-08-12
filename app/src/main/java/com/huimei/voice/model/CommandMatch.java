package com.huimei.voice.model;

import java.util.Objects;

public final class CommandMatch {
    private final String recognizedPhrase;
    private final String displayPhrase;
    private final CommandEvent event;

    public CommandMatch(String recognizedPhrase, String displayPhrase, CommandEvent event) {
        this.recognizedPhrase = Objects.requireNonNull(recognizedPhrase);
        this.displayPhrase = Objects.requireNonNull(displayPhrase);
        this.event = Objects.requireNonNull(event);
    }

    public String getRecognizedPhrase() {
        return recognizedPhrase;
    }

    public String getDisplayPhrase() {
        return displayPhrase;
    }

    public CommandEvent getEvent() {
        return event;
    }
}
