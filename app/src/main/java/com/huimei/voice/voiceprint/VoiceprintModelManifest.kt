package com.huimei.voice.voiceprint

import java.util.Arrays
import java.util.Collections

object VoiceprintModelManifest {
    const val SHERPA_MODEL_ASSET: String =
        "voiceprint/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
    const val VOSK_SPEAKER_ASSET_DIRECTORY: String = "voiceprint/vosk-model-spk-0.4"
    const val VOSK_RECOGNITION_ASSET_DIRECTORY: String = "model-cn"

    private val REQUIRED_ASSETS: MutableList<AssetSpec?> = Collections.unmodifiableList<AssetSpec?>(
        Arrays.asList<AssetSpec?>(
            AssetSpec(
                SHERPA_MODEL_ASSET,
                28281138L,
                "f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11"
            ),
            AssetSpec(
                VOSK_SPEAKER_ASSET_DIRECTORY + "/mfcc.conf",
                94L,
                "86f4df625b950b81b68864fdefabf26aa65079f84ea20f40e7f9e7ccbb9c456c"
            ),
            AssetSpec(
                VOSK_SPEAKER_ASSET_DIRECTORY + "/mean.vec",
                1199L,
                "3bc5dfa9479c3aaacf4f45c155e9bd4772785449112566fac23578c09e93bcd7"
            ),
            AssetSpec(
                VOSK_SPEAKER_ASSET_DIRECTORY + "/transform.mat",
                65551L,
                "fab14481fb27c41df0f1825afe18530e4f6d128dbb2c239ee86d8d7096e22c33"
            ),
            AssetSpec(
                VOSK_SPEAKER_ASSET_DIRECTORY + "/final.ext.raw",
                14929171L,
                "d30354474ac719b3cfce58186463302b988cf22ce3afee7237a2da862c4a91d1"
            )
        )
    )

    @JvmStatic
    fun requiredAssets(): MutableList<AssetSpec?> {
        return REQUIRED_ASSETS
    }

    class AssetSpec internal constructor(
        val assetPath: String?,
        val sizeBytes: Long,
        val sha256: String?
    )
}
