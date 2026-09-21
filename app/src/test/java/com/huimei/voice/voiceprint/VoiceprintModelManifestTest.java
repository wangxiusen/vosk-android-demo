package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VoiceprintModelManifestTest {

    @Test
    public void requiredAssetsMatchPinnedReleaseBytes() throws Exception {
        Map<String, ExpectedAsset> expected = new LinkedHashMap<>();
        expected.put(
                "voiceprint/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx",
                new ExpectedAsset(28_281_138L,
                        "f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11"));
        expected.put(
                "voiceprint/vosk-model-spk-0.4/mfcc.conf",
                new ExpectedAsset(94L,
                        "86f4df625b950b81b68864fdefabf26aa65079f84ea20f40e7f9e7ccbb9c456c"));
        expected.put(
                "voiceprint/vosk-model-spk-0.4/mean.vec",
                new ExpectedAsset(1_199L,
                        "3bc5dfa9479c3aaacf4f45c155e9bd4772785449112566fac23578c09e93bcd7"));
        expected.put(
                "voiceprint/vosk-model-spk-0.4/transform.mat",
                new ExpectedAsset(65_551L,
                        "fab14481fb27c41df0f1825afe18530e4f6d128dbb2c239ee86d8d7096e22c33"));
        expected.put(
                "voiceprint/vosk-model-spk-0.4/final.ext.raw",
                new ExpectedAsset(14_929_171L,
                        "d30354474ac719b3cfce58186463302b988cf22ce3afee7237a2da862c4a91d1"));

        List<VoiceprintModelManifest.AssetSpec> actual =
                VoiceprintModelManifest.requiredAssets();
        assertEquals(expected.size(), actual.size());

        File assetsRoot = findAssetsRoot();
        for (VoiceprintModelManifest.AssetSpec spec : actual) {
            ExpectedAsset pinned = expected.get(spec.getAssetPath());
            assertTrue("Unexpected asset " + spec.getAssetPath(), pinned != null);
            assertEquals(pinned.size, spec.getSizeBytes());
            assertEquals(pinned.sha256, spec.getSha256());

            File asset = new File(assetsRoot, spec.getAssetPath());
            assertTrue("Missing asset " + asset, asset.isFile());
            assertEquals(pinned.size, asset.length());
            assertEquals(pinned.sha256, sha256(asset));
        }
    }

    private static File findAssetsRoot() {
        File workingDirectory = new File(System.getProperty("user.dir"));
        File direct = new File(workingDirectory, "src/main/assets");
        if (direct.isDirectory()) {
            return direct;
        }
        return new File(workingDirectory, "app/src/main/assets");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder value = new StringBuilder();
        for (byte element : digest.digest()) {
            value.append(String.format("%02x", element & 0xff));
        }
        return value.toString();
    }

    private static final class ExpectedAsset {
        private final long size;
        private final String sha256;

        private ExpectedAsset(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
