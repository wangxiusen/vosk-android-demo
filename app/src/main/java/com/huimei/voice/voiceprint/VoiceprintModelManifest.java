package com.huimei.voice.voiceprint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VoiceprintModelManifest {
    public static final String SHERPA_MODEL_ASSET =
            "voiceprint/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx";
    public static final String VOSK_SPEAKER_ASSET_DIRECTORY =
            "voiceprint/vosk-model-spk-0.4";
    public static final String VOSK_RECOGNITION_ASSET_DIRECTORY = "model-cn";

    private static final List<AssetSpec> REQUIRED_ASSETS = Collections.unmodifiableList(
            Arrays.asList(
                    new AssetSpec(
                            SHERPA_MODEL_ASSET,
                            28_281_138L,
                            "f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11"),
                    new AssetSpec(
                            VOSK_SPEAKER_ASSET_DIRECTORY + "/mfcc.conf",
                            94L,
                            "86f4df625b950b81b68864fdefabf26aa65079f84ea20f40e7f9e7ccbb9c456c"),
                    new AssetSpec(
                            VOSK_SPEAKER_ASSET_DIRECTORY + "/mean.vec",
                            1_199L,
                            "3bc5dfa9479c3aaacf4f45c155e9bd4772785449112566fac23578c09e93bcd7"),
                    new AssetSpec(
                            VOSK_SPEAKER_ASSET_DIRECTORY + "/transform.mat",
                            65_551L,
                            "fab14481fb27c41df0f1825afe18530e4f6d128dbb2c239ee86d8d7096e22c33"),
                    new AssetSpec(
                            VOSK_SPEAKER_ASSET_DIRECTORY + "/final.ext.raw",
                            14_929_171L,
                            "d30354474ac719b3cfce58186463302b988cf22ce3afee7237a2da862c4a91d1")));

    private VoiceprintModelManifest() {
    }

    public static List<AssetSpec> requiredAssets() {
        return REQUIRED_ASSETS;
    }

    public static final class AssetSpec {
        private final String assetPath;
        private final long sizeBytes;
        private final String sha256;

        private AssetSpec(String assetPath, long sizeBytes, String sha256) {
            this.assetPath = assetPath;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }

        public String getAssetPath() {
            return assetPath;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getSha256() {
            return sha256;
        }
    }
}
