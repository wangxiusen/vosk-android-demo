package com.huimei.voice.face

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Float
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.ByteArray
import kotlin.FloatArray
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.String
import kotlin.byteArrayOf
import kotlin.collections.ArrayList
import kotlin.collections.MutableList

class FaceRepository(root: File) {
    private val root: File
    private val metadataFile: File

    init {
        requireNotNull(root) { "Face repository root is required" }
        this.root = root.getCanonicalFile()
        if (!this.root.exists() && !this.root.mkdirs()) {
            throw IOException("Cannot create face repository")
        }
        if (!this.root.isDirectory()) {
            throw IOException("Face repository root is not a directory")
        }
        metadataFile = safeChild(this.root, METADATA_FILE)
        if (!metadataFile.exists()) {
            writeProfiles(mutableListOf<FaceProfile>())
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun saveEnrollment(
        displayName: String,
        embedding: FloatArray,
        portraitJpeg: ByteArray
    ): FaceProfile {
        val checkedName: String = requireDisplayName(displayName)
        validateEmbedding(embedding)
        require(!(portraitJpeg == null || portraitJpeg.size == 0)) { "Face portrait is required" }

        val profiles = loadProfiles()
        val existingIndex: Int = findByDisplayName(profiles, checkedName)
        val existing = if (existingIndex >= 0) profiles.get(existingIndex) else null
        val profileId = if (existing == null) UUID.randomUUID().toString() else existing.id
        val createdAt =
            if (existing == null) System.currentTimeMillis() else existing.createdAtMillis
        val directory = safeChild(root, profileId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create face profile directory")
        }
        writeEmbedding(directory, embedding)
        writeBytesAtomically(directory, PORTRAIT_FILE, portraitJpeg)

        val profile = FaceProfile(
            profileId, checkedName, ENROLLMENT_SAMPLE_COUNT, createdAt
        )
        if (existingIndex >= 0) {
            profiles.set(existingIndex, profile)
        } else {
            profiles.add(profile)
        }
        writeProfiles(profiles)
        return profile
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun listProfiles(): MutableList<FaceProfile> {
        return ArrayList(loadProfiles())
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun loadEmbedding(profileId: String, expectedDimension: Int): FloatArray {
        require(!(expectedDimension <= 0 || expectedDimension > MAX_EMBEDDING_DIMENSION)) { "Expected face embedding dimension is invalid" }
        val profile: FaceProfile = requireProfile(loadProfiles(), profileId)
        val file = safeChild(safeChild(root, profile.id), EMBEDDING_FILE)
        try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                requireBytes(input, EMBEDDING_MAGIC)
                val version: Int = readIntLe(input)
                val dimension: Int = readIntLe(input)
                if (version != EMBEDDING_VERSION || dimension != expectedDimension) {
                    throw IOException("Stored face embedding metadata does not match")
                }
                if (dimension <= 0 || dimension > MAX_EMBEDDING_DIMENSION) {
                    throw IOException("Stored face embedding dimension is invalid")
                }
                val value = FloatArray(dimension)
                for (i in 0..<dimension) {
                    value[i] = Float.intBitsToFloat(readIntLe(input))
                    if (!Float.isFinite(value[i])) {
                        throw IOException("Stored face embedding contains a non-finite value")
                    }
                }
                if (input.read() != -1) {
                    throw IOException("Stored face embedding has unexpected trailing data")
                }
                return value
            }
        } catch (exception: EOFException) {
            throw IOException("Stored face embedding is truncated", exception)
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun loadPortrait(profileId: String): ByteArray {
        val profile: FaceProfile = requireProfile(loadProfiles(), profileId)
        val file = safeChild(safeChild(root, profile.id), PORTRAIT_FILE)
        BufferedInputStream(FileInputStream(file)).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while ((input.read(buffer).also { read = it }) != -1) {
                    output.write(buffer, 0, read)
                }
                val value: ByteArray = output.toByteArray()
                if (value.size == 0) {
                    throw IOException("Stored face portrait is empty")
                }
                return value
            }
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun deleteProfile(profileId: String) {
        val profiles = loadProfiles()
        val profile: FaceProfile = requireProfile(profiles, profileId)
        deleteTree(safeChild(root, profile.id))
        profiles.remove(profile)
        writeProfiles(profiles)
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun clearAll() {
        val children = root.listFiles()
        if (children == null) {
            throw IOException("Cannot list face repository")
        }
        for (child in children) {
            deleteTree(child)
        }
        writeProfiles(mutableListOf<FaceProfile>())
    }

    @kotlin.Throws(IOException::class)
    private fun writeEmbedding(directory: File?, embedding: FloatArray) {
        val target = safeChild(directory, EMBEDDING_FILE)
        val temporary = safeChild(directory, EMBEDDING_FILE + ".tmp")
        FileOutputStream(temporary).use { fileOutput ->
            BufferedOutputStream(fileOutput).use { output ->
                output.write(EMBEDDING_MAGIC)
                writeIntLe(output, EMBEDDING_VERSION)
                writeIntLe(output, embedding.size)
                for (value in embedding) {
                    writeIntLe(output, Float.floatToRawIntBits(value))
                }
                output.flush()
                fileOutput.getFD().sync()
            }
        }
        replaceFile(temporary, target)
    }

    @kotlin.Throws(IOException::class)
    private fun writeBytesAtomically(directory: File?, name: String, bytes: ByteArray?) {
        val target = safeChild(directory, name)
        val temporary = safeChild(directory, name + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.flush()
            output.getFD().sync()
        }
        replaceFile(temporary, target)
    }

    @kotlin.Throws(IOException::class)
    private fun loadProfiles(): MutableList<FaceProfile> {
        val json: String = readUtf8(metadataFile)
        try {
            val array = JSONObject(json).getJSONArray("profiles")
            val profiles: MutableList<FaceProfile> = ArrayList<FaceProfile>()
            for (i in 0..<array.length()) {
                val `object` = array.getJSONObject(i)
                val profile = FaceProfile(
                    `object`.getString("id"),
                    `object`.getString("displayName"),
                    `object`.getInt("sampleCount"),
                    `object`.getLong("createdAtMillis")
                )
                if (profile.sampleCount != ENROLLMENT_SAMPLE_COUNT) {
                    throw IOException("Face profile has an invalid sample count")
                }
                safeChild(root, profile.id)
                if (findById(profiles, profile.id) >= 0
                    || findByDisplayName(profiles, profile.displayName) >= 0
                ) {
                    throw IOException("Face profile metadata contains duplicates")
                }
                profiles.add(profile)
            }
            return profiles
        } catch (exception: JSONException) {
            throw IOException("Face profile metadata is malformed", exception)
        } catch (exception: IllegalArgumentException) {
            throw IOException("Face profile metadata is malformed", exception)
        }
    }

    @kotlin.Throws(IOException::class)
    private fun writeProfiles(profiles: MutableList<FaceProfile>) {
        val array = JSONArray()
        val `object` = JSONObject()
        try {
            for (profile in profiles) {
                val item = JSONObject()
                item.put("id", profile.id)
                item.put("displayName", profile.displayName)
                item.put("sampleCount", profile.sampleCount)
                item.put("createdAtMillis", profile.createdAtMillis)
                array.put(item)
            }
            `object`.put("profiles", array)
        } catch (exception: JSONException) {
            throw IOException("Cannot serialize face profiles", exception)
        }
        val temporary = safeChild(root, METADATA_TEMP_FILE)
        FileOutputStream(temporary).use { output ->
            output.write(`object`.toString().toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.getFD().sync()
        }
        replaceFile(temporary, metadataFile)
    }

    @kotlin.Throws(IOException::class)
    private fun replaceFile(temporary: File, target: File) {
        if (!temporary.renameTo(target)) {
            throw IOException("Cannot atomically replace " + target.getName())
        }
    }

    @kotlin.Throws(IOException::class)
    private fun safeChild(parent: File?, childName: String): File {
        val candidate = File(parent, childName).getCanonicalFile()
        val rootPath = root.getCanonicalPath()
        val candidatePath = candidate.getCanonicalPath()
        if (!candidatePath.startsWith(rootPath + File.separator)) {
            throw IOException("Face path escapes the repository root")
        }
        return candidate
    }

    @kotlin.Throws(IOException::class)
    private fun deleteTree(file: File) {
        val checked = file.getCanonicalFile()
        val rootPrefix = root.getCanonicalPath() + File.separator
        if (!checked.getCanonicalPath().startsWith(rootPrefix)) {
            throw IOException("Refusing to delete outside the face repository")
        }
        if (checked.isDirectory()) {
            val children = checked.listFiles()
            if (children == null) {
                throw IOException("Cannot list " + checked.getName())
            }
            for (child in children) {
                deleteTree(child)
            }
        }
        if (checked.exists() && !checked.delete()) {
            throw IOException("Cannot delete " + checked.getName())
        }
    }

    companion object {
        private const val METADATA_FILE = "profiles.json"
        private const val METADATA_TEMP_FILE = "profiles.json.tmp"
        private const val EMBEDDING_FILE = "embedding.f32"
        private const val PORTRAIT_FILE = "portrait.jpg"
        private val EMBEDDING_MAGIC =
            byteArrayOf('H'.code.toByte(), 'F'.code.toByte(), 'A'.code.toByte(), 'C'.code.toByte())
        private const val EMBEDDING_VERSION = 1
        private const val ENROLLMENT_SAMPLE_COUNT = 5
        private const val MAX_EMBEDDING_DIMENSION = 100000

        @kotlin.Throws(IOException::class)
        private fun requireProfile(
            profiles: MutableList<FaceProfile>,
            profileId: String
        ): FaceProfile {
            require(!(profileId == null || profileId.trim { it <= ' ' }
                .isEmpty())) { "Face profile ID is required" }
            val index: Int = findById(profiles, profileId)
            if (index < 0) {
                throw IOException("Face profile does not exist")
            }
            return profiles.get(index)
        }

        private fun findById(profiles: MutableList<FaceProfile>, profileId: String?): Int {
            for (i in profiles.indices) {
                if (profiles.get(i).id == profileId) {
                    return i
                }
            }
            return -1
        }

        private fun findByDisplayName(
            profiles: MutableList<FaceProfile>,
            displayName: String
        ): Int {
            val key: String = displayName.trim { it <= ' ' }.lowercase()
            for (i in profiles.indices) {
                if (profiles.get(i).displayName.trim { it <= ' ' }.lowercase() == key) {
                    return i
                }
            }
            return -1
        }

        private fun requireDisplayName(displayName: String): String {
            require(!(displayName == null || displayName.trim { it <= ' ' }
                .isEmpty())) { "Face profile display name is required" }
            return displayName.trim { it <= ' ' }
        }

        private fun validateEmbedding(embedding: FloatArray) {
            require(!(embedding == null || embedding.size == 0 || embedding.size > MAX_EMBEDDING_DIMENSION)) { "Face embedding is invalid" }
            for (value in embedding) {
                require(Float.isFinite(value)) { "Face embedding must be finite" }
            }
        }

        @kotlin.Throws(IOException::class)
        private fun readUtf8(file: File?): String {
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while ((input.read(buffer).also { read = it }) != -1) {
                        output.write(buffer, 0, read)
                    }
                    return String(output.toByteArray(), StandardCharsets.UTF_8)
                }
            }
        }

        @kotlin.Throws(IOException::class)
        private fun requireBytes(input: InputStream, expected: ByteArray) {
            for (value in expected) {
                if (input.read() != (value.toInt() and 0xff)) {
                    throw IOException("Stored face embedding has invalid magic")
                }
            }
        }

        @kotlin.Throws(IOException::class)
        private fun readIntLe(input: InputStream): Int {
            val b0 = input.read()
            val b1 = input.read()
            val b2 = input.read()
            val b3 = input.read()
            if ((b0 or b1 or b2 or b3) < 0) {
                throw EOFException()
            }
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        @kotlin.Throws(IOException::class)
        private fun writeIntLe(output: OutputStream, value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
            output.write((value ushr 16) and 0xff)
            output.write((value ushr 24) and 0xff)
        }
    }
}
