package de.anisentinel.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object CoverImageLoader {
    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, url: String, targetPixels: Int = 480): Bitmap? {
        memory.get(url)?.let { return it }
        val directory = File(context.cacheDir, "covers").apply { mkdirs() }
        val file = File(directory, url.sha256())
        val bytes = if (file.isFile) {
            file.readBytes()
        } else {
            download(url)?.also { downloaded ->
                runCatching { file.writeBytes(downloaded) }
            }
        } ?: return null
        return decodeSampled(bytes, targetPixels)?.also { memory.put(url, it) }
    }

    private fun download(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "AniSentinel/0.10.0")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeSampled(bytes: ByteArray, targetPixels: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPixels &&
            bounds.outHeight / (sample * 2) >= targetPixels
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
}
