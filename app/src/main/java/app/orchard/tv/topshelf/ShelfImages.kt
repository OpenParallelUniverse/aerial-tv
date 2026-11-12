package app.orchard.tv.topshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Small process-local cache; decode off the UI thread and bound both bytes and parallel requests. */
object ShelfImages {
    private const val MAX_BYTES = 6 * 1024 * 1024
    private val slots = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(context: Context, value: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(value)?.let { return@withContext it }
        slots.withPermit {
            cache.get(value)?.let { return@withPermit it }
            runCatching {
                val uri = Uri.parse(value)
                val bytes = when (uri.scheme) {
                    "https", "http" -> {
                        val connection = URL(value).openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 4000
                            connection.readTimeout = 4000
                            // Do not follow a network image redirect into a different URI scheme.
                            connection.instanceFollowRedirects = true
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
                while (bounds.outWidth / sample > 960 || bounds.outHeight / sample > 540) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { cache.put(value, it) }
            }.getOrNull()
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > MAX_BYTES) return null
            out.write(buffer, 0, n)
        }
    }
}
