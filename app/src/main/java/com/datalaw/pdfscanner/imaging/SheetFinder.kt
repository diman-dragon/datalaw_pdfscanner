package com.datalaw.pdfscanner.imaging

import androidx.compose.ui.geometry.Offset
import java.io.File

/** A sheet or item found in a photo: corners (TL, TR, BR, BL as 0..1 of the upright photo) and the real width / height they show. */
class Found(val quad: List<Offset>, val aspect: Double)

/** Finds the sheet or item: in the photo file after the shot, and the same rules of acceptance for the live frames of the watcher. */
object SheetFinder {
    /** True when [quad] (0..1 corners in a picture of w x h px) is believable as what is scanned; [expected] = its width / height, null = any sheet. */
    fun accepts(quad: List<Offset>, w: Int, h: Int, expected: Double?): Boolean =
        SheetFit.plausible(quad, w.toFloat() / h, checkRatio = expected == null) &&
            (expected == null || ShapeMatch.matches(quad, w, h, expected))

    /** The sheet in the photo, null when none is seen. [minAreaFrac] = the smallest share of the photo it fills. Blocking: call it off the main thread. */
    fun find(file: File, minAreaFrac: Double, expected: Double?): Found? = try {
        val img = BitmapLoader.load(file, 1200)
        try {
            val q = EdgeDetector.find(img, minAreaFrac)?.map { Offset(it.x / img.width, it.y / img.height) }
            if (q != null && accepts(q, img.width, img.height, expected)) Found(q, ShapeMatch.aspectOf(q, img.width, img.height)) else null
        } finally {
            img.recycle()
        }
    } catch (e: Throwable) {
        null
    }
}
