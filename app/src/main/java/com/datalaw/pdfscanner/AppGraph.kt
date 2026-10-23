package com.datalaw.pdfscanner

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import com.datalaw.pdfscanner.data.FolderRepository
import com.datalaw.pdfscanner.ocr.OcrEngine
import com.datalaw.pdfscanner.settings.SettingsStore
import com.datalaw.pdfscanner.ui.UiHost
import java.io.File

/** The things that live as long as the app (no DI framework: there are few). */
class AppGraph(val app: Application) {
    val settingsStore = SettingsStore(app)
    val folders = FolderRepository(app)
    val ocr = OcrEngine(app)
    val ui = UiHost()
    val thumbs = LruCache<String, Pair<Bitmap, Int>>(48)

    fun shotsDir(): File = File(app.cacheDir, "shots").apply { mkdirs() }

    /** Photos of an unfinished scan are never kept. */
    fun clearShots() {
        shotsDir().listFiles()?.forEach { it.delete() }
    }
}
