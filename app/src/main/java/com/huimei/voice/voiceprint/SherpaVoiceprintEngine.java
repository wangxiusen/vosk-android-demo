package com.huimei.voice.voiceprint;

import android.content.Context;
import android.content.res.AssetManager;

import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;

public final class SherpaVoiceprintEngine implements VoiceprintEngine {
    private static final int MODEL_SAMPLE_RATE = 16000;

    private final AssetManager assets;
    private SpeakerEmbeddingExtractor extractor;

    public SherpaVoiceprintEngine(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        Context applicationContext = context.getApplicationContext();
        assets = (applicationContext == null ? context : applicationContext).getAssets();
    }

    @Override
    public VoiceprintEngineType type() {
        return VoiceprintEngineType.SHERPA;
    }

    @Override
    public String modelId() {
        return type().modelId();
    }

    @Override
    public synchronized int dimension() {
        return extractor == null ? 0 : extractor.dim();
    }

    @Override
    public synchronized void initialize() {
        if (extractor != null) {
            return;
        }
        SpeakerEmbeddingExtractorConfig config = new SpeakerEmbeddingExtractorConfig(
                VoiceprintModelManifest.SHERPA_MODEL_ASSET,
                2,
                false,
                "cpu");
        SpeakerEmbeddingExtractor created = new SpeakerEmbeddingExtractor(assets, config);
        if (created.dim() <= 0) {
            created.release();
            throw new IllegalStateException("Sherpa speaker model has an invalid dimension");
        }
        extractor = created;
    }

    @Override
    public synchronized float[] extract(short[] pcm16, int sampleRate) {
        if (extractor == null) {
            throw new IllegalStateException("Sherpa voiceprint engine is not initialized");
        }
        validateAudio(pcm16, sampleRate);
        float[] samples = new float[pcm16.length];
        for (int i = 0; i < pcm16.length; i++) {
            samples[i] = pcm16[i] / 32768f;
        }

        OnlineStream stream = extractor.createStream();
        try {
            stream.acceptWaveform(samples, sampleRate);
            stream.inputFinished();
            if (!extractor.isReady(stream)) {
                throw new IllegalArgumentException("Audio is too short for a Sherpa speaker embedding");
            }
            float[] embedding = extractor.compute(stream);
            if (embedding.length != extractor.dim()) {
                throw new IllegalStateException("Sherpa returned an unexpected embedding dimension");
            }
            return VoiceprintMatcher.normalize(embedding);
        } finally {
            stream.release();
        }
    }

    @Override
    public synchronized void close() {
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
    }

    private static void validateAudio(short[] pcm16, int sampleRate) {
        if (pcm16 == null || pcm16.length == 0) {
            throw new IllegalArgumentException("PCM audio is required");
        }
        if (sampleRate != MODEL_SAMPLE_RATE) {
            throw new IllegalArgumentException("Sherpa speaker model requires 16 kHz audio");
        }
    }
}
