package com.datalaw.pdfscanner.core

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import com.datalaw.pdfscanner.data.FolderRepository
import com.datalaw.pdfscanner.ocr.OcrEngine
import com.datalaw.pdfscanner.settings.SettingsStore
import java.io.File

/** The things that live as long as the app (no DI framework: there are few). */
class AppGraph(val app: Application) {
    val settingsStore = SettingsStore(app)
    val folders = FolderRepository(app)
    val ocr = OcrEngine(app)
    val ui = UiHost()
    /** Page previews, limited by memory (a 16th of what the app may use), not by count. */
    val thumbs = object : LruCache<String, Pair<Bitmap, Int>>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Pair<Bitmap, Int>): Int = value.first.byteCount
    }

    fun shotsDir(): File = File(app.cacheDir, "shots").apply { mkdirs() }

    /** Photos of an unfinished scan are never kept. */
    fun clearShots() {
        shotsDir().listFiles()?.forEach { it.delete() }
    }
}
