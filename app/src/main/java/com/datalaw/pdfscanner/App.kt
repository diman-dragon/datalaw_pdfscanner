package com.datalaw.pdfscanner

import android.app.Application
import com.datalaw.pdfscanner.core.AppGraph
import com.datalaw.pdfscanner.core.CrashReport

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        graph = AppGraph(this)
        graph.clearShots()
    }
}
