package com.huimei.voice.face;

public final class FaceModelManifest {
    public static final Entry YUNET = new Entry(
            "face/face_detection_yunet_2023mar.onnx",
            "face_detection_yunet_2023mar.onnx",
            232_589L,
            "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4");
    public static final Entry SFACE = new Entry(
            "face/face_recognition_sface_2021dec_int8.onnx",
            "face_recognition_sface_2021dec_int8.onnx",
            9_896_933L,
            "2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a");

    private FaceModelManifest() {
    }

    public static final class Entry {
        private final String assetPath;
        private final String fileName;
        private final long sizeBytes;
        private final String sha256;

        private Entry(String assetPath, String fileName, long sizeBytes, String sha256) {
            this.assetPath = assetPath;
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }

        public String assetPath() {
            return assetPath;
        }

        public String fileName() {
            return fileName;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public String sha256() {
            return sha256;
        }
    }
}
