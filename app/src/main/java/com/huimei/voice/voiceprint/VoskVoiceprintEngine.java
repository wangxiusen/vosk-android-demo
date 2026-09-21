package com.huimei.voice.voiceprint;

import android.content.Context;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.SpeakerModel;

import java.io.File;
import java.util.Arrays;

public final class VoskVoiceprintEngine implements VoiceprintEngine {
    private static final int MODEL_SAMPLE_RATE = 16000;
    private static final int EMBEDDING_DIMENSION = 128;
    private static final int MINIMUM_SPEAKER_FRAMES = 200;
    private static final int PCM_CHUNK_SAMPLES = 4096;

    private final VoiceprintModelInstaller installer;
    private Model recognitionModel;
    private SpeakerModel speakerModel;

    public VoskVoiceprintEngine(Context context) throws Exception {
        installer = new VoiceprintModelInstaller(context);
    }

    @Override
    public VoiceprintEngineType type() {
        return VoiceprintEngineType.VOSK;
    }

    @Override
    public String modelId() {
        return type().modelId();
    }

    @Override
    public int dimension() {
        return EMBEDDING_DIMENSION;
    }

    @Override
    public synchronized void initialize() throws Exception {
        if (recognitionModel != null && speakerModel != null) {
            return;
        }
        close();
        File recognitionDirectory = installer.installChineseRecognitionModel();
        File speakerDirectory = installer.installVoskSpeakerModel();
        Model createdRecognition = null;
        SpeakerModel createdSpeaker = null;
        try {
            createdRecognition = new Model(recognitionDirectory.getAbsolutePath());
            createdSpeaker = new SpeakerModel(speakerDirectory.getAbsolutePath());
            recognitionModel = createdRecognition;
            speakerModel = createdSpeaker;
        } catch (Exception exception) {
            if (createdSpeaker != null) {
                createdSpeaker.close();
            }
            if (createdRecognition != null) {
                createdRecognition.close();
            }
            throw exception;
        }
    }

    @Override
    public synchronized float[] extract(short[] pcm16, int sampleRate) throws Exception {
        if (recognitionModel == null || speakerModel == null) {
            throw new IllegalStateException("Vosk voiceprint engine is not initialized");
        }
        if (pcm16 == null || pcm16.length == 0) {
            throw new IllegalArgumentException("PCM audio is required");
        }
        if (sampleRate != MODEL_SAMPLE_RATE) {
            throw new IllegalArgumentException("Vosk speaker model requires 16 kHz audio");
        }

        try (Recognizer recognizer = new Recognizer(
                recognitionModel, MODEL_SAMPLE_RATE, speakerModel)) {
            for (int offset = 0; offset < pcm16.length; offset += PCM_CHUNK_SAMPLES) {
                int end = Math.min(pcm16.length, offset + PCM_CHUNK_SAMPLES);
                short[] chunk = Arrays.copyOfRange(pcm16, offset, end);
                recognizer.acceptWaveForm(chunk, chunk.length);
            }
            float[] embedding = VoskSpeakerVectorParser.parse(
                    recognizer.getFinalResult(), MINIMUM_SPEAKER_FRAMES);
            if (embedding.length != EMBEDDING_DIMENSION) {
                throw new IllegalStateException("Vosk returned an unexpected embedding dimension");
            }
            return VoiceprintMatcher.normalize(embedding);
        }
    }

    @Override
    public synchronized void close() {
        if (speakerModel != null) {
            speakerModel.close();
            speakerModel = null;
        }
        if (recognitionModel != null) {
            recognitionModel.close();
            recognitionModel = null;
        }
    }
}
