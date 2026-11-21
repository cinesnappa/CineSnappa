package com.nuvio.app.features.cinesnappa

/**
 * CineSnappa cache control.
 *
 * Only temporary files are touched (image cache, subtitle files, downloaded update APKs,
 * streaming buffers). Addons, logins, settings, watch history and offline downloads are
 * stored elsewhere and are never removed.
 *
 * Automatic clearing happens at app start on Android:
 *  - after the app is updated to a new version, and
 *  - when the cache grew past [AUTO_CLEAR_LIMIT_BYTES] during the previous session.
 */
expect object CineSnappaCache {
    /** Deletes cached files right now and returns how many bytes were freed. */
    fun clearNow(): Long

    /** Current size of the cache in bytes. */
    fun sizeBytes(): Long
}

const val AUTO_CLEAR_LIMIT_BYTES: Long = 500L * 1024L * 1024L

fun formatCacheSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        "${(mb / 1024.0 * 10).toLong() / 10.0} GB"
    } else {
        "${(mb * 10).toLong() / 10.0} MB"
    }
}
