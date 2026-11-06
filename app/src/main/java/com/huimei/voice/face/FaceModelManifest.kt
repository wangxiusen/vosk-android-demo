package com.huimei.voice.face

object FaceModelManifest {
    @JvmField
    val YUNET: Entry = FaceModelManifest.Entry(
        "face/face_detection_yunet_2023mar.onnx",
        "face_detection_yunet_2023mar.onnx",
        232589L,
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4"
    )
    @JvmField
    val SFACE: Entry = FaceModelManifest.Entry(
        "face/face_recognition_sface_2021dec_int8.onnx",
        "face_recognition_sface_2021dec_int8.onnx",
        9896933L,
        "2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a"
    )

    class Entry internal constructor(
        private val assetPath: String?,
        private val fileName: String?,
        private val sizeBytes: Long,
        private val sha256: String?
    ) {
        fun assetPath(): String? {
            return assetPath
        }

        fun fileName(): String? {
            return fileName
        }

        fun sizeBytes(): Long {
            return sizeBytes
        }

        fun sha256(): String? {
            return sha256
        }
    }
}
