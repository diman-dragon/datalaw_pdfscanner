package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap

/** How sharp the frame region of a shot is (the whole photo when the shot has no frame); "sharp" (1) when it cannot be measured. Blocking. */
fun shotSharpness(shot: ShotFile): Float = try {
    val c = shot.crop
    val img = BitmapLoader.load(shot.file, 1600)
    val region = if (c != null && Math.abs(img.width.toFloat() / img.height - shot.vr) < 0.04f * shot.vr) {
        val x0 = (c[0] * img.width).toInt().coerceIn(0, img.width - 1)
        val y0 = (c[1] * img.height).toInt().coerceIn(0, img.height - 1)
        Bitmap.createBitmap(img, x0, y0, ((c[2] - c[0]) * img.width).toInt().coerceIn(1, img.width - x0), ((c[3] - c[1]) * img.height).toInt().coerceIn(1, img.height - y0))
    } else img
    val s = Sharpness.of(region)
    if (region !== img) region.recycle()
    img.recycle()
    s
} catch (e: Throwable) {
    1f
}
