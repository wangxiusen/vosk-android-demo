package com.huimei.voice.functiongemma;

import java.util.Locale;
import java.util.Objects;

public final class FunctionGemmaModelManifest {
    public static final Entry MODEL = new Entry(
            "mobile-actions-38942192",
            "mobile_actions_q8_ekv1024.litertlm",
            288_964_608L,
            "33e295cbd996b419bb1de8f3f85c5b6b01ee058a2c89bdb2173cf3e6ff4ce9d0");

    private FunctionGemmaModelManifest() {
    }

    public static final class Entry {
        private final String version;
        private final String filename;
        private final long sizeBytes;
        private final String sha256;

        public Entry(String version, String filename, long sizeBytes, String sha256) {
            this.version = Objects.requireNonNull(version, "version");
            this.filename = Objects.requireNonNull(filename, "filename");
            if (version.trim().isEmpty() || filename.trim().isEmpty()) {
                throw new IllegalArgumentException("version and filename must not be blank");
            }
            if (sizeBytes <= 0L) {
                throw new IllegalArgumentException("sizeBytes must be positive");
            }
            String normalizedHash = Objects.requireNonNull(sha256, "sha256")
                    .toLowerCase(Locale.ROOT);
            if (!normalizedHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
            }
            this.sizeBytes = sizeBytes;
            this.sha256 = normalizedHash;
        }

        public String version() {
            return version;
        }

        public String filename() {
            return filename;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public String sha256() {
            return sha256;
        }
    }
}
