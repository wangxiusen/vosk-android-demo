package com.huimei.voice.tts

import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class LightweightTtsConfigFactory private constructor() {
    companion object {
        const val CHINESE_MODEL_DIRECTORY = "vits-icefall-zh-aishell3"
        const val ENGLISH_MODEL_DIRECTORY = "vits-piper-en_US-ljspeech-medium"
        const val ENGLISH_DATA_DIRECTORY = "$ENGLISH_MODEL_DIRECTORY/espeak-ng-data"

        @JvmStatic
        fun create(language: TtsLanguage, copiedEnglishDataDirectory: String?): OfflineTtsConfig {
            val model = OfflineTtsModelConfig()
            val config = OfflineTtsConfig()
            val vits = OfflineTtsVitsModelConfig()
            if (language == TtsLanguage.CHINESE) {
                vits.model = "$CHINESE_MODEL_DIRECTORY/model.onnx"
                vits.tokens = "$CHINESE_MODEL_DIRECTORY/tokens.txt"
                vits.lexicon = "$CHINESE_MODEL_DIRECTORY/lexicon.txt"
                config.ruleFsts = "$CHINESE_MODEL_DIRECTORY/phone.fst,$CHINESE_MODEL_DIRECTORY/date.fst,$CHINESE_MODEL_DIRECTORY/number.fst"
            } else {
                vits.model = "$ENGLISH_MODEL_DIRECTORY/en_US-ljspeech-medium.onnx"
                vits.tokens = "$ENGLISH_MODEL_DIRECTORY/tokens.txt"
                vits.dataDir = copiedEnglishDataDirectory ?: ""
            }
            model.vits = vits
            model.numThreads = 4
            model.debug = false
            model.provider = "cpu"
            config.model = model
            config.maxNumSentences = 1
            config.silenceScale = TtsPause.NATURAL.silenceScale()
            return config
        }
    }
}
