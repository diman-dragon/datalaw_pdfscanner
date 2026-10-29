package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.pdf.PdfTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Makes PDF pages. Heavy renders run strictly one at a time (phone memory); on a failure the page is made again smaller.
 */
class PageRenderer(private val target: PdfTarget) {
    /** The gate is NOT re-entrant (one permit): a render must never wait for the gate while it already holds it. */
    private suspend fun <T> guarded(block: (Double) -> T): T = gate.withPermit {
        withContext(Dispatchers.Default) {
            try {
                block(1.0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                System.gc()
                block(0.6)
            }
        }
    }

    /** Quick scan: the photo cut to the frame and pressed into the PDF quality, nothing else. */
    suspend fun raw(shot: ShotFile): PdfPage = guarded { k ->
        val side = (target.side * k).toInt()
        val c = shot.crop
        val long = if (c != null) Math.round(side / maxOf(c[2] - c[0], c[3] - c[1])) else side
        val img = BitmapLoader.load(shot.file, long)
        var out: Bitmap = img
        if (c != null && Math.abs(img.width.toFloat() / img.height - shot.vr) < 0.04f * shot.vr) {
            val x0 = (c[0] * img.width).toInt().coerceIn(0, img.width - 1)
            val y0 = (c[1] * img.height).toInt().coerceIn(0, img.height - 1)
            val x1 = (c[2] * img.width).toInt().coerceIn(x0 + 1, img.width)
            val y1 = (c[3] * img.height).toInt().coerceIn(y0 + 1, img.height)
            out = Bitmap.createBitmap(img, x0, y0, x1 - x0, y1 - y0)
            if (out !== img) img.recycle() // a crop of the whole picture is the same bitmap
        }
        finish(out, null, side, shot.gain, paper = true)
    }

    /** Quick scan, Auto mode: the sheet found in the photo ([quad]: TL, TR, BR, BL as 0..1 of the upright photo) straightened to a flat page of true proportions, nothing else. */
    suspend fun straightened(shot: ShotFile, quad: List<Offset>): PdfPage = guarded { k ->
        val side = (target.side * k).toInt()
        val ext = maxOf(quad.maxOf { it.x } - quad.minOf { it.x }, quad.maxOf { it.y } - quad.minOf { it.y }).coerceAtLeast(0.2f)
        val wside = minOf(side, WARP_MAX) // the straightening holds the photo, a copy and the result at once: bigger than this it does not fit in a phone
        val img = BitmapLoader.load(shot.file, Math.round(wside / ext))
        val out = PageWarp.warp(img, quad.map { PointF(it.x * img.width, it.y * img.height) }, wside)
        img.recycle()
        finish(out, null, side, shot.gain, paper = true)
    }

    /** Scan tab: an A4 sheet with the item at its real size. */
    suspend fun placed(item: PlacedItem): PdfPage = guarded { k ->
        SheetComposer.compose(item, minOf(WARP_MAX, (target.side * k).toInt()), target.budget)
    }

    /** An existing page edited: the frame (normalised corners, null = whole), turns (90 deg clockwise each), the look. [src] is not recycled. */
    suspend fun edited(src: Bitmap, quad: List<Offset>?, look: Look, turns: Int): PdfPage = guarded { k ->
        val side = (target.side * k).toInt()
        finish(transform(src, quad, look, turns, minOf(WARP_MAX, side)), src, side)
    }

    /** [gain] > 1: the shot was taken darker on purpose (a shorter shutter in the dark): the light is given back here. */
    private fun finish(bmp: Bitmap, keep: Bitmap?, side: Int, gain: Float = 1f, paper: Boolean = false): PdfPage {
        val g = if (paper) Enhance.whiteGain(bmp, gain) else gain // a camera shot: the paper is made white
        val lit = if (g > 1.05f) Enhance.lift(bmp, g) else bmp
        if (lit !== bmp && bmp !== keep) bmp.recycle()
        val enc = JpegCodec.encodeToBudget(lit, target.budget)
        if (lit !== keep) lit.recycle()
        return PdfPage(enc.jpeg, enc.w, enc.h)
    }

    companion object {
        private val gate = Semaphore(1)
        private const val WARP_MAX = 5200

        fun isFull(q: List<Offset>): Boolean {
            val full = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
            return q.indices.all { Math.abs(q[it].x - full[it].x) < 0.003f && Math.abs(q[it].y - full[it].y) < 0.003f }
        }

        /** The edited page as a bitmap: frame -> straighten, turns, look. Never recycles [src]. */
        fun transform(src: Bitmap, quad: List<Offset>?, look: Look, turns: Int, side: Int): Bitmap {
            var c: Bitmap = src
            fun swap(n: Bitmap) { if (c !== src && c !== n) c.recycle(); c = n }
            if (quad != null && !isFull(quad)) {
                swap(PageWarp.warp(src, quad.map { PointF(it.x * src.width, it.y * src.height) }, side))
            } else {
                swap(BitmapLoader.fit(src, side))
            }
            if (((turns % 4) + 4) % 4 != 0) swap(BitmapLoader.rotate(c, turns * 90))
            if (look != Look.Original) swap(Enhance.apply(c, look))
            return c
        }

        /** A small preview of the edited page. */
        fun preview(src: Bitmap, quad: List<Offset>?, look: Look, turns: Int, side: Int): Bitmap =
            transform(src, quad, look, turns, side).let { if (it === src) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }
}
