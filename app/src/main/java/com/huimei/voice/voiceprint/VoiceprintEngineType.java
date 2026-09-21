package com.huimei.voice.voiceprint;

public enum VoiceprintEngineType {
    SHERPA(
            "sherpa",
            "Sherpa-ONNX CAM++",
            "3dspeaker-campplus-zh-cn-16k-common",
            0.60f),
    VOSK(
            "vosk",
            "Vosk x-vector",
            "vosk-model-spk-0.4",
            0.50f);

    private final String id;
    private final String label;
    private final String modelId;
    private final float initialThreshold;

    VoiceprintEngineType(String id, String label, String modelId, float initialThreshold) {
        this.id = id;
        this.label = label;
        this.modelId = modelId;
        this.initialThreshold = initialThreshold;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String modelId() {
        return modelId;
    }

    public float initialThreshold() {
        return initialThreshold;
    }
}
