package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class FunctionGemmaModelStoreTest {
    private static final byte[] VALID = "verified-model".getBytes();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validStreamIsInstalledAndReported() throws Exception {
        FunctionGemmaModelStore store = newStore(entryFor(VALID));

        File installed = store.install(new ByteArrayInputStream(VALID));

        assertTrue(store.isInstalled());
        assertTrue(installed.isFile());
        assertArrayEquals(VALID, Files.readAllBytes(installed.toPath()));
    }

    @Test
    public void wrongSizeAndWrongHashAreRejected() throws Exception {
        FunctionGemmaModelStore sizeStore = newStore(entryFor(VALID));
        assertInstallFails(sizeStore, "short".getBytes());
        assertFalse(sizeStore.isInstalled());

        byte[] sameSizeWrongHash = "invalid-modelx".getBytes();
        FunctionGemmaModelStore hashStore = newStore(entryFor(VALID));
        assertInstallFails(hashStore, sameSizeWrongHash);
        assertFalse(hashStore.isInstalled());
    }

    @Test
    public void htmlErrorBodyWithExpectedLengthIsRejected() throws Exception {
        byte[] html = "<html>denied</html>".getBytes();
        FunctionGemmaModelManifest.Entry entry = new FunctionGemmaModelManifest.Entry(
                "test", "model.litertlm", html.length, sha256("real-model-content".getBytes()));
        FunctionGemmaModelStore store = newStore(entry);

        assertInstallFails(store, html);

        assertFalse(store.isInstalled());
    }

    @Test
    public void failedInstallPreservesAnAlreadyValidModel() throws Exception {
        FunctionGemmaModelStore store = newStore(entryFor(VALID));
        File installed = store.install(new ByteArrayInputStream(VALID));

        assertInstallFails(store, "broken".getBytes());

        assertTrue(store.isInstalled());
        assertArrayEquals(VALID, Files.readAllBytes(installed.toPath()));
    }

    @Test
    public void validInstallReplacesAnInvalidTarget() throws Exception {
        FunctionGemmaModelStore store = newStore(entryFor(VALID));
        File target = store.installedFile();
        assertTrue(target.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write("corrupt".getBytes());
        }

        store.install(new ByteArrayInputStream(VALID));

        assertTrue(store.isInstalled());
        assertArrayEquals(VALID, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void entryPathsCannotEscapeTheStoreRoot() throws Exception {
        FunctionGemmaModelManifest.Entry malicious = new FunctionGemmaModelManifest.Entry(
                "../escape", "model.litertlm", VALID.length, sha256(VALID));
        try {
            newStore(malicious);
            fail("Expected an invalid entry path");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("root"));
        }
    }

    private FunctionGemmaModelStore newStore(FunctionGemmaModelManifest.Entry entry)
            throws IOException {
        return new FunctionGemmaModelStore(temporaryFolder.newFolder(), entry);
    }

    private static FunctionGemmaModelManifest.Entry entryFor(byte[] bytes) throws Exception {
        return new FunctionGemmaModelManifest.Entry(
                "test", "model.litertlm", bytes.length, sha256(bytes));
    }

    private static void assertInstallFails(FunctionGemmaModelStore store, byte[] bytes)
            throws Exception {
        try {
            store.install(new ByteArrayInputStream(bytes));
            fail("Expected model verification to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("verification"));
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
