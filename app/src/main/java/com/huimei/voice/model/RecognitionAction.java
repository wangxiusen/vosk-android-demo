package com.huimei.voice.model;

public final class RecognitionAction {
    public enum Type {
        NONE,
        WOKE_UP,
        COMMAND,
        TIMED_OUT
    }

    private static final RecognitionAction NONE = new RecognitionAction(Type.NONE, null, null);
    private static final RecognitionAction TIMED_OUT = new RecognitionAction(Type.TIMED_OUT, null, null);

    private final Type type;
    private final CommandMatch match;
    private final String originalText;

    private RecognitionAction(Type type, CommandMatch match, String originalText) {
        this.type = type;
        this.match = match;
        this.originalText = originalText;
    }

    public static RecognitionAction none() {
        return NONE;
    }

    public static RecognitionAction wokeUp(CommandMatch match, String originalText) {
        return new RecognitionAction(Type.WOKE_UP, match, originalText);
    }

    public static RecognitionAction command(CommandMatch match, String originalText) {
        return new RecognitionAction(Type.COMMAND, match, originalText);
    }

    public static RecognitionAction timedOut() {
        return TIMED_OUT;
    }

    public Type getType() {
        return type;
    }

    public CommandMatch getMatch() {
        return match;
    }

    public String getOriginalText() {
        return originalText;
    }
}
