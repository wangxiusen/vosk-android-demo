package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class FunctionGemmaModelDownloaderTest {
    private static final byte[] MODEL = "verified-model".getBytes();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsNonHttpsAndCredentialBearingUrlsBeforeConnecting() throws Exception {
        FunctionGemmaModelDownloader downloader = new FunctionGemmaModelDownloader(
                url -> {
                    fail("Connection must not be opened");
                    return null;
                });

        assertDownloadFails(downloader, new URL("http://example.com/model"), newStore());
        assertDownloadFails(
                downloader, new URL("https://user:password@example.com/model"), newStore());
    }

    @Test
    public void redirectCannotDowngradeToHttp() throws Exception {
        FakeConnection redirect = new FakeConnection(
                new URL("https://example.com/model"), 302, new byte[0], 0L);
        redirect.location = "http://example.com/insecure";
        FunctionGemmaModelDownloader downloader = downloaderWith(redirect);
        FunctionGemmaModelStore store = newStore();

        assertDownloadFails(downloader, redirect.getURL(), store);

        assertFalse(store.isInstalled());
    }

    @Test
    public void followsHttpsRedirectAndInstallsOnlyTheVerifiedBody() throws Exception {
        FakeConnection redirect = new FakeConnection(
                new URL("https://example.com/model"), 302, new byte[0], 0L);
        redirect.location = "https://cdn.example.com/model";
        FakeConnection success = new FakeConnection(
                new URL(redirect.location), 200, MODEL, MODEL.length);
        FunctionGemmaModelDownloader downloader = downloaderWith(redirect, success);
        FunctionGemmaModelStore store = newStore();

        downloader.download(redirect.getURL(), store, (downloaded, declared) -> { });

        assertTrue(store.isInstalled());
    }

    @Test
    public void reportedProgressNeverExceedsDeclaredContentLength() throws Exception {
        FakeConnection success = new FakeConnection(
                new URL("https://example.com/model"), 200, MODEL, 4L);
        FunctionGemmaModelDownloader downloader = downloaderWith(success);
        List<Long> reported = new ArrayList<>();

        downloader.download(
                success.getURL(),
                newStore(),
                (downloaded, declared) -> reported.add(downloaded));

        assertFalse(reported.isEmpty());
        for (long progress : reported) {
            assertTrue(progress <= 4L);
        }
    }

    @Test
    public void errorResponseDoesNotOpenOrInstallTheBody() throws Exception {
        FakeConnection forbidden = new FakeConnection(
                new URL("https://example.com/model"), 403, MODEL, MODEL.length);
        FunctionGemmaModelDownloader downloader = downloaderWith(forbidden);
        FunctionGemmaModelStore store = newStore();

        assertDownloadFails(downloader, forbidden.getURL(), store);

        assertFalse(forbidden.inputOpened);
        assertFalse(store.isInstalled());
    }

    private FunctionGemmaModelDownloader downloaderWith(FakeConnection... connections) {
        Deque<FakeConnection> queue = new ArrayDeque<>();
        for (FakeConnection connection : connections) {
            queue.add(connection);
        }
        return new FunctionGemmaModelDownloader(url -> {
            if (queue.isEmpty()) {
                throw new IOException("Unexpected connection");
            }
            return queue.removeFirst();
        });
    }

    private FunctionGemmaModelStore newStore() throws Exception {
        FunctionGemmaModelManifest.Entry entry = new FunctionGemmaModelManifest.Entry(
                "test", "model.litertlm", MODEL.length, sha256(MODEL));
        File root = temporaryFolder.newFolder();
        return new FunctionGemmaModelStore(root, entry);
    }

    private static void assertDownloadFails(
            FunctionGemmaModelDownloader downloader,
            URL url,
            FunctionGemmaModelStore store) throws Exception {
        try {
            downloader.download(url, store, (downloaded, declared) -> { });
            fail("Expected download to fail");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isEmpty());
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

    private static final class FakeConnection extends HttpURLConnection {
        private final int responseCode;
        private final byte[] body;
        private final long declaredLength;
        String location;
        boolean inputOpened;

        FakeConnection(URL url, int responseCode, byte[] body, long declaredLength) {
            super(url);
            this.responseCode = responseCode;
            this.body = body;
            this.declaredLength = declaredLength;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public String getHeaderField(String name) {
            return "Location".equalsIgnoreCase(name) ? location : null;
        }

        @Override
        public long getContentLengthLong() {
            return declaredLength;
        }

        @Override
        public InputStream getInputStream() {
            inputOpened = true;
            return new ByteArrayInputStream(body);
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }
}
