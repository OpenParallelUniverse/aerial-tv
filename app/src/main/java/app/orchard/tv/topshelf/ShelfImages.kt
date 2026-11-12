package app.orchard.tv.topshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import app.orchard.tv.ui.components.smoothCut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Size-keyed immutable artwork; bounded download, memory and atomic disk caches. */
object ShelfImages {
    private const val MAX_BYTES = 6 * 1024 * 1024
    private const val DISK_BYTES = 48L * 1024 * 1024
    private const val MAX_AGE = 3L * 24 * 60 * 60 * 1000
    private val slots = Semaphore(2)
    private val diskLock = Any()
    private data class CachedArt(val bitmap: Bitmap, val storedAt: Long)
    private val cache = object : LruCache<String, CachedArt>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedArt) = value.bitmap.byteCount
    }
    private fun cached(key: String): Bitmap? {
        val entry = cache.get(key) ?: return null
        if (System.currentTimeMillis() - entry.storedAt < MAX_AGE) return entry.bitmap
        cache.remove(key)
        return null
    }

    fun trimMemory() { cache.evictAll() }

    suspend fun load(context: Context, value: String, width: Int = 392, height: Int = 220): Bitmap? = withContext(Dispatchers.IO) {
        val w = width.coerceIn(1, 1568); val h = height.coerceIn(1, 880)
        val key = "$value@$w:$h:v2"
        cached(key)?.let { return@withContext it }
        slots.withPermit {
            cached(key)?.let { return@withPermit it }
            runCatching {
                val directory = File(context.cacheDir, "topshelf-art").apply { mkdirs() }
                val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                val disk = File(directory, "$hash.png")
                if (disk.exists() && System.currentTimeMillis() - disk.lastModified() < MAX_AGE) {
                    BitmapFactory.decodeFile(disk.path)?.let { cache.put(key, CachedArt(it, disk.lastModified())); return@runCatching it }
                }
                val uri = Uri.parse(value)
                val bytes = when (uri.scheme) {
                    "https", "http" -> {
                        val connection = URL(value).openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 4000; connection.readTimeout = 4000
                            connection.inputStream.use { it.readBytesBounded() }
                        } finally { connection.disconnect() }
                    }
                    "content", "android.resource" -> context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded() }
                    else -> null
                } ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > w * 2 || bounds.outHeight / sample > h * 2) sample *= 2
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@runCatching null
                val art = bake(raw, w, h)
                raw.recycle()
                cache.put(key, CachedArt(art, System.currentTimeMillis()))
                // Disk failure must not discard already decoded content.
                runCatching { synchronized(diskLock) {
                    val temporary = File(directory, "$hash.part")
                    temporary.outputStream().use { art.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!temporary.renameTo(disk)) temporary.delete()
                    prune(directory)
                } }
                art
            }.getOrNull()
        }
    }

    internal fun bake(raw: Bitmap, width: Int, height: Int): Bitmap {
        val composed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = maxOf(width.toFloat() / raw.width, height.toFloat() / raw.height)
        val dx = (width - raw.width * scale) / 2f; val dy = (height - raw.height * scale) * .35f
        canvas.drawBitmap(raw, null, RectF(dx, dy, dx + raw.width * scale, dy + raw.height * scale), paint)
        paint.shader = LinearGradient(0f, height * .52f, 0f, height.toFloat(), 0, 0xAD000000.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, height * .52f, width.toFloat(), height.toFloat(), paint)
        return smoothCut(composed, width, height, width * 12f / 196f).also { composed.recycle() }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }.orEmpty()
        var retained = 0L
        val now = System.currentTimeMillis()
        files.forEach {
            retained += it.length()
            if (retained > DISK_BYTES || now - it.lastModified() > MAX_AGE) it.delete()
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray? {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > MAX_BYTES) return null
            out.write(buffer, 0, n)
        }
    }
}
