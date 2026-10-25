package com.datalaw.pdfscanner

import android.app.Application
import android.util.Log
import com.datalaw.pdfscanner.core.AppGraph
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("PdfScanner", "FATAL UNCAUGHT CRASH in thread ${thread.name}", throwable)
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(cacheDir, "crash.log").writeText("Thread: ${thread.name}\n$sw")
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
        graph = AppGraph(this)
        graph.clearShots()
    }
}
