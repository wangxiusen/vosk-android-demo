package com.huimei.voice.voiceprint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

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

    public static float[] parseBest(List<String> jsonResults, int minimumFrames) {
        if (jsonResults == null || jsonResults.isEmpty()) {
            throw new IllegalArgumentException("Vosk results are required");
        }
        if (minimumFrames <= 0) {
            throw new IllegalArgumentException("Minimum speaker frame count must be positive");
        }

        float[] bestVector = null;
        int bestFrameCount = -1;
        for (String json : jsonResults) {
            try {
                int frameCount = speakerFrameCount(json);
                float[] vector = parse(json, minimumFrames);
                if (frameCount > bestFrameCount) {
                    bestFrameCount = frameCount;
                    bestVector = vector;
                }
            } catch (IllegalArgumentException ignored) {
                // Endpoint and final results without a speaker vector are expected.
            }
        }
        if (bestVector == null) {
            throw new IllegalArgumentException("Vosk results have no usable speaker vector");
        }
        return bestVector;
    }

    private static int speakerFrameCount(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Vosk result is required");
        }
        try {
            Object value = new JSONObject(json).opt("spk_frames");
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Vosk result has no speaker frames");
            }
            return ((Number) value).intValue();
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Malformed Vosk speaker result", exception);
        }
    }
}
