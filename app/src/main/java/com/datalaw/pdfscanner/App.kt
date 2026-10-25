package com.datalaw.pdfscanner

import android.app.Application
import com.datalaw.pdfscanner.core.AppGraph

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.clearShots()
    }
}
