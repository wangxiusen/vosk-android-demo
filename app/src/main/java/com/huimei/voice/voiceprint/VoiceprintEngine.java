package com.huimei.voice.voiceprint;

public interface VoiceprintEngine extends AutoCloseable {
    VoiceprintEngineType type();

    String modelId();

    int dimension();

    void initialize() throws Exception;

    float[] extract(short[] pcm16, int sampleRate) throws Exception;

    @Override
    void close();
}
