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
 *
 * FRM-48 (performance testing, photo loading): [ContactsLoader] reads
 * `Phone.PHOTO_URI`, which - despite the generic-sounding name - is
 * ContactsContract's *full-size* display photo (up to 720x720, sometimes
 * larger for photos synced from some accounts), not the thumbnail. Every
 * avatar in this app renders at 48dp or 96dp (`fm_avatar_size_list`/
 * `_detail`), so decoding straight from that stream with no downsampling
 * meant every cache entry was a full-resolution `Bitmap` regardless of how
 * small it's actually drawn - e.g. a 2048x1536 source photo decodes to a
 * ~12.6MB ARGB_8888 bitmap (width x height x 4 bytes) to fill a 384x384px
 * (96dp @ xxxhdpi) ImageView. Against a cache budget of maxMemory/8 (tens of
 * MB on a typical device), that's room for only a handful of contacts'
 * photos before the LRU starts evicting and re-decoding on scroll - the
 * exact kind of jank that doesn't show up with a handful of seeded test
 * contacts but does with a realistic-sized, photo-heavy contact list.
 *
 * Fix: decode bounds-only first (`inJustDecodeBounds`), compute a power-of-2
 * `inSampleSize` against [MAX_DECODE_DIMENSION_PX] (comfortably above the
 * largest on-screen size this app ever draws an avatar at, so there's no
 * visible quality loss), then decode once at that size. Same math example
 * above downsamples to roughly 400x300px (~480KB) - about 25x less memory
 * per photo, so the same cache budget holds ~25x more contacts' photos
 * before anything gets evicted.
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
            runCatching { decodeSampled(context, Uri.parse(photoUri)) }
                .getOrNull()
                ?.also { cache.put(contactId, it) }
        }
    }

    /** Drops a stale cache entry, e.g. after detecting a contact was removed from the device. */
    fun evict(contactId: String) {
        cache.remove(contactId)
    }

    /**
     * Decodes [uri] downsampled so neither dimension exceeds
     * [MAX_DECODE_DIMENSION_PX], reading the stream twice (bounds, then the
     * real decode) since a `ContentResolver` stream for a `content://` URI
     * generally can't be rewound - the standard Android pattern for this.
     */
    private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
        // GH #116: this used to be
        //   `contentResolver.openInputStream(uri)?.use { decodeStream(...) } ?: return null`
        // - which looks like "return null if the stream couldn't be opened",
        // but isn't: BitmapFactory.decodeStream ALWAYS returns null when
        // inJustDecodeBounds is true (that's how it signals "bounds are in
        // `options`, no Bitmap was allocated" - see the platform docs), so
        // `.use { ... }` always evaluated to null here regardless of
        // whether the stream opened fine, and `?: return null` fired on
        // every single call. No contact photo has ever actually rendered
        // in this app because of it - every call fell through to the
        // initials fallback before reaching the real decode pass below.
        // Opening the stream and checking for null explicitly (rather than
        // reading it off the bounds-decode call's return value) fixes it.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_DECODE_DIMENSION_PX || height / sampleSize > MAX_DECODE_DIMENSION_PX) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private const val BYTES_PER_KB = 1024
        private const val CACHE_FRACTION_OF_MEMORY = 8

        // Comfortably above fm_avatar_size_detail (96dp) at xxxhdpi (4x): 384px.
        // Rounded up so a slightly-higher-density device still gets a sharp
        // decode without falling back to the next inSampleSize step down.
        private const val MAX_DECODE_DIMENSION_PX = 400

        private fun cacheSizeKb(): Int {
            val maxMemoryKb = (Runtime.getRuntime().maxMemory() / BYTES_PER_KB).toInt()
            return maxMemoryKb / CACHE_FRACTION_OF_MEMORY
        }
    }
}
