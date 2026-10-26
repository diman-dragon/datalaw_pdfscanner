package com.datalaw.pdfscanner.core

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.datalaw.pdfscanner.BuildConfig
import com.datalaw.pdfscanner.R
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * A crash must never be silent, and the report is shown ONLY when the last run ended badly. Three sources are put together at the next start:
 *  - an exception nobody caught: written to a file by the default handler, right before the process dies;
 *  - how the last process ended (Android 11+): a native crash, "killed for memory", "not responding" leave no exception at all;
 *  - the trail of what the person did before it ([Breadcrumbs]).
 * The person reads a plain reason and the whole report, to copy or send to the developer. The report stays until the person closes it.
 */
object CrashReport {
    /** [reason] = the plain reason the person reads, [text] = the details for the developer. */
    class Report(@StringRes val reason: Int, val text: String)

    private const val PREFS = "crash"
    private const val KEY_SEEN = "exit_seen"
    private const val DAYS = 3L
    private const val KIND_MEMORY = "memory"
    private const val KIND_JAVA = "java"

    private fun file(app: Application) = File(app.filesDir, "last_crash.txt")

    /** Call first thing in Application.onCreate. */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Breadcrumbs.init(app)
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val kind = if (e is OutOfMemoryError) KIND_MEMORY else KIND_JAVA
                file(app).writeText("$kind\n" + header(app) + "\n--- Exception ---\nThread: ${thread.name}\n" + Log.getStackTraceString(e))
            } catch (ignored: Throwable) {
                // the process is dying anyway; nothing may get in the way of the system handler
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun header(app: Application): String {
        val rt = Runtime.getRuntime()
        val mb = 1024 * 1024
        val mem = ActivityManager.MemoryInfo().also { app.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        val age = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000
        return "PDF Scanner ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}\n" +
            "Language: ${Locale.getDefault()}\n" +
            "Time: ${Date()}\n" +
            "Process age: $age s\n" +
            "Java heap: ${(rt.totalMemory() - rt.freeMemory()) / mb} of ${rt.maxMemory() / mb} MB\n" +
            "Native heap: ${Debug.getNativeHeapAllocatedSize() / mb} MB\n" +
            "Device memory: ${mem.availMem / mb} MB free of ${mem.totalMem / mb} MB" + (if (mem.lowMemory) " (LOW)" else "") + "\n"
    }

    /** How the last process ended, already put into words: the plain reason and the details. */
    private class Exit(@StringRes val reason: Int, val text: String)

    /** What is to be shown at this start (null = the last run ended normally). Reading it consumes it. */
    fun pending(app: Application): Report? = try {
        val f = file(app)
        val saved = if (f.exists()) f.readText() else null
        val exit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) lastBadExit(app) else null
        build(app, saved, exit)
    } catch (e: Throwable) {
        Log.w("PdfScanner", "crash report unavailable", e)
        null
    }

    private fun build(app: Application, saved: String?, exit: Exit?): Report? {
        if (saved == null && exit == null) return null
        val kind = saved?.substringBefore('\n')
        val body = saved?.substringAfter('\n') ?: ""
        val reason = when {
            kind == KIND_MEMORY -> R.string.crash_r_memory
            kind == KIND_JAVA -> R.string.crash_r_java
            exit != null -> exit.reason
            else -> R.string.crash_r_other
        }
        val trail = Breadcrumbs.previous().ifEmpty { "(nothing was noted)" }
        val text = "=== PDF Scanner: crash report ===\n" +
            (if (body.isNotEmpty()) body else header(app) + "(this is the header of the NEW run: the process that ended left no exception, the system ended it)\n") +
            "\n--- What was done before it (oldest first) ---\n$trail\n" +
            (if (exit != null) "\n--- How the process ended ---\n${exit.text}" else "")
        return Report(reason, text)
    }

    /** The newest failed end of the process since the last look (and not older than a few days); remembers what has been seen. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun lastBadExit(app: Application): Exit? {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = maxOf(prefs.getLong(KEY_SEEN, 0L), System.currentTimeMillis() - DAYS * 24 * 3600 * 1000)
        val all = exits(app)
        all.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong(KEY_SEEN, it).apply() }
        val bad = all.firstOrNull { it.timestamp > since && isBad(it) } ?: return null
        return Exit(reasonOf(bad), describe(bad))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exits(app: Application): List<ApplicationExitInfo> =
        app.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(app.packageName, 0, 8) ?: emptyList()

    /** A crash, a native crash and "not responding" are always a failure; the system's own kills only when the app was on the screen. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun isBad(i: ApplicationExitInfo): Boolean = when (i.reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR -> true
        ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        -> i.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        else -> false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @StringRes
    private fun reasonOf(i: ApplicationExitInfo): Int = when (i.reason) {
        ApplicationExitInfo.REASON_CRASH -> R.string.crash_r_java
        ApplicationExitInfo.REASON_CRASH_NATIVE -> R.string.crash_r_native
        ApplicationExitInfo.REASON_ANR -> R.string.crash_r_anr
        ApplicationExitInfo.REASON_LOW_MEMORY -> R.string.crash_r_memory
        else -> R.string.crash_r_other
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(i: ApplicationExitInfo): String {
        val name = when (i.reason) {
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            else -> "reason ${i.reason}"
        }
        return "$name, status ${i.status}, importance ${i.importance}, memory rss ${i.rss / 1024} MB / pss ${i.pss / 1024} MB, at ${Date(i.timestamp)}\n" +
            "description: ${i.description ?: "-"}\n"
    }

    /** The person has read the report and closed it: it is not shown again. */
    fun dismiss(app: Application) {
        try { file(app).delete() } catch (e: Throwable) { Log.w("PdfScanner", "crash file not deleted", e) }
    }

    /** The report to any app that takes text (mail, a messenger); with no such app the copy button is still there. */
    fun share(app: Application, r: Report, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PDF Scanner ${BuildConfig.VERSION_NAME}: crash report")
            putExtra(Intent.EXTRA_TEXT, r.text)
        }
        try { app.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { Log.w("PdfScanner", "nobody to send the report", e) }
    }

    /** The details to the clipboard, to be pasted into a message to the developer. */
    fun copy(app: Application, r: Report) {
        app.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("PDF Scanner crash", r.text))
    }
}
