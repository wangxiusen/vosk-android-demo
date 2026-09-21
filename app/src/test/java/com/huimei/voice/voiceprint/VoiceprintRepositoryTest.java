package com.huimei.voice.voiceprint;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class VoiceprintRepositoryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File root;
    private VoiceprintRepository repository;

    @Before
    public void setUp() throws Exception {
        root = temporaryFolder.newFolder("voiceprint");
        repository = new VoiceprintRepository(root);
    }

    @Test
    public void duplicateDisplayNameReplacesLogicalProfileAndInvalidatesVectors() throws Exception {
        VoiceprintProfile first = repository.saveEnrollment(
                " Alice ", samples((short) 1), 16000);
        repository.saveEmbedding(
                first.getId(),
                VoiceprintEngineType.SHERPA,
                VoiceprintEngineType.SHERPA.modelId(),
                new float[]{1f, 0f});

        VoiceprintProfile replacement = repository.saveEnrollment(
                "alice", samples((short) 9), 16000);

        assertEquals(first.getId(), replacement.getId());
        assertEquals(1, repository.listProfiles().size());
        assertArrayEquals(new short[]{9, 10},
                repository.loadEnrollment(first.getId()).get(0).getSamples());
        assertFalse(repository.loadEmbedding(
                first.getId(),
                VoiceprintEngineType.SHERPA,
                VoiceprintEngineType.SHERPA.modelId(),
                2).isPresent());
    }

    @Test
    public void embeddingRoundTripsWithExactFloatBits() throws Exception {
        VoiceprintProfile profile = repository.saveEnrollment("Alice", samples((short) 1), 16000);
        float[] expected = new float[]{-0.0f, Float.MIN_VALUE, -1.25f, 3.5f};

        repository.saveEmbedding(
                profile.getId(), VoiceprintEngineType.VOSK, "model-v1", expected);
        float[] actual = repository.loadEmbedding(
                profile.getId(), VoiceprintEngineType.VOSK, "model-v1", 4).get();

        for (int i = 0; i < expected.length; i++) {
            assertEquals(Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(actual[i]));
        }
    }

    @Test
    public void modelOrDimensionMismatchReturnsEmptyButKeepsWavs() throws Exception {
        VoiceprintProfile profile = repository.saveEnrollment("Alice", samples((short) 2), 16000);
        repository.saveEmbedding(
                profile.getId(), VoiceprintEngineType.SHERPA, "model-v1", new float[]{1f, 2f});

        Optional<float[]> wrongModel = repository.loadEmbedding(
                profile.getId(), VoiceprintEngineType.SHERPA, "model-v2", 2);
        Optional<float[]> wrongDimension = repository.loadEmbedding(
                profile.getId(), VoiceprintEngineType.SHERPA, "model-v1", 3);

        assertFalse(wrongModel.isPresent());
        assertFalse(wrongDimension.isPresent());
        assertEquals(3, repository.loadEnrollment(profile.getId()).size());
    }

    @Test
    public void deleteRemovesProfileSamplesAndBothEngineVectors() throws Exception {
        VoiceprintProfile profile = repository.saveEnrollment("Alice", samples((short) 3), 16000);
        repository.saveEmbedding(
                profile.getId(), VoiceprintEngineType.SHERPA, "s1", new float[]{1f});
        repository.saveEmbedding(
                profile.getId(), VoiceprintEngineType.VOSK, "v1", new float[]{1f});

        repository.deleteProfile(profile.getId());

        assertTrue(repository.listProfiles().isEmpty());
        assertFalse(new File(root, profile.getId()).exists());
    }

    @Test
    public void clearAllLeavesAnEmptyRepository() throws Exception {
        repository.saveEnrollment("Alice", samples((short) 4), 16000);
        repository.saveEnrollment("Bob", samples((short) 5), 16000);

        repository.clearAll();

        assertTrue(repository.listProfiles().isEmpty());
        assertTrue(root.isDirectory());
        assertEquals(1, root.listFiles().length);
        assertEquals("profiles.json", root.listFiles()[0].getName());
    }

    @Test
    public void malformedMetadataFailsClosedWithoutEscapingRoot() throws Exception {
        File outside = temporaryFolder.newFile("outside-marker");
        writeUtf8(new File(root, "profiles.json"),
                "{\"profiles\":[{\"id\":\"../outside-marker\","
                        + "\"displayName\":\"Mallory\",\"sampleCount\":3,"
                        + "\"createdAtMillis\":1}]}");

        assertThrows(IOException.class, () -> repository.listProfiles());
        repository.clearAll();

        assertTrue(outside.exists());
        assertTrue(repository.listProfiles().isEmpty());
    }

    @Test
    public void enrollmentRequiresExactlyThreeSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.saveEnrollment(
                        "Alice", Arrays.asList(new short[]{1}), 16000));
    }

    private static List<short[]> samples(short first) {
        return Arrays.asList(
                new short[]{first, (short) (first + 1)},
                new short[]{(short) (first + 2), (short) (first + 3)},
                new short[]{(short) (first + 4), (short) (first + 5)});
    }

    private static void writeUtf8(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
