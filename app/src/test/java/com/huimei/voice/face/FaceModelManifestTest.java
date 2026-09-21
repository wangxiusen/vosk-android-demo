package com.huimei.voice.face;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FaceModelManifestTest {
    @Test
    public void pinsOfficialModelMetadata() {
        assertEquals("face/face_detection_yunet_2023mar.onnx",
                FaceModelManifest.YUNET.assetPath());
        assertEquals(232589L, FaceModelManifest.YUNET.sizeBytes());
        assertEquals("8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
                FaceModelManifest.YUNET.sha256());

        assertEquals("face/face_recognition_sface_2021dec_int8.onnx",
                FaceModelManifest.SFACE.assetPath());
        assertEquals(9896933L, FaceModelManifest.SFACE.sizeBytes());
        assertEquals("2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a",
                FaceModelManifest.SFACE.sha256());
    }
}
