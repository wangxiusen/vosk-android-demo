package com.huimei.voice.functiongemma;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class FunctionGemmaModelDownloader {
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_REDIRECTS = 5;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public interface ProgressListener {
        void onProgress(long downloadedBytes, long declaredBytes);
    }

    private final ConnectionFactory connectionFactory;

    public FunctionGemmaModelDownloader() {
        this(url -> (HttpURLConnection) url.openConnection());
    }

    FunctionGemmaModelDownloader(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public FileResult download(
            URL source,
            FunctionGemmaModelStore store,
            ProgressListener progressListener) throws IOException {
        validateUrl(source);
        URL current = source;

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpURLConnection connection = connectionFactory.open(current);
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();

                if (isRedirect(status)) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw new IOException("Too many model download redirects");
                    }
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("Model download redirect has no location");
                    }
                    current = new URL(current, location);
                    validateUrl(current);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("Model download failed with HTTP " + status);
                }

                long declaredBytes = connection.getContentLengthLong();
                ProgressListener listener = progressListener == null
                        ? (downloaded, declared) -> { }
                        : progressListener;
                try (InputStream input = new ProgressInputStream(
                        connection.getInputStream(), declaredBytes, listener)) {
                    java.io.File installed = store.install(input);
                    return new FileResult(installed, declaredBytes);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many model download redirects");
    }

    private static void validateUrl(URL url) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Model download requires HTTPS");
        }
        if (url.getUserInfo() != null && !url.getUserInfo().isEmpty()) {
            throw new IOException("Model download URL must not contain credentials");
        }
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    public static final class FileResult {
        private final java.io.File file;
        private final long declaredBytes;

        FileResult(java.io.File file, long declaredBytes) {
            this.file = file;
            this.declaredBytes = declaredBytes;
        }

        public java.io.File file() {
            return file;
        }

        public long declaredBytes() {
            return declaredBytes;
        }
    }

    private static final class ProgressInputStream extends FilterInputStream {
        private final long declaredBytes;
        private final ProgressListener listener;
        private long downloadedBytes;

        ProgressInputStream(
                InputStream input,
                long declaredBytes,
                ProgressListener listener) {
            super(input);
            this.declaredBytes = declaredBytes;
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                report(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            if (count > 0) {
                report(count);
            }
            return count;
        }

        private void report(long count) {
            downloadedBytes += count;
            long reported = declaredBytes >= 0L
                    ? Math.min(downloadedBytes, declaredBytes)
                    : downloadedBytes;
            listener.onProgress(reported, declaredBytes);
        }
    }
}
