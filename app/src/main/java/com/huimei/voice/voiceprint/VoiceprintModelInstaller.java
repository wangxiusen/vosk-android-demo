package com.huimei.voice.voiceprint;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class VoiceprintModelInstaller {
    private static final String SPEAKER_DIRECTORY_NAME = "vosk-model-spk-0.4";
    private static final String RECOGNITION_DIRECTORY_NAME = "model-cn";
    private static final String COMPLETION_MARKER = ".complete";
    private static final String SPEAKER_VERSION =
            "vosk-model-spk-0.4:a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7";
    private static final String RECOGNITION_VERSION = "vosk-model-small-cn-0.22";

    private final AssetManager assets;
    private final File modelRoot;

    public VoiceprintModelInstaller(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext == null ? context : applicationContext;
        assets = safeContext.getAssets();
        modelRoot = new File(safeContext.getFilesDir(), "voiceprint-models").getCanonicalFile();
        if (!modelRoot.exists() && !modelRoot.mkdirs()) {
            throw new IOException("Cannot create voiceprint model directory");
        }
    }

    public synchronized File installVoskSpeakerModel() throws IOException {
        return installDirectory(
                VoiceprintModelManifest.VOSK_SPEAKER_ASSET_DIRECTORY,
                SPEAKER_DIRECTORY_NAME,
                SPEAKER_VERSION);
    }

    public synchronized File installChineseRecognitionModel() throws IOException {
        return installDirectory(
                VoiceprintModelManifest.VOSK_RECOGNITION_ASSET_DIRECTORY,
                RECOGNITION_DIRECTORY_NAME,
                RECOGNITION_VERSION);
    }

    private File installDirectory(
            String assetDirectory,
            String targetName,
            String version) throws IOException {
        File target = safeChild(modelRoot, targetName);
        File marker = safeChild(target, COMPLETION_MARKER);
        if (target.isDirectory() && version.equals(readMarker(marker))) {
            return target;
        }

        File staging = safeChild(modelRoot, targetName + ".installing");
        deleteTreeIfPresent(staging);
        if (!staging.mkdirs()) {
            throw new IOException("Cannot create model staging directory");
        }
        try {
            copyAssetTree(assetDirectory, staging);
            writeMarker(safeChild(staging, COMPLETION_MARKER), version);
            deleteTreeIfPresent(target);
            if (!staging.renameTo(target)) {
                throw new IOException("Cannot activate installed voiceprint model");
            }
            return target;
        } catch (IOException exception) {
            deleteTreeIfPresent(staging);
            throw exception;
        }
    }

    private void copyAssetTree(String assetPath, File destination) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null) {
            throw new IOException("Cannot list model asset " + assetPath);
        }
        if (children.length == 0) {
            copyAssetFile(assetPath, destination);
            return;
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Cannot create model directory " + destination.getName());
        }
        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, safeChild(destination, child));
        }
    }

    private void copyAssetFile(String assetPath, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Cannot create model file directory");
        }
        try (InputStream input = new BufferedInputStream(assets.open(assetPath));
             FileOutputStream fileOutput = new FileOutputStream(destination);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
    }

    private static void writeMarker(File marker, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static String readMarker(File marker) throws IOException {
        if (!marker.isFile()) {
            return "";
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(marker));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private File safeChild(File parent, String name) throws IOException {
        File candidate = new File(parent, name).getCanonicalFile();
        String rootPath = modelRoot.getCanonicalPath() + File.separator;
        if (!candidate.getCanonicalPath().startsWith(rootPath)) {
            throw new IOException("Model path escapes private storage");
        }
        return candidate;
    }

    private void deleteTreeIfPresent(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        File checked = file.getCanonicalFile();
        if (!checked.getCanonicalPath().startsWith(modelRoot.getCanonicalPath() + File.separator)) {
            throw new IOException("Refusing to delete outside private model storage");
        }
        if (checked.isDirectory()) {
            File[] children = checked.listFiles();
            if (children == null) {
                throw new IOException("Cannot list model directory");
            }
            for (File child : children) {
                deleteTreeIfPresent(child);
            }
        }
        if (!checked.delete()) {
            throw new IOException("Cannot remove incomplete model data");
        }
    }
}
