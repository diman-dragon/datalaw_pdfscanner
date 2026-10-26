package com.datalaw.pdfscanner.camera

import android.graphics.Bitmap
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.Breadcrumbs
import com.datalaw.pdfscanner.core.CameraSession
import com.datalaw.pdfscanner.core.UiHost
import com.datalaw.pdfscanner.core.askSaveAsIs
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.imaging.Sharpness
import com.datalaw.pdfscanner.imaging.SheetFinder
import com.datalaw.pdfscanner.imaging.ShotFile
import com.datalaw.pdfscanner.imaging.shotSharpness
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A soft shot is taken again (up to this many photos in all) and the sharpest of them is kept. */
private const val MAX_PHOTOS = 3

/**
 * One shot: the photo goes to a file, a small copy is loaded ONCE to check the sharpness and (Auto) to find the sheet, then it is freed before the session
 * saves anything. A soft photo is not shown to the person at once: the camera takes up to two more and keeps the sharpest; only if even that one is soft
 * it asks "Retake / Save as is".
 * [region] = where the item is in the picture (the frame, or the outline found live), null = the whole picture.
 * Returns null when the person chose to retake (the photo is deleted), otherwise the shot for the session.
 */
internal suspend fun takeShot(
    rig: CameraRig,
    cacheDir: File,
    host: UiHost,
    session: CameraSession,
    auto: Boolean,
    region: FloatArray?,
    aspect: Float,
    land: Boolean,
): ShotFile? {
    var best: Bitmap? = null
    var bestFile: File? = null
    var bestSharp = -1f
    var crop: FloatArray? = null
    try {
        val dir = File(cacheDir, "shots").apply { mkdirs() }
        repeat(MAX_PHOTOS) { n ->
            if (n > 0 && !Sharpness.isBlurry(bestSharp)) return@repeat
            val file = try {
                rig.takePhoto(File(dir, "shot_${System.nanoTime()}.jpg"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (n == 0) throw e else return@repeat // the extra try failed: what is in hand is kept
            }
            if (n == 0) {
                val (w, h) = withContext(Dispatchers.IO) { BitmapLoader.uprightSize(file) }
                Breadcrumbs.note("photo taken ${w}x$h, auto=$auto")
                crop = if (Math.abs(w.toFloat() / h - aspect) < 0.04f * aspect) region else null
            } else {
                Breadcrumbs.note("photo $n was soft, taken again")
            }
            val p = withContext(Dispatchers.IO) { BitmapLoader.load(file, 1600) }
            val s = withContext(Dispatchers.Default) { shotSharpness(p, crop, aspect) }
            if (s > bestSharp) {
                best?.recycle()
                bestFile?.delete()
                best = p
                bestFile = file
                bestSharp = s
            } else {
                p.recycle()
                file.delete()
            }
        }
        val file = bestFile ?: return null
        val p = best ?: return null
        if (Sharpness.isBlurry(bestSharp) && !host.askSaveAsIs(tx(R.string.qs_blur))) {
            file.delete()
            return null
        }
        val found = if (auto) withContext(Dispatchers.Default) { SheetFinder.find(p, session.minAreaFrac, session.expectRatio) } else null
        return ShotFile(file, crop, aspect, land, found = found)
    } finally {
        best?.recycle() // before the session starts to render: two big pictures at once are what the phone cannot carry
    }
}
