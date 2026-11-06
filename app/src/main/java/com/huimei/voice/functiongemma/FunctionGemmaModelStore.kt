package com.huimei.voice.functiongemma

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class FunctionGemmaModelStore @Throws(IOException::class) constructor(
    root: File,
    private val entry: FunctionGemmaModelManifest.Entry
) {
    private val root = root.canonicalFile
    private val target: File
    private val stagingDirectory: File

    init {
        if (!isSafePathComponent(entry.version()) || !isSafePathComponent(entry.filename())) {
            throw IllegalArgumentException("Model path must stay inside store root")
        }
        target = requireInsideRoot(File(File(File(this.root, "models"), entry.version()), entry.filename()))
        stagingDirectory = requireInsideRoot(File(this.root, "staging"))
    }

    @Synchronized
    fun isInstalled(): Boolean = verifyFile(target)

    fun installedFile(): File = target

    @Synchronized
    @Throws(IOException::class)
    fun install(source: InputStream): File {
        if (!stagingDirectory.isDirectory && !stagingDirectory.mkdirs()) {
            throw IOException("Unable to create model staging directory")
        }
        val staged = File.createTempFile("functiongemma-", ".part", stagingDirectory)
        try {
            val copied = copyAndFingerprint(source, staged)
            requireVerified(copied.sizeBytes, copied.sha256)
            if (isInstalled()) return target
            replaceTarget(staged)
            if (!verifyFile(target)) throw IOException("Model verification failed after install")
            return target
        } finally {
            if (staged.exists() && !staged.delete()) staged.deleteOnExit()
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun delete(): Boolean {
        if (!target.exists()) return false
        if (!target.delete()) throw IOException("Unable to delete installed model")
        return true
    }

    @Throws(IOException::class)
    private fun copyAndFingerprint(source: InputStream, staged: File): Verification {
        val digest = newDigest()
        var copied = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        BufferedInputStream(source).use { input ->
            FileOutputStream(staged).use { fileOutput ->
                BufferedOutputStream(fileOutput, BUFFER_BYTES).use { output ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        copied += count
                        if (copied > entry.sizeBytes()) throw IOException("Model verification failed: unexpected size")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
        }
        return Verification(copied, toHex(digest.digest()))
    }

    @Throws(IOException::class)
    private fun replaceTarget(staged: File) {
        val parent = target.parentFile ?: throw IOException("Unable to create model directory")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Unable to create model directory")

        var backup: File? = null
        if (target.exists()) {
            backup = File(parent, entry.filename() + ".backup")
            if (backup.exists() && !backup.delete()) throw IOException("Unable to clear stale model backup")
            if (!target.renameTo(backup)) throw IOException("Unable to stage invalid installed model")
        }

        if (!staged.renameTo(target)) {
            backup?.renameTo(target)
            throw IOException("Unable to atomically install verified model")
        }
        if (backup != null && !backup.delete()) backup.deleteOnExit()
    }

    private fun verifyFile(file: File): Boolean {
        if (!file.isFile || file.length() != entry.sizeBytes()) return false
        val digest = try {
            val result = newDigest()
            val buffer = ByteArray(BUFFER_BYTES)
            BufferedInputStream(FileInputStream(file)).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    result.update(buffer, 0, count)
                }
            }
            result
        } catch (_: IOException) {
            return false
        }
        return entry.sha256() == toHex(digest.digest())
    }

    @Throws(IOException::class)
    private fun requireVerified(actualSize: Long, actualHash: String) {
        if (actualSize != entry.sizeBytes() || entry.sha256() != actualHash) {
            throw IOException("Model verification failed: size or SHA-256 mismatch")
        }
    }

    @Throws(IOException::class)
    private fun requireInsideRoot(file: File): File {
        val canonical = file.canonicalFile
        if (!canonical.path.startsWith(root.path + File.separator)) {
            throw IllegalArgumentException("Model path must stay inside store root")
        }
        return canonical
    }

    private data class Verification(val sizeBytes: Long, val sha256: String)

    companion object {
        private const val BUFFER_BYTES = 64 * 1024

        private fun isSafePathComponent(value: String): Boolean =
            value != "." && value != ".." && '/' !in value && '\\' !in value

        @Throws(IOException::class)
        private fun newDigest(): MessageDigest = try {
            MessageDigest.getInstance("SHA-256")
        } catch (error: NoSuchAlgorithmException) {
            throw IOException("SHA-256 is unavailable", error)
        }

        private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
            for (value in bytes) append(String.format("%02x", value))
        }
    }
}
