package com.huimei.voice.voiceprint

import android.content.Context
import android.content.res.AssetManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class VoiceprintModelInstaller @Throws(IOException::class) constructor(context: Context?) {
    private val assets: AssetManager
    private val modelRoot: File

    init {
        val context = requireNotNull(context) { "Context is required" }
        val applicationContext = context.getApplicationContext()
        val safeContext = if (applicationContext == null) context else applicationContext
        assets = safeContext.getAssets()
        modelRoot = File(safeContext.getFilesDir(), "voiceprint-models").getCanonicalFile()
        if (!modelRoot.exists() && !modelRoot.mkdirs()) {
            throw IOException("Cannot create voiceprint model directory")
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun installVoskSpeakerModel(): File {
        return installDirectory(
            VoiceprintModelManifest.VOSK_SPEAKER_ASSET_DIRECTORY,
            SPEAKER_DIRECTORY_NAME,
            SPEAKER_VERSION
        )
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun installChineseRecognitionModel(): File {
        return installDirectory(
            VoiceprintModelManifest.VOSK_RECOGNITION_ASSET_DIRECTORY,
            RECOGNITION_DIRECTORY_NAME,
            RECOGNITION_VERSION
        )
    }

    @kotlin.Throws(IOException::class)
    private fun installDirectory(
        assetDirectory: String,
        targetName: String,
        version: String
    ): File {
        val target = safeChild(modelRoot, targetName)
        val marker = safeChild(target, COMPLETION_MARKER)
        if (target.isDirectory() && version == readMarker(marker)) {
            return target
        }

        val staging = safeChild(modelRoot, targetName + ".installing")
        deleteTreeIfPresent(staging)
        if (!staging.mkdirs()) {
            throw IOException("Cannot create model staging directory")
        }
        try {
            copyAssetTree(assetDirectory, staging)
            writeMarker(safeChild(staging, COMPLETION_MARKER), version)
            deleteTreeIfPresent(target)
            if (!staging.renameTo(target)) {
                throw IOException("Cannot activate installed voiceprint model")
            }
            return target
        } catch (exception: IOException) {
            deleteTreeIfPresent(staging)
            throw exception
        }
    }

    @kotlin.Throws(IOException::class)
    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = assets.list(assetPath)
        if (children == null) {
            throw IOException("Cannot list model asset " + assetPath)
        }
        if (children.size == 0) {
            copyAssetFile(assetPath, destination)
            return
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create model directory " + destination.getName())
        }
        for (child in children) {
            copyAssetTree(assetPath + "/" + child, safeChild(destination, child))
        }
    }

    @kotlin.Throws(IOException::class)
    private fun copyAssetFile(assetPath: String, destination: File) {
        val parent = destination.getParentFile()
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw IOException("Cannot create model file directory")
        }
        BufferedInputStream(assets.open(assetPath)).use { input ->
            FileOutputStream(destination).use { fileOutput ->
                BufferedOutputStream(fileOutput).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var count: Int
                    while ((input.read(buffer).also { count = it }) >= 0) {
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    fileOutput.getFD().sync()
                }
            }
        }
    }

    @kotlin.Throws(IOException::class)
    private fun safeChild(parent: File?, name: String): File {
        val candidate = File(parent, name).getCanonicalFile()
        val rootPath = modelRoot.getCanonicalPath() + File.separator
        if (!candidate.getCanonicalPath().startsWith(rootPath)) {
            throw IOException("Model path escapes private storage")
        }
        return candidate
    }

    @kotlin.Throws(IOException::class)
    private fun deleteTreeIfPresent(file: File) {
        if (!file.exists()) {
            return
        }
        val checked = file.getCanonicalFile()
        if (!checked.getCanonicalPath().startsWith(modelRoot.getCanonicalPath() + File.separator)) {
            throw IOException("Refusing to delete outside private model storage")
        }
        if (checked.isDirectory()) {
            val children = checked.listFiles()
            if (children == null) {
                throw IOException("Cannot list model directory")
            }
            for (child in children) {
                deleteTreeIfPresent(child)
            }
        }
        if (!checked.delete()) {
            throw IOException("Cannot remove incomplete model data")
        }
    }

    companion object {
        private const val SPEAKER_DIRECTORY_NAME = "vosk-model-spk-0.4"
        private const val RECOGNITION_DIRECTORY_NAME = "model-cn"
        private const val COMPLETION_MARKER = ".complete"
        private const val SPEAKER_VERSION =
            "vosk-model-spk-0.4:a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7"
        private const val RECOGNITION_VERSION = "vosk-model-small-cn-0.22"

        @kotlin.Throws(IOException::class)
        private fun writeMarker(marker: File?, value: String) {
            FileOutputStream(marker).use { output ->
                output.write(value.toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.getFD().sync()
            }
        }

        @kotlin.Throws(IOException::class)
        private fun readMarker(marker: File): String {
            if (!marker.isFile()) {
                return ""
            }
            BufferedInputStream(FileInputStream(marker)).use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(256)
                    var count: Int
                    while ((input.read(buffer).also { count = it }) >= 0) {
                        output.write(buffer, 0, count)
                    }
                    return String(output.toByteArray(), StandardCharsets.UTF_8)
                }
            }
        }
    }
}
