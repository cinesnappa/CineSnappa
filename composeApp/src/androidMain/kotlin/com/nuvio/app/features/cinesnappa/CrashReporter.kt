package com.nuvio.app.features.cinesnappa

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.io.File

/**
 * CineSnappa: records the last crash to a file and, on the next launch, shows it
 * with a Copy button before the app starts, so crashes can be diagnosed without adb.
 */
object CrashReporter {
    private const val FILE_NAME = "cinesnappa_last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                }.getOrNull()
                File(appContext.filesDir, FILE_NAME).writeText(
                    buildString {
                        append("CineSnappa ").append(version).append(" · Android ").append(Build.VERSION.RELEASE)
                        append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                        append("Thread: ").append(thread.name).append("\n\n")
                        append(error.stackTraceToString().take(12_000))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pendingReport(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /** Shows the saved crash; [onContinue] runs after the user closes it. */
    fun showReport(activity: Activity, report: String, onContinue: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("CineSnappa crashed last time")
            .setMessage(report.take(4_000))
            .setCancelable(false)
            .setPositiveButton("Copy & continue") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("CineSnappa crash", report))
                Toast.makeText(activity, "Crash details copied", Toast.LENGTH_SHORT).show()
                clear(activity)
                onContinue()
            }
            .setNegativeButton("Continue") { _, _ ->
                clear(activity)
                onContinue()
            }
            .show()
    }
}
