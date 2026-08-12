package com.huimei.voice.recognition;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;

public final class RecognitionResultParser {
    private RecognitionResultParser() {
    }

    public static Optional<String> parseText(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            String text = new JSONObject(json).optString("text", "").trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (JSONException ignored) {
            return Optional.empty();
        }
    }
}
