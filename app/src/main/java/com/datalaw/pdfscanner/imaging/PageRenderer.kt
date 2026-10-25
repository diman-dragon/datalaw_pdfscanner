package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.ocr.OcrEngine
import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.pdf.PdfTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Makes PDF pages. Heavy renders run strictly one at a time (phone memory); on a failure the page is made again smaller.
 * With [ocr] set, the recognised words become the invisible text layer of the page.
 */
class PageRenderer(
    private val target: PdfTarget,
    private val ocr: OcrEngine? = null,
    private val ocrLangs: List<String> = emptyList(),
) {
    private class Made(val enc: Encoded, val forOcr: Bitmap?)

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

    private fun page(m: Made) = PdfPage(m.enc.jpeg, m.enc.w, m.enc.h)

    private suspend fun withText(m: Made): PdfPage {
        val p = page(m)
        val b = m.forOcr ?: return p
        return try {
            val words = ocr!!.words(b, ocrLangs)
            if (words.isEmpty()) p else p.withWords(words)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // the text layer is a bonus: a failed recognition (memory, native library) never loses the scan itself
            android.util.Log.w("PdfScanner", "text layer skipped", e)
            p
        } finally {
            b.recycle()
        }
    }

    /** A finished page given its text layer (Scan makes the pages while the user shoots; the text is added when "Recognize and save" is chosen). */
    suspend fun recognized(p: PdfPage): PdfPage {
        val engine = ocr ?: return p
        return gate.withPermit {
            withContext(Dispatchers.Default) {
                var bmp: Bitmap? = null
                try {
                    bmp = BitmapFactory.decodeByteArray(p.jpeg, 0, p.jpeg.size) ?: return@withContext p
                    val forOcr = scaleForOcr(bmp)
                    val words = try { engine.words(forOcr, ocrLangs) } finally { forOcr.recycle() }
                    if (words.isEmpty()) p else p.withWords(words)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    p
                } catch (e: OutOfMemoryError) {
                    p
                } finally {
                    bmp?.recycle()
                }
            }
        }
    }

    /** Quick scan: the photo cut to the frame and pressed into the PDF quality, nothing else. */
    suspend fun raw(shot: ShotFile): PdfPage = withText(guarded { k ->
        val side = (target.side * k).toInt()
        val c = shot.crop
        val long = if (c != null) Math.round(minOf(4000f, side / maxOf(c[2] - c[0], c[3] - c[1]))) else minOf(4000, side)
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
        finish(out, null, side)
    })

    /** Quick scan, Auto mode: the sheet found in the photo ([quad]: TL, TR, BR, BL as 0..1 of the upright photo) straightened to a flat page of true proportions, nothing else. */
    suspend fun straightened(shot: ShotFile, quad: List<Offset>): PdfPage = withText(guarded { k ->
        val side = (target.side * k).toInt()
        val ext = maxOf(quad.maxOf { it.x } - quad.minOf { it.x }, quad.maxOf { it.y } - quad.minOf { it.y }).coerceAtLeast(0.2f)
        val img = BitmapLoader.load(shot.file, Math.round(minOf(4000f, side / ext)))
        val out = PageWarp.warp(img, quad.map { PointF(it.x * img.width, it.y * img.height) }, minOf(4000, side))
        img.recycle()
        finish(out, null, side)
    })

    /** Scan tab: an A4 sheet with the item at its real size. */
    suspend fun placed(item: PlacedItem): PdfPage = withText(guarded { k ->
        val p = SheetComposer.compose(item, (target.side * k).toInt(), target.budget)
        Made(Encoded(p.jpeg, p.w, p.h), null)
    })

    /** An existing page edited: the frame (normalised corners, null = whole), turns (90 deg clockwise each), the look. [src] is not recycled. */
    suspend fun edited(src: Bitmap, quad: List<Offset>?, look: Look, turns: Int): PdfPage = withText(guarded { k ->
        val side = (target.side * k).toInt()
        finish(transform(src, quad, look, turns, minOf(4000, side)), src, side)
    })

    private fun finish(bmp: Bitmap, keep: Bitmap?, side: Int): Made {
        val enc = JpegCodec.encodeToBudget(bmp, target.budget)
        val forOcr = if (ocr != null && wantText) scaleForOcr(bmp) else null
        if (bmp !== keep && bmp !== forOcr) bmp.recycle()
        return Made(enc, forOcr)
    }

    private val wantText = ocr != null && ocrLangs.isNotEmpty()

    private fun scaleForOcr(b: Bitmap): Bitmap {
        val long = maxOf(b.width, b.height)
        val k = (2480f / long).coerceIn(1f, 2f)
        return if (k > 1.02f) Bitmap.createScaledBitmap(b, Math.round(b.width * k), Math.round(b.height * k), true) else b.copy(Bitmap.Config.ARGB_8888, false)
    }

    companion object {
        private val gate = Semaphore(1)

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
            if (look == Look.Auto) swap(Enhance.auto(c))
            return c
        }

        /** A small preview of the edited page. */
        fun preview(src: Bitmap, quad: List<Offset>?, look: Look, turns: Int, side: Int): Bitmap =
            transform(src, quad, look, turns, side).let { if (it === src) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }
}
