package com.huimei.voice.functiongemma;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FunctionGemmaModelStore {
    private static final int BUFFER_BYTES = 64 * 1024;

    private final File root;
    private final FunctionGemmaModelManifest.Entry entry;
    private final File target;
    private final File stagingDirectory;

    public FunctionGemmaModelStore(
            File root,
            FunctionGemmaModelManifest.Entry entry) throws IOException {
        this.root = root.getCanonicalFile();
        this.entry = entry;
        if (!isSafePathComponent(entry.version()) || !isSafePathComponent(entry.filename())) {
            throw new IllegalArgumentException("Model path must stay inside store root");
        }
        this.target = requireInsideRoot(new File(
                new File(new File(this.root, "models"), entry.version()),
                entry.filename()));
        this.stagingDirectory = requireInsideRoot(new File(this.root, "staging"));
    }

    public synchronized boolean isInstalled() {
        return verifyFile(target);
    }

    public File installedFile() {
        return target;
    }

    public synchronized File install(InputStream source) throws IOException {
        if (!stagingDirectory.isDirectory() && !stagingDirectory.mkdirs()) {
            throw new IOException("Unable to create model staging directory");
        }
        File staged = File.createTempFile("functiongemma-", ".part", stagingDirectory);
        try {
            Verification copied = copyAndFingerprint(source, staged);
            requireVerified(copied.sizeBytes, copied.sha256);
            if (isInstalled()) {
                return target;
            }
            replaceTarget(staged);
            if (!verifyFile(target)) {
                throw new IOException("Model verification failed after install");
            }
            return target;
        } finally {
            if (staged.exists() && !staged.delete()) {
                staged.deleteOnExit();
            }
        }
    }

    public synchronized boolean delete() throws IOException {
        if (!target.exists()) {
            return false;
        }
        if (!target.delete()) {
            throw new IOException("Unable to delete installed model");
        }
        return true;
    }

    private Verification copyAndFingerprint(InputStream source, File staged) throws IOException {
        MessageDigest digest = newDigest();
        long copied = 0L;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream input = new BufferedInputStream(source);
             FileOutputStream fileOutput = new FileOutputStream(staged);
             BufferedOutputStream output = new BufferedOutputStream(fileOutput, BUFFER_BYTES)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > entry.sizeBytes()) {
                    throw new IOException("Model verification failed: unexpected size");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
        return new Verification(copied, toHex(digest.digest()));
    }

    private void replaceTarget(File staged) throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create model directory");
        }

        File backup = null;
        if (target.exists()) {
            backup = new File(parent, entry.filename() + ".backup");
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Unable to clear stale model backup");
            }
            if (!target.renameTo(backup)) {
                throw new IOException("Unable to stage invalid installed model");
            }
        }

        if (!staged.renameTo(target)) {
            if (backup != null) {
                backup.renameTo(target);
            }
            throw new IOException("Unable to atomically install verified model");
        }
        if (backup != null && !backup.delete()) {
            backup.deleteOnExit();
        }
    }

    private boolean verifyFile(File file) {
        if (!file.isFile() || file.length() != entry.sizeBytes()) {
            return false;
        }
        MessageDigest digest;
        try {
            digest = newDigest();
            byte[] buffer = new byte[BUFFER_BYTES];
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
        } catch (IOException error) {
            return false;
        }
        return entry.sha256().equals(toHex(digest.digest()));
    }

    private void requireVerified(long actualSize, String actualHash) throws IOException {
        if (actualSize != entry.sizeBytes() || !entry.sha256().equals(actualHash)) {
            throw new IOException("Model verification failed: size or SHA-256 mismatch");
        }
    }

    private File requireInsideRoot(File file) throws IOException {
        File canonical = file.getCanonicalFile();
        String rootPath = root.getPath() + File.separator;
        if (!canonical.getPath().startsWith(rootPath)) {
            throw new IllegalArgumentException("Model path must stay inside store root");
        }
        return canonical;
    }

    private static boolean isSafePathComponent(String value) {
        return !".".equals(value)
                && !"..".equals(value)
                && value.indexOf('/') < 0
                && value.indexOf('\\') < 0;
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static final class Verification {
        final long sizeBytes;
        final String sha256;

        Verification(long sizeBytes, String sha256) {
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }
    }
}
