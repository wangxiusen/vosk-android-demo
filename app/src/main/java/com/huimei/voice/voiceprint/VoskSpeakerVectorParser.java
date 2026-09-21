package com.huimei.voice.voiceprint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class VoskSpeakerVectorParser {
    private VoskSpeakerVectorParser() {
    }

    public static float[] parse(String json, int minimumFrames) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Vosk result is required");
        }
        if (minimumFrames <= 0) {
            throw new IllegalArgumentException("Minimum speaker frame count must be positive");
        }
        try {
            JSONObject object = new JSONObject(json);
            Object frameValue = object.opt("spk_frames");
            if (!(frameValue instanceof Number)
                    || ((Number) frameValue).intValue() < minimumFrames) {
                throw new IllegalArgumentException("Vosk result has insufficient speaker frames");
            }
            JSONArray array = object.optJSONArray("spk");
            if (array == null || array.length() == 0) {
                throw new IllegalArgumentException("Vosk result has no speaker vector");
            }
            float[] vector = new float[array.length()];
            for (int i = 0; i < array.length(); i++) {
                Object value = array.get(i);
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("Vosk speaker vector must be numeric");
                }
                vector[i] = ((Number) value).floatValue();
                if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                    throw new IllegalArgumentException("Vosk speaker vector must be finite");
                }
            }
            return vector;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Malformed Vosk speaker result", exception);
        }
    }
}
