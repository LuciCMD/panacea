package com.clementine.panacea.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Pill photos, kept in the app's own storage: never in the gallery, never shared. Medications refer to
 * them by file name.
 */
class PhotoStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Decoded photos by name and size, so scrolling past the same pill doesn't decode it again. */
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun fileOf(name: String) = File(dir, name)

    /** Saves a photo the camera took, already upright and cropped. */
    suspend fun save(bitmap: Bitmap): String = withContext(Dispatchers.IO) { write(bitmap) }

    /** Copies a picture picked from the phone's photos. Null if it can't be read as an image. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder turns the picture upright from its EXIF data.
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            write(bitmap)
        } catch (e: Exception) {
            null
        }
    }

    /** The photo scaled to about [px] on its shorter side, or null if it's gone. */
    suspend fun load(name: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$name@$px"
        cache.get(key)?.let { return@withContext it }
        val file = fileOf(name)
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.also { cache.put(key, it) }
    }

    fun delete(name: String?) {
        if (name == null) return
        fileOf(name).delete()
        cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach(cache::remove)
    }

    /**
     * Deletes photos no medication uses. Only ones older than a day, so a photo taken for a medication
     * that is still being added survives.
     */
    suspend fun sweep(inUse: Collection<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - DAY_MS
        dir.listFiles()?.filter { it.name !in inUse && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun write(source: Bitmap): String {
        val longest = maxOf(source.width, source.height)
        val bitmap = if (longest > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else {
            source
        }
        val name = "${UUID.randomUUID()}.jpg"
        fileOf(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return name
    }

    private companion object {
        const val MAX_SIDE = 1280
        const val JPEG_QUALITY = 88
        const val CACHE_BYTES = 16 * 1024 * 1024
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
