package com.nuvio.app.features.cinesnappa

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual object CineSnappaCache {
    private fun cachesPath(): String? =
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String

    actual fun clearNow(): Long {
        val root = cachesPath() ?: return 0L
        val fm = NSFileManager.defaultManager
        val before = sizeBytes()
        val entries = fm.contentsOfDirectoryAtPath(root, null).orEmpty()
        entries.forEach { name -> fm.removeItemAtPath("$root/$name", null) }
        return (before - sizeBytes()).coerceAtLeast(0L)
    }

    actual fun sizeBytes(): Long {
        val root = cachesPath() ?: return 0L
        val fm = NSFileManager.defaultManager
        val files = fm.subpathsOfDirectoryAtPath(root, null).orEmpty()
        return files.sumOf { sub ->
            val attrs = fm.attributesOfItemAtPath("$root/$sub", null)
            (attrs?.get(NSFileSize) as? Number)?.toLong() ?: 0L
        }
    }
}
