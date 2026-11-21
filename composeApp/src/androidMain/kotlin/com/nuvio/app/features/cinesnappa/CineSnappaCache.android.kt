package com.nuvio.app.features.cinesnappa

import android.content.Context
import java.io.File

actual object CineSnappaCache {
    private const val PREFS = "cinesnappa_cache"
    private const val KEY_LAST_VERSION = "last_version_name"
    private const val KEY_CLEAR_NEXT_LAUNCH = "clear_next_launch"

    private var appContext: Context? = null
    private var startupDone = false

    /**
     * Call at the very start of MainActivity.onCreate, before anything opens cache files.
     * Clears the cache if the app version changed or the cache got too big last session,
     * then measures the cache in the background for next time.
     */
    fun onAppStart(context: Context) {
        appContext = context.applicationContext
        if (startupDone) return
        startupDone = true
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val currentVersion = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull().orEmpty()
        val lastVersion = prefs.getString(KEY_LAST_VERSION, null)
        val versionChanged = lastVersion != null && lastVersion != currentVersion
        val tooBig = prefs.getBoolean(KEY_CLEAR_NEXT_LAUNCH, false)

        if (versionChanged || tooBig) {
            runCatching { clearNow() }
        }
        prefs.edit()
            .putString(KEY_LAST_VERSION, currentVersion)
            .putBoolean(KEY_CLEAR_NEXT_LAUNCH, false)
            .apply()

        // Measure off the main thread; if it's over the limit, clear on the next launch
        // (clearing mid-session could pull files out from under the player or image loader).
        Thread {
            runCatching {
                if (sizeBytes() > AUTO_CLEAR_LIMIT_BYTES) {
                    prefs.edit().putBoolean(KEY_CLEAR_NEXT_LAUNCH, true).apply()
                }
            }
        }.apply { isDaemon = true; name = "cinesnappa-cache-size" }.start()
    }

    actual fun clearNow(): Long {
        val ctx = appContext ?: return 0L
        return cacheRoots(ctx).sumOf { root -> root.listFiles().orEmpty().sumOf { deleteRecursively(it) } }
    }

    actual fun sizeBytes(): Long {
        val ctx = appContext ?: return 0L
        return cacheRoots(ctx).sumOf { root -> root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    private fun cacheRoots(ctx: Context): List<File> =
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).filter { it.exists() }

    private fun deleteRecursively(file: File): Long {
        val size = if (file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return if (file.deleteRecursively()) size else 0L
    }
}
