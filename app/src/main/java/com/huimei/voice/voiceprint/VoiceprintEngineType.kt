package com.huimei.voice.voiceprint

enum class VoiceprintEngineType(
    id: String,
    label: String,
    modelId: String,
    initialThreshold: Float
) {
    SHERPA(
        "sherpa",
        "Sherpa-ONNX CAM++",
        "3dspeaker-campplus-zh-cn-16k-common",
        0.60f
    ),
    VOSK(
        "vosk",
        "Vosk x-vector",
        "vosk-model-spk-0.4",
        0.50f
    );

    private val id: String?
    private val label: String?
    private val modelId: String?
    private val initialThreshold: Float

    init {
        this.id = id
        this.label = label
        this.modelId = modelId
        this.initialThreshold = initialThreshold
    }

    fun id(): String? {
        return id
    }

    fun label(): String? {
        return label
    }

    fun modelId(): String? {
        return modelId
    }

    fun initialThreshold(): Float {
        return initialThreshold
    }
}
