package com.huimei.voice.face;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class FaceRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void savesListsAndLoadsEmbeddingAndPortrait() throws Exception {
        FaceRepository repository = new FaceRepository(temporaryFolder.newFolder("faces"));
        float[] embedding = embedding(128, 0.01f);
        byte[] portrait = new byte[]{1, 2, 3, 4};

        FaceProfile saved = repository.saveEnrollment(" Alice ", embedding, portrait);
        List<FaceProfile> profiles = repository.listProfiles();

        assertEquals(1, profiles.size());
        assertEquals(saved, profiles.get(0));
        assertEquals("Alice", saved.getDisplayName());
        assertEquals(5, saved.getSampleCount());
        assertArrayEquals(embedding, repository.loadEmbedding(saved.getId(), 128), 0f);
        assertArrayEquals(portrait, repository.loadPortrait(saved.getId()));
    }

    @Test
    public void caseInsensitiveNameReplacesExistingProfile() throws Exception {
        FaceRepository repository = new FaceRepository(temporaryFolder.newFolder("replace"));
        FaceProfile first = repository.saveEnrollment(
                "Alice", embedding(128, 0.01f), new byte[]{1});
        FaceProfile second = repository.saveEnrollment(
                " alice ", embedding(128, 0.02f), new byte[]{2});

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getCreatedAtMillis(), second.getCreatedAtMillis());
        assertEquals(1, repository.listProfiles().size());
        assertArrayEquals(new byte[]{2}, repository.loadPortrait(second.getId()));
    }

    @Test
    public void rejectsInvalidNamesUnknownProfilesAndDimensions() throws Exception {
        FaceRepository repository = new FaceRepository(temporaryFolder.newFolder("invalid"));

        assertThrows(IllegalArgumentException.class,
                () -> repository.saveEnrollment("  ", embedding(128, 0.01f), new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> repository.saveEnrollment("Alice", new float[]{Float.NaN}, new byte[]{1}));
        assertThrows(IOException.class,
                () -> repository.loadEmbedding("../outside", 128));
        assertThrows(IOException.class,
                () -> repository.loadPortrait("missing"));
        FaceProfile saved = repository.saveEnrollment(
                "Alice", embedding(128, 0.01f), new byte[]{1});
        assertThrows(IOException.class,
                () -> repository.loadEmbedding(saved.getId(), 127));
    }

    @Test
    public void rejectsCorruptEmbeddingAndTraversalMetadata() throws Exception {
        File root = temporaryFolder.newFolder("corrupt");
        FaceRepository repository = new FaceRepository(root);
        FaceProfile saved = repository.saveEnrollment(
                "Alice", embedding(128, 0.01f), new byte[]{1});
        File embedding = new File(new File(root, saved.getId()), "embedding.f32");
        try (FileOutputStream output = new FileOutputStream(embedding, true)) {
            output.write(99);
        }
        assertThrows(IOException.class,
                () -> repository.loadEmbedding(saved.getId(), 128));

        String malicious = "{\"profiles\":[{\"id\":\"../escape\","
                + "\"displayName\":\"Mallory\",\"sampleCount\":5,"
                + "\"createdAtMillis\":1}]}";
        Files.write(new File(root, "profiles.json").toPath(),
                malicious.getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, repository::listProfiles);
    }

    @Test
    public void deleteAndClearStayInsideRepository() throws Exception {
        File parent = temporaryFolder.newFolder("parent");
        File root = new File(parent, "faces");
        File sentinel = new File(parent, "keep.txt");
        Files.write(sentinel.toPath(), new byte[]{7});
        FaceRepository repository = new FaceRepository(root);
        FaceProfile alice = repository.saveEnrollment(
                "Alice", embedding(128, 0.01f), new byte[]{1});
        repository.saveEnrollment("Bob", embedding(128, 0.02f), new byte[]{2});

        repository.deleteProfile(alice.getId());
        assertEquals(1, repository.listProfiles().size());
        repository.clearAll();

        assertTrue(repository.listProfiles().isEmpty());
        assertTrue(sentinel.isFile());
        assertFalse(new File(root, alice.getId()).exists());
    }

    private static float[] embedding(int length, float start) {
        float[] value = new float[length];
        for (int i = 0; i < length; i++) {
            value[i] = start + i * 0.001f;
        }
        return value;
    }
}
