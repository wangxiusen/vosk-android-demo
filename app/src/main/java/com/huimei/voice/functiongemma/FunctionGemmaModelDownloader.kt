package com.huimei.voice.functiongemma

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class FunctionGemmaModelDownloader(private val connectionFactory: ConnectionFactory) {
    fun interface ConnectionFactory {
        @Throws(IOException::class)
        fun open(url: URL): HttpURLConnection
    }

    fun interface ProgressListener {
        fun onProgress(downloadedBytes: Long, declaredBytes: Long)
    }

    constructor() : this(ConnectionFactory { it.openConnection() as HttpURLConnection })

    @Throws(IOException::class)
    fun download(source: URL, store: FunctionGemmaModelStore, progressListener: ProgressListener?): FileResult {
        validateUrl(source)
        var current = source
        for (redirectCount in 0..5) {
            val connection = connectionFactory.open(current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.requestMethod = "GET"
                val status = connection.responseCode
                if (isRedirect(status)) {
                    if (redirectCount == 5) throw IOException("Too many model download redirects")
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) throw IOException("Model download redirect has no location")
                    current = URL(current, location)
                    validateUrl(current)
                    continue
                }
                if (status !in 200..299) throw IOException("Model download failed with HTTP $status")

                val declaredBytes = connection.contentLengthLong
                val listener = progressListener ?: ProgressListener { _, _ -> }
                ProgressInputStream(connection.inputStream, declaredBytes, listener).use { input ->
                    return FileResult(store.install(input), declaredBytes)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many model download redirects")
    }

    class FileResult(private val file: File, private val declaredBytes: Long) {
        fun file(): File = file
        fun declaredBytes(): Long = declaredBytes
    }

    private class ProgressInputStream(
        private val delegate: InputStream,
        private val declaredBytes: Long,
        private val listener: ProgressListener
    ) : FilterInputStream(delegate) {
        private var downloadedBytes = 0L

        override fun read(): Int {
            val value = super.read()
            if (value != -1) report(1L)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(buffer, offset, length)
            if (count > 0) report(count.toLong())
            return count
        }

        private fun report(count: Long) {
            downloadedBytes += count
            val reported = if (declaredBytes >= 0L) minOf(downloadedBytes, declaredBytes) else downloadedBytes
            listener.onProgress(reported, declaredBytes)
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun validateUrl(url: URL) {
            if (!url.protocol.equals("https", ignoreCase = true)) throw IOException("Model download requires HTTPS")
            if (!url.userInfo.isNullOrEmpty()) throw IOException("Model download URL must not contain credentials")
        }

        private fun isRedirect(status: Int): Boolean = status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_SEE_OTHER ||
            status == 307 || status == 308
    }
}
