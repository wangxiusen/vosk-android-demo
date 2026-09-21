package com.huimei.voice.voiceprint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class VoiceprintRepository {
    private static final String METADATA_FILE = "profiles.json";
    private static final String METADATA_TEMP_FILE = "profiles.json.tmp";
    private static final byte[] EMBEDDING_MAGIC = new byte[]{'H', 'V', 'E', 'C'};
    private static final int EMBEDDING_VERSION = 1;
    private static final int ENROLLMENT_SAMPLE_COUNT = 3;
    private static final int MAX_EMBEDDING_DIMENSION = 100_000;

    private final File root;
    private final File metadataFile;

    public VoiceprintRepository(File root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Voiceprint repository root is required");
        }
        this.root = root.getCanonicalFile();
        if (!this.root.exists() && !this.root.mkdirs()) {
            throw new IOException("Cannot create voiceprint repository");
        }
        if (!this.root.isDirectory()) {
            throw new IOException("Voiceprint repository root is not a directory");
        }
        metadataFile = safeChild(this.root, METADATA_FILE);
        if (!metadataFile.exists()) {
            writeProfiles(Collections.emptyList());
        }
    }

    public synchronized VoiceprintProfile saveEnrollment(
            String displayName,
            List<short[]> samples,
            int sampleRate) throws IOException {
        String trimmedName = requireDisplayName(displayName);
        if (samples == null || samples.size() != ENROLLMENT_SAMPLE_COUNT) {
            throw new IllegalArgumentException("Exactly three enrollment samples are required");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        for (short[] sample : samples) {
            if (sample == null) {
                throw new IllegalArgumentException("Enrollment sample cannot be null");
            }
        }

        List<VoiceprintProfile> profiles = loadProfiles();
        int existingIndex = findByDisplayName(profiles, trimmedName);
        VoiceprintProfile existing = existingIndex >= 0 ? profiles.get(existingIndex) : null;
        String profileId = existing == null ? UUID.randomUUID().toString() : existing.getId();
        long createdAt = existing == null ? System.currentTimeMillis() : existing.getCreatedAtMillis();
        File profileDirectory = safeChild(root, profileId);
        if (profileDirectory.exists()) {
            deleteTree(profileDirectory);
        }
        if (!profileDirectory.mkdirs()) {
            throw new IOException("Cannot create voiceprint profile directory");
        }
        for (int i = 0; i < samples.size(); i++) {
            WavIO.writeMono16(
                    safeChild(profileDirectory, "sample-" + (i + 1) + ".wav"),
                    samples.get(i),
                    sampleRate);
        }

        VoiceprintProfile profile = new VoiceprintProfile(
                profileId, trimmedName, samples.size(), createdAt);
        if (existingIndex >= 0) {
            profiles.set(existingIndex, profile);
        } else {
            profiles.add(profile);
        }
        writeProfiles(profiles);
        return profile;
    }

    public synchronized List<VoiceprintProfile> listProfiles() throws IOException {
        return new ArrayList<>(loadProfiles());
    }

    public synchronized List<WavIO.Audio> loadEnrollment(String profileId) throws IOException {
        VoiceprintProfile profile = requireProfile(loadProfiles(), profileId);
        File profileDirectory = safeChild(root, profile.getId());
        List<WavIO.Audio> audio = new ArrayList<>();
        for (int i = 1; i <= profile.getSampleCount(); i++) {
            audio.add(WavIO.readMono16(safeChild(profileDirectory, "sample-" + i + ".wav")));
        }
        return audio;
    }

    public synchronized void saveEmbedding(
            String profileId,
            VoiceprintEngineType engineType,
            String modelId,
            float[] embedding) throws IOException {
        VoiceprintProfile profile = requireProfile(loadProfiles(), profileId);
        requireEngine(engineType);
        String checkedModelId = requireModelId(modelId);
        validateEmbedding(embedding);
        File profileDirectory = safeChild(root, profile.getId());
        File target = safeChild(profileDirectory, engineType.id() + ".f32");
        File temporary = safeChild(profileDirectory, engineType.id() + ".f32.tmp");
        try (FileOutputStream fileOutput = new FileOutputStream(temporary);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            output.write(EMBEDDING_MAGIC);
            writeIntLe(output, EMBEDDING_VERSION);
            writeString(output, engineType.id());
            writeString(output, checkedModelId);
            writeIntLe(output, embedding.length);
            for (float value : embedding) {
                writeIntLe(output, Float.floatToRawIntBits(value));
            }
            output.flush();
            fileOutput.getFD().sync();
        }
        replaceFile(temporary, target);
    }

    public synchronized Optional<float[]> loadEmbedding(
            String profileId,
            VoiceprintEngineType engineType,
            String modelId,
            int expectedDimension) throws IOException {
        VoiceprintProfile profile = requireProfile(loadProfiles(), profileId);
        requireEngine(engineType);
        String checkedModelId = requireModelId(modelId);
        if (expectedDimension <= 0 || expectedDimension > MAX_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("Expected embedding dimension is invalid");
        }
        File file = safeChild(safeChild(root, profile.getId()), engineType.id() + ".f32");
        if (!file.isFile()) {
            return Optional.empty();
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            requireBytes(input, EMBEDDING_MAGIC);
            int version = readIntLe(input);
            String storedEngineId = readString(input);
            String storedModelId = readString(input);
            int dimension = readIntLe(input);
            if (version != EMBEDDING_VERSION
                    || !engineType.id().equals(storedEngineId)
                    || !checkedModelId.equals(storedModelId)
                    || dimension != expectedDimension) {
                return Optional.empty();
            }
            if (dimension <= 0 || dimension > MAX_EMBEDDING_DIMENSION) {
                throw new IOException("Stored embedding dimension is invalid");
            }
            float[] embedding = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                embedding[i] = Float.intBitsToFloat(readIntLe(input));
                if (Float.isNaN(embedding[i]) || Float.isInfinite(embedding[i])) {
                    throw new IOException("Stored embedding contains a non-finite value");
                }
            }
            if (input.read() != -1) {
                throw new IOException("Stored embedding has unexpected trailing data");
            }
            return Optional.of(embedding);
        } catch (EOFException exception) {
            throw new IOException("Stored embedding is truncated", exception);
        }
    }

    public synchronized void deleteProfile(String profileId) throws IOException {
        List<VoiceprintProfile> profiles = loadProfiles();
        VoiceprintProfile profile = requireProfile(profiles, profileId);
        deleteTree(safeChild(root, profile.getId()));
        profiles.remove(profile);
        writeProfiles(profiles);
    }

    public synchronized void clearAll() throws IOException {
        File[] children = root.listFiles();
        if (children == null) {
            throw new IOException("Cannot list voiceprint repository");
        }
        for (File child : children) {
            deleteTree(child);
        }
        writeProfiles(Collections.emptyList());
    }

    private List<VoiceprintProfile> loadProfiles() throws IOException {
        String json = readUtf8(metadataFile);
        try {
            JSONArray array = new JSONObject(json).getJSONArray("profiles");
            List<VoiceprintProfile> profiles = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                VoiceprintProfile profile = new VoiceprintProfile(
                        object.getString("id"),
                        object.getString("displayName"),
                        object.getInt("sampleCount"),
                        object.getLong("createdAtMillis"));
                if (profile.getSampleCount() != ENROLLMENT_SAMPLE_COUNT) {
                    throw new IOException("Voiceprint profile has an invalid sample count");
                }
                safeChild(root, profile.getId());
                if (findById(profiles, profile.getId()) >= 0
                        || findByDisplayName(profiles, profile.getDisplayName()) >= 0) {
                    throw new IOException("Voiceprint profile metadata contains duplicates");
                }
                profiles.add(profile);
            }
            return profiles;
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException("Voiceprint profile metadata is malformed", exception);
        }
    }

    private void writeProfiles(List<VoiceprintProfile> profiles) throws IOException {
        JSONArray array = new JSONArray();
        JSONObject rootObject = new JSONObject();
        try {
            for (VoiceprintProfile profile : profiles) {
                JSONObject object = new JSONObject();
                object.put("id", profile.getId());
                object.put("displayName", profile.getDisplayName());
                object.put("sampleCount", profile.getSampleCount());
                object.put("createdAtMillis", profile.getCreatedAtMillis());
                array.put(object);
            }
            rootObject.put("profiles", array);
        } catch (JSONException exception) {
            throw new IOException("Cannot serialize voiceprint profile metadata", exception);
        }

        File temporary = safeChild(root, METADATA_TEMP_FILE);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(rootObject.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        replaceFile(temporary, metadataFile);
    }

    private static void replaceFile(File temporary, File target) throws IOException {
        if (!temporary.renameTo(target)) {
            throw new IOException("Cannot atomically replace " + target.getName());
        }
    }

    private File safeChild(File parent, String childName) throws IOException {
        File candidate = new File(parent, childName).getCanonicalFile();
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        if (!candidatePath.startsWith(rootPath + File.separator)) {
            throw new IOException("Voiceprint path escapes the repository root");
        }
        return candidate;
    }

    private void deleteTree(File file) throws IOException {
        File checked = file.getCanonicalFile();
        String rootPrefix = root.getCanonicalPath() + File.separator;
        if (!checked.getCanonicalPath().startsWith(rootPrefix)) {
            throw new IOException("Refusing to delete outside the voiceprint repository");
        }
        if (checked.isDirectory()) {
            File[] children = checked.listFiles();
            if (children == null) {
                throw new IOException("Cannot list " + checked.getName());
            }
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (checked.exists() && !checked.delete()) {
            throw new IOException("Cannot delete " + checked.getName());
        }
    }

    private static VoiceprintProfile requireProfile(
            List<VoiceprintProfile> profiles,
            String profileId) throws IOException {
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile ID is required");
        }
        int index = findById(profiles, profileId);
        if (index < 0) {
            throw new IOException("Voiceprint profile does not exist");
        }
        return profiles.get(index);
    }

    private static int findById(List<VoiceprintProfile> profiles, String profileId) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(profileId)) {
                return i;
            }
        }
        return -1;
    }

    private static int findByDisplayName(List<VoiceprintProfile> profiles, String displayName) {
        String key = displayName.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < profiles.size(); i++) {
            String candidate = profiles.get(i).getDisplayName().trim().toLowerCase(Locale.ROOT);
            if (candidate.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name is required");
        }
        return displayName.trim();
    }

    private static void requireEngine(VoiceprintEngineType engineType) {
        if (engineType == null) {
            throw new IllegalArgumentException("Voiceprint engine is required");
        }
    }

    private static String requireModelId(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalArgumentException("Model ID is required");
        }
        return modelId.trim();
    }

    private static void validateEmbedding(float[] embedding) {
        if (embedding == null
                || embedding.length == 0
                || embedding.length > MAX_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("Embedding dimension is invalid");
        }
        for (float value : embedding) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Embedding must contain finite values");
            }
        }
    }

    private static String readUtf8(File file) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeString(OutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeIntLe(output, bytes.length);
        output.write(bytes);
    }

    private static String readString(InputStream input) throws IOException {
        int length = readIntLe(input);
        if (length < 0 || length > 16_384) {
            throw new IOException("Stored embedding string is too large");
        }
        byte[] bytes = new byte[length];
        readFully(input, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireBytes(InputStream input, byte[] expected) throws IOException {
        byte[] actual = new byte[expected.length];
        readFully(input, actual);
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                throw new IOException("Stored embedding has an invalid header");
            }
        }
    }

    private static void readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int count = input.read(buffer, offset, buffer.length - offset);
            if (count < 0) {
                throw new EOFException();
            }
            offset += count;
        }
    }

    private static int readIntLe(InputStream input) throws IOException {
        int first = input.read();
        int second = input.read();
        int third = input.read();
        int fourth = input.read();
        if ((first | second | third | fourth) < 0) {
            throw new EOFException();
        }
        return first | (second << 8) | (third << 16) | (fourth << 24);
    }

    private static void writeIntLe(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }
}
