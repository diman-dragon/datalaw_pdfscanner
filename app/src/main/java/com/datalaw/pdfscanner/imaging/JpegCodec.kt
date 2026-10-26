package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class Encoded(val jpeg: ByteArray, val w: Int, val h: Int)

/** JPEG into a byte budget: the best quality that fits, and if even the lowest does not, a smaller picture. */
object JpegCodec {
    private const val TOP = 92

    private fun encode(b: Bitmap, q: Int): ByteArray {
        val o = ByteArrayOutputStream(256 * 1024)
        b.compress(Bitmap.CompressFormat.JPEG, q, o)
        return o.toByteArray()
    }

    fun encodeToBudget(src: Bitmap, max: Int): Encoded {
        var c = src
        for (round in 0 until 5) {
            // a clean page (white paper) fits at the top quality and keeps its crisp text; a noisy photo goes down to what the budget allows
            val first = encode(c, TOP)
            if (first.size <= max) return done(c, src, first)
            var lo = 30
            var hi = TOP
            var best: ByteArray? = null
            for (step in 0 until 5) {
                val q = (lo + hi) / 2
                val b = encode(c, q)
                if (b.size <= max) { best = b; lo = q } else hi = q
            }
            if (best != null) return done(c, src, best)
            val nc = Bitmap.createScaledBitmap(c, Math.round(c.width * 0.85f).coerceAtLeast(16), Math.round(c.height * 0.85f).coerceAtLeast(16), true)
            if (c !== src) c.recycle()
            c = nc
        }
        return done(c, src, encode(c, 30))
    }

    private fun done(c: Bitmap, src: Bitmap, bytes: ByteArray): Encoded {
        val e = Encoded(bytes, c.width, c.height)
        if (c !== src) c.recycle()
        return e
    }
}
