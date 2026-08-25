package com.huimei.voice.tts;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class AssetDirectoryCopier {
    private AssetDirectoryCopier() {
    }

    static String copyOnce(Context context, String assetDirectory) throws IOException {
        File destination = new File(context.getFilesDir(), assetDirectory);
        File completionMarker = new File(destination, ".copy-complete");
        if (completionMarker.isFile()) {
            return destination.getAbsolutePath();
        }
        copyEntry(context.getAssets(), assetDirectory, destination);
        if (!completionMarker.createNewFile() && !completionMarker.isFile()) {
            throw new IOException("无法创建模型数据完成标记");
        }
        return destination.getAbsolutePath();
    }

    private static void copyEntry(
            AssetManager assets,
            String assetPath,
            File destination) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyFile(assets, assetPath, destination);
            return;
        }
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("无法创建模型数据目录：" + destination);
        }
        for (String child : children) {
            copyEntry(
                    assets,
                    assetPath + "/" + child,
                    new File(destination, child));
        }
    }

    private static void copyFile(
            AssetManager assets,
            String assetPath,
            File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建模型数据目录：" + parent);
        }
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }
}
