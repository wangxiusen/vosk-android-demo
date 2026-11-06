package com.huimei.voice.face

import android.content.Context
import android.content.res.AssetManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class FaceModelInstaller(context: Context) {
    private val assets: AssetManager
    private val modelDirectory: File

    init {
        requireNotNull(context) { "Context is required" }
        assets = context.getApplicationContext().getAssets()
        modelDirectory = File(context.getFilesDir(), "face-models").getCanonicalFile()
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun install(): ModelFiles {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw IOException("Cannot create face model directory")
        }
        if (!modelDirectory.isDirectory()) {
            throw IOException("Face model path is not a directory")
        }
        val detector = install(FaceModelManifest.YUNET)
        val recognizer = install(FaceModelManifest.SFACE)
        return ModelFiles(detector, recognizer)
    }

    @kotlin.Throws(IOException::class)
    private fun install(entry: FaceModelManifest.Entry): File {
        val target = safeChild(entry.fileName()!!)
        if (matches(target, entry)) {
            return target
        }
        val temporary = safeChild(entry.fileName() + ".tmp")
        if (temporary.exists() && !temporary.delete()) {
            throw IOException("Cannot replace temporary face model")
        }
        BufferedInputStream(assets.open(entry.assetPath()!!)).use { input ->
            FileOutputStream(temporary).use { fileOutput ->
                BufferedOutputStream(fileOutput).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while ((input.read(buffer).also { read = it }) != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    fileOutput.getFD().sync()
                }
            }
        }
        if (!matches(temporary, entry)) {
            if (!temporary.delete()) {
                temporary.deleteOnExit()
            }
            throw IOException("Face model checksum mismatch: " + entry.fileName())
        }
        if (!temporary.renameTo(target)) {
            throw IOException("Cannot install face model: " + entry.fileName())
        }
        return target
    }

    @kotlin.Throws(IOException::class)
    private fun safeChild(name: String): File {
        val candidate = File(modelDirectory, name).getCanonicalFile()
        if (!candidate.getCanonicalPath().startsWith(
                modelDirectory.getCanonicalPath() + File.separator
            )
        ) {
            throw IOException("Face model path escapes model directory")
        }
        return candidate
    }

    class ModelFiles internal constructor(
        private val detector: File?,
        private val recognizer: File?
    ) {
        fun detector(): File? {
            return detector
        }

        fun recognizer(): File? {
            return recognizer
        }
    }

    companion object {
        @kotlin.Throws(IOException::class)
        private fun matches(file: File, entry: FaceModelManifest.Entry): Boolean {
            return file.isFile()
                    && file.length() == entry.sizeBytes() && entry.sha256() == sha256(file)
        }

        @kotlin.Throws(IOException::class)
        private fun sha256(file: File?): String {
            val digest: MessageDigest?
            try {
                digest = MessageDigest.getInstance("SHA-256")
            } catch (exception: NoSuchAlgorithmException) {
                throw IOException("SHA-256 is unavailable", exception)
            }
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while ((input.read(buffer).also { read = it }) != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val value = StringBuilder(64)
            for (item in digest.digest()) {
                value.append(String.format("%02x", item.toInt() and 0xff))
            }
            return value.toString()
        }
    }
}
