package com.datalaw.pdfscanner.core

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last things the person did (a tab, a screen, a dialog, a save), one short line each, kept in a small file. When the app ends badly the crash report
 * tells the developer what was going on; a crash report without it says only WHERE the code fell, not what led to it.
 */
object Breadcrumbs {
    private const val KEEP = 40
    private var file: File? = null
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var before = ""

    /** Call in Application.onCreate: what the PREVIOUS run wrote is kept to be read, a new trail begins. */
    fun init(app: Application) {
        val f = File(app.filesDir, "breadcrumbs.txt")
        file = f
        before = try { if (f.exists()) f.readText() else "" } catch (e: Throwable) { "" }
        note("start")
    }

    @Synchronized
    fun note(text: String) {
        lines.addLast(clock.format(Date()) + " " + text)
        while (lines.size > KEEP) lines.removeFirst()
        try { file?.writeText(lines.joinToString("\n")) } catch (e: Throwable) { /* a trail must never be the reason of a failure */ }
    }

    /** What the previous run did, oldest first; empty = nothing known. */
    fun previous(): String = before
}
