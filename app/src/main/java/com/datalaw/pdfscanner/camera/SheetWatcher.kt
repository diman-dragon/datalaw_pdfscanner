package com.datalaw.pdfscanner.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.EdgeDetector
import com.datalaw.pdfscanner.imaging.SheetFinder

/**
 * [expect] = the width / height of the item that is scanned (null = any sheet), [minAreaFrac] = the smallest share of the picture it fills.
 * Looks for the sheet in the live camera frames (about 5 times a second) and reports its corners as 0..1 of the preview,
 * smoothed so the outline does not jitter; null when no sheet is seen for a moment. Runs on the analysis thread.
 */
class SheetWatcher(private val expect: Double?, private val minAreaFrac: Double, private val active: () -> Boolean, private val onSheet: (List<Offset>?) -> Unit) : ImageAnalysis.Analyzer {
    private var last = 0L
    private var shown: List<Offset>? = null
    private var misses = 0

    override fun analyze(image: ImageProxy) {
        try {
            if (!active()) { // Frame mode: nothing is searched
                if (shown != null || misses != 0) { shown = null; misses = 0; onSheet(null) }
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - last < 200) return
            last = now
            val raw = image.toBitmap()
            val bmp = BitmapLoader.rotate(raw, image.imageInfo.rotationDegrees)
            if (bmp !== raw) raw.recycle()
            val found = EdgeDetector.find(bmp, minAreaFrac)?.map { Offset(it.x / bmp.width, it.y / bmp.height) }
                ?.takeIf { SheetFinder.accepts(it, bmp.width, bmp.height, expect) }
            bmp.recycle()
            publish(found)
        } catch (e: Throwable) {
            publish(null)
        } finally {
            image.close()
        }
    }

    private fun publish(found: List<Offset>?) {
        if (found == null) {
            if (++misses >= 3) { shown = null; onSheet(null) }
            return
        }
        misses = 0
        val prev = shown
        val next = if (prev == null || prev.indices.any { (prev[it] - found[it]).getDistance() > 0.08f }) found
        else prev.indices.map { prev[it] * 0.6f + found[it] * 0.4f }
        shown = next
        onSheet(next)
    }
}
