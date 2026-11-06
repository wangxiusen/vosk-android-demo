package com.huimei.voice.voiceprint

import com.huimei.voice.voiceprint.WavIO.readMono16
import com.huimei.voice.voiceprint.WavIO.writeMono16
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
import java.util.Optional
import java.util.UUID
import kotlin.ByteArray
import kotlin.FloatArray
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.ShortArray
import kotlin.String
import kotlin.byteArrayOf
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.plus

class VoiceprintRepository(root: File) {
    private val root: File
    private val metadataFile: File

    init {
        requireNotNull(root) { "Voiceprint repository root is required" }
        this.root = root.getCanonicalFile()
        if (!this.root.exists() && !this.root.mkdirs()) {
            throw IOException("Cannot create voiceprint repository")
        }
        if (!this.root.isDirectory()) {
            throw IOException("Voiceprint repository root is not a directory")
        }
        metadataFile = safeChild(this.root, METADATA_FILE)
        if (!metadataFile.exists()) {
            writeProfiles(mutableListOf<VoiceprintProfile>())
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun saveEnrollment(
        displayName: String,
        samples: MutableList<ShortArray>,
        sampleRate: Int
    ): VoiceprintProfile {
        val trimmedName: String = requireDisplayName(displayName)
        require(!(samples == null || samples.size != ENROLLMENT_SAMPLE_COUNT)) { "Exactly three enrollment samples are required" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        for (sample in samples) {
            requireNotNull(sample) { "Enrollment sample cannot be null" }
        }

        val profiles = loadProfiles()
        val existingIndex: Int = findByDisplayName(profiles, trimmedName)
        val existing = if (existingIndex >= 0) profiles.get(existingIndex) else null
        val profileId = if (existing == null) UUID.randomUUID().toString() else existing.id
        val createdAt =
            if (existing == null) System.currentTimeMillis() else existing.createdAtMillis
        val profileDirectory = safeChild(root, profileId)
        if (profileDirectory.exists()) {
            deleteTree(profileDirectory)
        }
        if (!profileDirectory.mkdirs()) {
            throw IOException("Cannot create voiceprint profile directory")
        }
        for (i in samples.indices) {
            writeMono16(
                safeChild(profileDirectory, "sample-" + (i + 1) + ".wav"),
                samples.get(i),
                sampleRate
            )
        }

        val profile = VoiceprintProfile(
            profileId, trimmedName, samples.size, createdAt
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
    fun listProfiles(): MutableList<VoiceprintProfile> {
        return ArrayList(loadProfiles())
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun loadEnrollment(profileId: String): MutableList<WavIO.Audio> {
        val profile: VoiceprintProfile = requireProfile(loadProfiles(), profileId)
        val profileDirectory = safeChild(root, profile.id)
        val audio: MutableList<WavIO.Audio> = ArrayList()
        for (i in 1..profile.sampleCount) {
            audio.add(readMono16(safeChild(profileDirectory, "sample-" + i + ".wav")))
        }
        return audio
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun saveEmbedding(
        profileId: String,
        engineType: VoiceprintEngineType,
        modelId: String,
        embedding: FloatArray
    ) {
        val profile: VoiceprintProfile = requireProfile(loadProfiles(), profileId)
        requireEngine(engineType)
        val checkedModelId: String = requireModelId(modelId)
        validateEmbedding(embedding)
        val profileDirectory = safeChild(root, profile.id)
        val target = safeChild(profileDirectory, engineType.id() + ".f32")
        val temporary = safeChild(profileDirectory, engineType.id() + ".f32.tmp")
        FileOutputStream(temporary).use { fileOutput ->
            BufferedOutputStream(fileOutput).use { output ->
                output.write(EMBEDDING_MAGIC)
                writeIntLe(output, EMBEDDING_VERSION)
                Companion.writeString(output, engineType.id()!!)
                writeString(output, checkedModelId)
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

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun loadEmbedding(
        profileId: String,
        engineType: VoiceprintEngineType,
        modelId: String,
        expectedDimension: Int
    ): Optional<FloatArray> {
        val profile: VoiceprintProfile = requireProfile(loadProfiles(), profileId)
        requireEngine(engineType)
        val checkedModelId: String = requireModelId(modelId)
        require(!(expectedDimension <= 0 || expectedDimension > MAX_EMBEDDING_DIMENSION)) { "Expected embedding dimension is invalid" }
        val file = safeChild(safeChild(root, profile.id), engineType.id() + ".f32")
        if (!file.isFile()) {
            return Optional.empty()
        }
        try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                requireBytes(input, EMBEDDING_MAGIC)
                val version: Int = readIntLe(input)
                val storedEngineId: String = readString(input)
                val storedModelId: String = readString(input)
                val dimension: Int = readIntLe(input)
                if (version != EMBEDDING_VERSION || (engineType.id() != storedEngineId) || (checkedModelId != storedModelId) || dimension != expectedDimension) {
                    return Optional.empty()
                }
                if (dimension <= 0 || dimension > MAX_EMBEDDING_DIMENSION) {
                    throw IOException("Stored embedding dimension is invalid")
                }
                val embedding = FloatArray(dimension)
                for (i in 0..<dimension) {
                    embedding[i] = Float.intBitsToFloat(readIntLe(input))
                    if (Float.isNaN(embedding[i]) || Float.isInfinite(embedding[i])) {
                        throw IOException("Stored embedding contains a non-finite value")
                    }
                }
                if (input.read() != -1) {
                    throw IOException("Stored embedding has unexpected trailing data")
                }
                return Optional.of(embedding)
            }
        } catch (exception: EOFException) {
            throw IOException("Stored embedding is truncated", exception)
        }
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun deleteProfile(profileId: String) {
        val profiles = loadProfiles()
        val profile: VoiceprintProfile = requireProfile(profiles, profileId)
        deleteTree(safeChild(root, profile.id))
        profiles.remove(profile)
        writeProfiles(profiles)
    }

    @kotlin.jvm.Synchronized
    @kotlin.Throws(IOException::class)
    fun clearAll() {
        val children = root.listFiles()
        if (children == null) {
            throw IOException("Cannot list voiceprint repository")
        }
        for (child in children) {
            deleteTree(child)
        }
        writeProfiles(mutableListOf<VoiceprintProfile>())
    }

    @kotlin.Throws(IOException::class)
    private fun loadProfiles(): MutableList<VoiceprintProfile> {
        val json: String = readUtf8(metadataFile)
        try {
            val array = JSONObject(json).getJSONArray("profiles")
            val profiles: MutableList<VoiceprintProfile> = ArrayList<VoiceprintProfile>()
            for (i in 0..<array.length()) {
                val `object` = array.getJSONObject(i)
                val profile = VoiceprintProfile(
                    `object`.getString("id"),
                    `object`.getString("displayName"),
                    `object`.getInt("sampleCount"),
                    `object`.getLong("createdAtMillis")
                )
                if (profile.sampleCount != ENROLLMENT_SAMPLE_COUNT) {
                    throw IOException("Voiceprint profile has an invalid sample count")
                }
                safeChild(root, profile.id)
                if (findById(profiles, profile.id) >= 0
                    || findByDisplayName(profiles, profile.displayName) >= 0
                ) {
                    throw IOException("Voiceprint profile metadata contains duplicates")
                }
                profiles.add(profile)
            }
            return profiles
        } catch (exception: JSONException) {
            throw IOException("Voiceprint profile metadata is malformed", exception)
        } catch (exception: IllegalArgumentException) {
            throw IOException("Voiceprint profile metadata is malformed", exception)
        }
    }

    @kotlin.Throws(IOException::class)
    private fun writeProfiles(profiles: MutableList<VoiceprintProfile>) {
        val array = JSONArray()
        val rootObject = JSONObject()
        try {
            for (profile in profiles) {
                val `object` = JSONObject()
                `object`.put("id", profile.id)
                `object`.put("displayName", profile.displayName)
                `object`.put("sampleCount", profile.sampleCount)
                `object`.put("createdAtMillis", profile.createdAtMillis)
                array.put(`object`)
            }
            rootObject.put("profiles", array)
        } catch (exception: JSONException) {
            throw IOException("Cannot serialize voiceprint profile metadata", exception)
        }

        val temporary = safeChild(root, METADATA_TEMP_FILE)
        FileOutputStream(temporary).use { output ->
            output.write(rootObject.toString().toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.getFD().sync()
        }
        replaceFile(temporary, metadataFile)
    }

    @kotlin.Throws(IOException::class)
    private fun safeChild(parent: File?, childName: String): File {
        val candidate = File(parent, childName).getCanonicalFile()
        val rootPath = root.getCanonicalPath()
        val candidatePath = candidate.getCanonicalPath()
        if (!candidatePath.startsWith(rootPath + File.separator)) {
            throw IOException("Voiceprint path escapes the repository root")
        }
        return candidate
    }

    @kotlin.Throws(IOException::class)
    private fun deleteTree(file: File) {
        val checked = file.getCanonicalFile()
        val rootPrefix = root.getCanonicalPath() + File.separator
        if (!checked.getCanonicalPath().startsWith(rootPrefix)) {
            throw IOException("Refusing to delete outside the voiceprint repository")
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
        private val EMBEDDING_MAGIC =
            byteArrayOf('H'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(), 'C'.code.toByte())
        private const val EMBEDDING_VERSION = 1
        private const val ENROLLMENT_SAMPLE_COUNT = 3
        private const val MAX_EMBEDDING_DIMENSION = 100000

        @kotlin.Throws(IOException::class)
        private fun replaceFile(temporary: File, target: File) {
            if (!temporary.renameTo(target)) {
                throw IOException("Cannot atomically replace " + target.getName())
            }
        }

        @kotlin.Throws(IOException::class)
        private fun requireProfile(
            profiles: MutableList<VoiceprintProfile>,
            profileId: String
        ): VoiceprintProfile {
            require(!(profileId == null || profileId.trim { it <= ' ' }
                .isEmpty())) { "Profile ID is required" }
            val index: Int = findById(profiles, profileId)
            if (index < 0) {
                throw IOException("Voiceprint profile does not exist")
            }
            return profiles.get(index)
        }

        private fun findById(profiles: MutableList<VoiceprintProfile>, profileId: String?): Int {
            for (i in profiles.indices) {
                if (profiles.get(i).id == profileId) {
                    return i
                }
            }
            return -1
        }

        private fun findByDisplayName(
            profiles: MutableList<VoiceprintProfile>,
            displayName: String
        ): Int {
            val key: String = displayName.trim { it <= ' ' }.lowercase()
            for (i in profiles.indices) {
                val candidate: String = profiles.get(i).displayName.trim { it <= ' ' }.lowercase()
                if (candidate == key) {
                    return i
                }
            }
            return -1
        }

        private fun requireDisplayName(displayName: String): String {
            require(!(displayName == null || displayName.trim { it <= ' ' }
                .isEmpty())) { "Display name is required" }
            return displayName.trim { it <= ' ' }
        }

        private fun requireEngine(engineType: VoiceprintEngineType) {
            requireNotNull(engineType) { "Voiceprint engine is required" }
        }

        private fun requireModelId(modelId: String): String {
            require(!(modelId == null || modelId.trim { it <= ' ' }
                .isEmpty())) { "Model ID is required" }
            return modelId.trim { it <= ' ' }
        }

        private fun validateEmbedding(embedding: FloatArray) {
            require(!(embedding == null || embedding.size == 0 || embedding.size > MAX_EMBEDDING_DIMENSION)) { "Embedding dimension is invalid" }
            for (value in embedding) {
                require(!(Float.isNaN(value) || Float.isInfinite(value))) { "Embedding must contain finite values" }
            }
        }

        @kotlin.Throws(IOException::class)
        private fun readUtf8(file: File?): String {
            BufferedInputStream(FileInputStream(file)).use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(4096)
                    var count: Int
                    while ((input.read(buffer).also { count = it }) >= 0) {
                        output.write(buffer, 0, count)
                    }
                    return String(output.toByteArray(), StandardCharsets.UTF_8)
                }
            }
        }

        @kotlin.Throws(IOException::class)
        private fun writeString(output: OutputStream, value: String) {
            val bytes: ByteArray = value.toByteArray(StandardCharsets.UTF_8)
            writeIntLe(output, bytes.size)
            output.write(bytes)
        }

        @kotlin.Throws(IOException::class)
        private fun readString(input: InputStream): String {
            val length: Int = readIntLe(input)
            if (length < 0 || length > 16384) {
                throw IOException("Stored embedding string is too large")
            }
            val bytes = ByteArray(length)
            readFully(input, bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        @kotlin.Throws(IOException::class)
        private fun requireBytes(input: InputStream, expected: ByteArray) {
            val actual = ByteArray(expected.size)
            readFully(input, actual)
            for (i in expected.indices) {
                if (actual[i] != expected[i]) {
                    throw IOException("Stored embedding has an invalid header")
                }
            }
        }

        @kotlin.Throws(IOException::class)
        private fun readFully(input: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) {
                    throw EOFException()
                }
                offset += count
            }
        }

        @kotlin.Throws(IOException::class)
        private fun readIntLe(input: InputStream): Int {
            val first = input.read()
            val second = input.read()
            val third = input.read()
            val fourth = input.read()
            if ((first or second or third or fourth) < 0) {
                throw EOFException()
            }
            return first or (second shl 8) or (third shl 16) or (fourth shl 24)
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
