package com.huimei.voice.face;

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
import java.util.UUID;

public final class FaceRepository {
    private static final String METADATA_FILE = "profiles.json";
    private static final String METADATA_TEMP_FILE = "profiles.json.tmp";
    private static final String EMBEDDING_FILE = "embedding.f32";
    private static final String PORTRAIT_FILE = "portrait.jpg";
    private static final byte[] EMBEDDING_MAGIC = new byte[]{'H', 'F', 'A', 'C'};
    private static final int EMBEDDING_VERSION = 1;
    private static final int ENROLLMENT_SAMPLE_COUNT = 5;
    private static final int MAX_EMBEDDING_DIMENSION = 100_000;

    private final File root;
    private final File metadataFile;

    public FaceRepository(File root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Face repository root is required");
        }
        this.root = root.getCanonicalFile();
        if (!this.root.exists() && !this.root.mkdirs()) {
            throw new IOException("Cannot create face repository");
        }
        if (!this.root.isDirectory()) {
            throw new IOException("Face repository root is not a directory");
        }
        metadataFile = safeChild(this.root, METADATA_FILE);
        if (!metadataFile.exists()) {
            writeProfiles(Collections.emptyList());
        }
    }

    public synchronized FaceProfile saveEnrollment(
            String displayName,
            float[] embedding,
            byte[] portraitJpeg) throws IOException {
        String checkedName = requireDisplayName(displayName);
        validateEmbedding(embedding);
        if (portraitJpeg == null || portraitJpeg.length == 0) {
            throw new IllegalArgumentException("Face portrait is required");
        }

        List<FaceProfile> profiles = loadProfiles();
        int existingIndex = findByDisplayName(profiles, checkedName);
        FaceProfile existing = existingIndex >= 0 ? profiles.get(existingIndex) : null;
        String profileId = existing == null ? UUID.randomUUID().toString() : existing.getId();
        long createdAt = existing == null ? System.currentTimeMillis() : existing.getCreatedAtMillis();
        File directory = safeChild(root, profileId);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Cannot create face profile directory");
        }
        writeEmbedding(directory, embedding);
        writeBytesAtomically(directory, PORTRAIT_FILE, portraitJpeg);

        FaceProfile profile = new FaceProfile(
                profileId, checkedName, ENROLLMENT_SAMPLE_COUNT, createdAt);
        if (existingIndex >= 0) {
            profiles.set(existingIndex, profile);
        } else {
            profiles.add(profile);
        }
        writeProfiles(profiles);
        return profile;
    }

    public synchronized List<FaceProfile> listProfiles() throws IOException {
        return new ArrayList<>(loadProfiles());
    }

    public synchronized float[] loadEmbedding(String profileId, int expectedDimension)
            throws IOException {
        if (expectedDimension <= 0 || expectedDimension > MAX_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("Expected face embedding dimension is invalid");
        }
        FaceProfile profile = requireProfile(loadProfiles(), profileId);
        File file = safeChild(safeChild(root, profile.getId()), EMBEDDING_FILE);
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            requireBytes(input, EMBEDDING_MAGIC);
            int version = readIntLe(input);
            int dimension = readIntLe(input);
            if (version != EMBEDDING_VERSION || dimension != expectedDimension) {
                throw new IOException("Stored face embedding metadata does not match");
            }
            if (dimension <= 0 || dimension > MAX_EMBEDDING_DIMENSION) {
                throw new IOException("Stored face embedding dimension is invalid");
            }
            float[] value = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                value[i] = Float.intBitsToFloat(readIntLe(input));
                if (!Float.isFinite(value[i])) {
                    throw new IOException("Stored face embedding contains a non-finite value");
                }
            }
            if (input.read() != -1) {
                throw new IOException("Stored face embedding has unexpected trailing data");
            }
            return value;
        } catch (EOFException exception) {
            throw new IOException("Stored face embedding is truncated", exception);
        }
    }

    public synchronized byte[] loadPortrait(String profileId) throws IOException {
        FaceProfile profile = requireProfile(loadProfiles(), profileId);
        File file = safeChild(safeChild(root, profile.getId()), PORTRAIT_FILE);
        try (InputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            byte[] value = output.toByteArray();
            if (value.length == 0) {
                throw new IOException("Stored face portrait is empty");
            }
            return value;
        }
    }

    public synchronized void deleteProfile(String profileId) throws IOException {
        List<FaceProfile> profiles = loadProfiles();
        FaceProfile profile = requireProfile(profiles, profileId);
        deleteTree(safeChild(root, profile.getId()));
        profiles.remove(profile);
        writeProfiles(profiles);
    }

    public synchronized void clearAll() throws IOException {
        File[] children = root.listFiles();
        if (children == null) {
            throw new IOException("Cannot list face repository");
        }
        for (File child : children) {
            deleteTree(child);
        }
        writeProfiles(Collections.emptyList());
    }

    private void writeEmbedding(File directory, float[] embedding) throws IOException {
        File target = safeChild(directory, EMBEDDING_FILE);
        File temporary = safeChild(directory, EMBEDDING_FILE + ".tmp");
        try (FileOutputStream fileOutput = new FileOutputStream(temporary);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            output.write(EMBEDDING_MAGIC);
            writeIntLe(output, EMBEDDING_VERSION);
            writeIntLe(output, embedding.length);
            for (float value : embedding) {
                writeIntLe(output, Float.floatToRawIntBits(value));
            }
            output.flush();
            fileOutput.getFD().sync();
        }
        replaceFile(temporary, target);
    }

    private void writeBytesAtomically(File directory, String name, byte[] bytes)
            throws IOException {
        File target = safeChild(directory, name);
        File temporary = safeChild(directory, name + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        replaceFile(temporary, target);
    }

    private List<FaceProfile> loadProfiles() throws IOException {
        String json = readUtf8(metadataFile);
        try {
            JSONArray array = new JSONObject(json).getJSONArray("profiles");
            List<FaceProfile> profiles = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                FaceProfile profile = new FaceProfile(
                        object.getString("id"),
                        object.getString("displayName"),
                        object.getInt("sampleCount"),
                        object.getLong("createdAtMillis"));
                if (profile.getSampleCount() != ENROLLMENT_SAMPLE_COUNT) {
                    throw new IOException("Face profile has an invalid sample count");
                }
                safeChild(root, profile.getId());
                if (findById(profiles, profile.getId()) >= 0
                        || findByDisplayName(profiles, profile.getDisplayName()) >= 0) {
                    throw new IOException("Face profile metadata contains duplicates");
                }
                profiles.add(profile);
            }
            return profiles;
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException("Face profile metadata is malformed", exception);
        }
    }

    private void writeProfiles(List<FaceProfile> profiles) throws IOException {
        JSONArray array = new JSONArray();
        JSONObject object = new JSONObject();
        try {
            for (FaceProfile profile : profiles) {
                JSONObject item = new JSONObject();
                item.put("id", profile.getId());
                item.put("displayName", profile.getDisplayName());
                item.put("sampleCount", profile.getSampleCount());
                item.put("createdAtMillis", profile.getCreatedAtMillis());
                array.put(item);
            }
            object.put("profiles", array);
        } catch (JSONException exception) {
            throw new IOException("Cannot serialize face profiles", exception);
        }
        File temporary = safeChild(root, METADATA_TEMP_FILE);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(object.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        replaceFile(temporary, metadataFile);
    }

    private void replaceFile(File temporary, File target) throws IOException {
        if (!temporary.renameTo(target)) {
            throw new IOException("Cannot atomically replace " + target.getName());
        }
    }

    private File safeChild(File parent, String childName) throws IOException {
        File candidate = new File(parent, childName).getCanonicalFile();
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        if (!candidatePath.startsWith(rootPath + File.separator)) {
            throw new IOException("Face path escapes the repository root");
        }
        return candidate;
    }

    private void deleteTree(File file) throws IOException {
        File checked = file.getCanonicalFile();
        String rootPrefix = root.getCanonicalPath() + File.separator;
        if (!checked.getCanonicalPath().startsWith(rootPrefix)) {
            throw new IOException("Refusing to delete outside the face repository");
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

    private static FaceProfile requireProfile(List<FaceProfile> profiles, String profileId)
            throws IOException {
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("Face profile ID is required");
        }
        int index = findById(profiles, profileId);
        if (index < 0) {
            throw new IOException("Face profile does not exist");
        }
        return profiles.get(index);
    }

    private static int findById(List<FaceProfile> profiles, String profileId) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(profileId)) {
                return i;
            }
        }
        return -1;
    }

    private static int findByDisplayName(List<FaceProfile> profiles, String displayName) {
        String key = displayName.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getDisplayName().trim().toLowerCase(Locale.ROOT).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Face profile display name is required");
        }
        return displayName.trim();
    }

    private static void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length == 0
                || embedding.length > MAX_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("Face embedding is invalid");
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Face embedding must be finite");
            }
        }
    }

    private static String readUtf8(File file) throws IOException {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void requireBytes(InputStream input, byte[] expected) throws IOException {
        for (byte value : expected) {
            if (input.read() != (value & 0xff)) {
                throw new IOException("Stored face embedding has invalid magic");
            }
        }
    }

    private static int readIntLe(InputStream input) throws IOException {
        int b0 = input.read();
        int b1 = input.read();
        int b2 = input.read();
        int b3 = input.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException();
        }
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void writeIntLe(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }
}
