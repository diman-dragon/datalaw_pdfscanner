package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap

/** How sharp the frame region of an image is (the whole photo when crop is null); "sharp" (1) when it cannot be measured. */
fun shotSharpness(img: Bitmap, crop: FloatArray?, vr: Float): Float = try {
    val region = if (crop != null && Math.abs(img.width.toFloat() / img.height - vr) < 0.04f * vr) {
        val x0 = (crop[0] * img.width).toInt().coerceIn(0, img.width - 1)
        val y0 = (crop[1] * img.height).toInt().coerceIn(0, img.height - 1)
        val w = ((crop[2] - crop[0]) * img.width).toInt().coerceIn(1, img.width - x0)
        val h = ((crop[3] - crop[1]) * img.height).toInt().coerceIn(1, img.height - y0)
        Bitmap.createBitmap(img, x0, y0, w, h)
    } else img
    val s = Sharpness.of(region)
    if (region !== img) region.recycle()
    s
} catch (e: Throwable) {
    1f
}

/** How sharp the frame region of a shot is (the whole photo when the shot has no frame); "sharp" (1) when it cannot be measured. Blocking. */
fun shotSharpness(shot: ShotFile): Float = try {
    val img = BitmapLoader.load(shot.file, 1600)
    try {
        shotSharpness(img, shot.crop, shot.vr)
    } finally {
        img.recycle()
    }
} catch (e: Throwable) {
    1f
}
