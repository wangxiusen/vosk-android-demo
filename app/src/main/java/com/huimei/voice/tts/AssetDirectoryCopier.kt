package com.huimei.voice.tts

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AssetDirectoryCopier private constructor() {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun copyOnce(context: Context, assetDirectory: String): String {
            val destination = File(context.filesDir, assetDirectory)
            val completionMarker = File(destination, ".copy-complete")
            if (completionMarker.isFile) return destination.absolutePath
            copyEntry(context.assets, assetDirectory, destination)
            if (!completionMarker.createNewFile() && !completionMarker.isFile) {
                throw IOException("无法创建模型数据完成标记")
            }
            return destination.absolutePath
        }

        @Throws(IOException::class)
        private fun copyEntry(assets: AssetManager, assetPath: String, destination: File) {
            val children = assets.list(assetPath)
            if (children.isNullOrEmpty()) {
                copyFile(assets, assetPath, destination)
                return
            }
            if (!destination.isDirectory && !destination.mkdirs()) {
                throw IOException("无法创建模型数据目录：$destination")
            }
            for (child in children) copyEntry(assets, "$assetPath/$child", File(destination, child))
        }

        @Throws(IOException::class)
        private fun copyFile(assets: AssetManager, assetPath: String, destination: File) {
            val parent = destination.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                throw IOException("无法创建模型数据目录：$parent")
            }
            assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output, 16 * 1024) }
            }
        }
    }
}
