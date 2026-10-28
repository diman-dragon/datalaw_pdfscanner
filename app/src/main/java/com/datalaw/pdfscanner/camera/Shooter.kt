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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One shot: the photo goes to a file, a small copy is loaded ONCE to check the sharpness and (Auto) to find the sheet, then it is freed before the session
 * saves anything. A soft shot asks "Retake / Save as is". The sharpness of the shot itself is the business of the moment it is taken in ([Stillness]).
 * [region] = where the item is in the picture (the frame, or the outline found live), null = the whole picture.
 * [gain] = how much lighter the page is to be made afterwards (the shot was taken darker on purpose, see [CameraRig.prepareShot]).
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
    gain: Float = 1f,
): ShotFile? {
    var preview: Bitmap? = null
    try {
        val dir = File(cacheDir, "shots").apply { mkdirs() }
        val file = rig.takePhoto(File(dir, "shot_${System.nanoTime()}.jpg"))
        val (w, h) = withContext(Dispatchers.IO) { BitmapLoader.uprightSize(file) }
        Breadcrumbs.note("photo taken ${w}x$h, auto=$auto, gain=$gain")
        val crop = if (Math.abs(w.toFloat() / h - aspect) < 0.04f * aspect) region else null

        val p = withContext(Dispatchers.IO) { BitmapLoader.load(file, 1600) }
        preview = p

        val soft = withContext(Dispatchers.Default) { Sharpness.isBlurry(shotSharpness(p, crop, aspect)) }
        if (soft && !host.askSaveAsIs(tx(R.string.qs_blur))) {
            file.delete()
            return null
        }
        val found = if (auto) withContext(Dispatchers.Default) { SheetFinder.find(p, session.minAreaFrac, session.expectRatio) } else null
        return ShotFile(file, crop, aspect, land, found = found, gain = gain)
    } finally {
        preview?.recycle() // before the session starts to render: two big pictures at once are what the phone cannot carry
    }
}
