package com.example.friendminder.data.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asynchronous contact photo loading with an in-memory cache (FRM-39,
 * Publisher responsibility #3): decodes a contact's `photoUri` (from
 * [ContactsLoader]) off the main thread, so a `ContentResolver` round-trip
 * and bitmap decode never block RecyclerView scrolling, and keeps decoded
 * bitmaps around so re-binding the same row (scroll back up, list refresh)
 * doesn't re-decode.
 *
 * The cache is sized in kilobytes, capped at an eighth of the app's
 * available memory - generous for small contact thumbnails while staying
 * safe on low-memory devices. It's process-lifetime (not persisted), which
 * is fine: contact photos come back from ContactsContract on every cold
 * start anyway.
 */
class ContactPhotoLoader {

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / BYTES_PER_KB
    }

    /**
     * Returns the decoded photo for [contactId], loading and caching it from
     * [photoUri] if it isn't already cached. Returns `null` (no exception) if
     * [photoUri] is null or the photo can't be decoded (deleted contact,
     * revoked permission, corrupt data) - callers should fall back to the
     * [AvatarPalette][com.example.friendminder.ui.common.AvatarPalette]
     * initials circle in that case.
     */
    suspend fun load(context: Context, contactId: String, photoUri: String?): Bitmap? {
        if (photoUri == null) return null
        cache.get(contactId)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.also { cache.put(contactId, it) }
        }
    }

    /** Drops a stale cache entry, e.g. after detecting a contact was removed from the device. */
    fun evict(contactId: String) {
        cache.remove(contactId)
    }

    companion object {
        private const val BYTES_PER_KB = 1024
        private const val CACHE_FRACTION_OF_MEMORY = 8

        private fun cacheSizeKb(): Int {
            val maxMemoryKb = (Runtime.getRuntime().maxMemory() / BYTES_PER_KB).toInt()
            return maxMemoryKb / CACHE_FRACTION_OF_MEMORY
        }
    }
}
