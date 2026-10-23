package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap

/**
 * 0..1 crispness of the edges of a photo, independent of the content: the steepness of strong edges across their own contrast.
 * A crisp photo gives ~1, a soft one well below 0.75 (the limit of "may be blurry"). 1 when it cannot be measured (no false alarm).
 */
object Sharpness {
    const val LIMIT = 0.75f
    private const val R = 8

    fun isBlurry(s: Float) = s < LIMIT

    fun of(src: Bitmap, maxSide: Int = 1280): Float {
        val k = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
        val b = if (k < 1f) Bitmap.createScaledBitmap(src, Math.round(src.width * k).coerceAtLeast(2 * R + 4), Math.round(src.height * k).coerceAtLeast(2 * R + 4), true) else src
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()
        val g = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            g[i] = 0.3f * ((c shr 16) and 255) + 0.59f * ((c shr 8) and 255) + 0.11f * (c and 255)
        }
        val ratios = FloatArray(w * h / 4 + 1)
        var n = 0
        var y = R
        while (y < h - R) {
            var x = R
            while (x < w - R) {
                val i = y * w + x
                val gx = Math.abs(g[i + 1] - g[i - 1])
                val gy = Math.abs(g[i + w] - g[i - w])
                val gm = maxOf(gx, gy)
                if (gm >= 5f) {
                    val step = if (gx >= gy) 1 else w
                    var lo = Float.MAX_VALUE
                    var hi = -Float.MAX_VALUE
                    for (q in -R..R) {
                        val v = g[i + q * step]
                        if (v < lo) lo = v
                        if (v > hi) hi = v
                    }
                    val contrast = hi - lo
                    if (contrast >= 25f) ratios[n++] = minOf(1f, gm * 2f / contrast)
                }
                x += 2
            }
            y += 2
        }
        if (n < 40) return 1f
        java.util.Arrays.sort(ratios, 0, n)
        return ratios[(n * 0.6f).toInt()]
    }
}
