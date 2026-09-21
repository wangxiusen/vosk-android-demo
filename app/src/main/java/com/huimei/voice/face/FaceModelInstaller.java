package com.huimei.voice.face;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FaceModelInstaller {
    private final AssetManager assets;
    private final File modelDirectory;

    public FaceModelInstaller(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        assets = context.getApplicationContext().getAssets();
        modelDirectory = new File(context.getFilesDir(), "face-models").getCanonicalFile();
    }

    public synchronized ModelFiles install() throws IOException {
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
            throw new IOException("Cannot create face model directory");
        }
        if (!modelDirectory.isDirectory()) {
            throw new IOException("Face model path is not a directory");
        }
        File detector = install(FaceModelManifest.YUNET);
        File recognizer = install(FaceModelManifest.SFACE);
        return new ModelFiles(detector, recognizer);
    }

    private File install(FaceModelManifest.Entry entry) throws IOException {
        File target = safeChild(entry.fileName());
        if (matches(target, entry)) {
            return target;
        }
        File temporary = safeChild(entry.fileName() + ".tmp");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Cannot replace temporary face model");
        }
        try (InputStream input = new BufferedInputStream(assets.open(entry.assetPath()));
             FileOutputStream fileOutput = new FileOutputStream(temporary);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
        if (!matches(temporary, entry)) {
            if (!temporary.delete()) {
                temporary.deleteOnExit();
            }
            throw new IOException("Face model checksum mismatch: " + entry.fileName());
        }
        if (!temporary.renameTo(target)) {
            throw new IOException("Cannot install face model: " + entry.fileName());
        }
        return target;
    }

    private File safeChild(String name) throws IOException {
        File candidate = new File(modelDirectory, name).getCanonicalFile();
        if (!candidate.getCanonicalPath().startsWith(
                modelDirectory.getCanonicalPath() + File.separator)) {
            throw new IOException("Face model path escapes model directory");
        }
        return candidate;
    }

    private static boolean matches(File file, FaceModelManifest.Entry entry)
            throws IOException {
        return file.isFile()
                && file.length() == entry.sizeBytes()
                && entry.sha256().equals(sha256(file));
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }

    public static final class ModelFiles {
        private final File detector;
        private final File recognizer;

        private ModelFiles(File detector, File recognizer) {
            this.detector = detector;
            this.recognizer = recognizer;
        }

        public File detector() {
            return detector;
        }

        public File recognizer() {
            return recognizer;
        }
    }
}
