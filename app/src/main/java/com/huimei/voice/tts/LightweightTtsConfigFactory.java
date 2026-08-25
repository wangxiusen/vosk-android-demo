package com.huimei.voice.tts;

import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

public final class LightweightTtsConfigFactory {
    public static final String CHINESE_MODEL_DIRECTORY = "vits-icefall-zh-aishell3";
    public static final String ENGLISH_MODEL_DIRECTORY =
            "vits-piper-en_US-ljspeech-medium";
    public static final String ENGLISH_DATA_DIRECTORY =
            ENGLISH_MODEL_DIRECTORY + "/espeak-ng-data";
    private LightweightTtsConfigFactory() {
    }

    public static OfflineTtsConfig create(
            TtsLanguage language,
            String copiedEnglishDataDirectory) {
        OfflineTtsModelConfig model = new OfflineTtsModelConfig();
        OfflineTtsConfig config = new OfflineTtsConfig();

        if (language == TtsLanguage.CHINESE) {
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel(CHINESE_MODEL_DIRECTORY + "/model.onnx");
            vits.setTokens(CHINESE_MODEL_DIRECTORY + "/tokens.txt");
            vits.setLexicon(CHINESE_MODEL_DIRECTORY + "/lexicon.txt");
            model.setVits(vits);
            config.setRuleFsts(
                    CHINESE_MODEL_DIRECTORY + "/phone.fst,"
                            + CHINESE_MODEL_DIRECTORY + "/date.fst,"
                            + CHINESE_MODEL_DIRECTORY + "/number.fst");
        } else {
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel(ENGLISH_MODEL_DIRECTORY + "/en_US-ljspeech-medium.onnx");
            vits.setTokens(ENGLISH_MODEL_DIRECTORY + "/tokens.txt");
            vits.setDataDir(copiedEnglishDataDirectory);
            model.setVits(vits);
        }

        model.setNumThreads(4);
        model.setDebug(false);
        model.setProvider("cpu");

        config.setModel(model);
        config.setMaxNumSentences(1);
        config.setSilenceScale(0.2f);
        return config;
    }
}
