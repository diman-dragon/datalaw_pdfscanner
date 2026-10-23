package com.datalaw.pdfscanner.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/** An opened PDF: page proportions up front, pages rendered on demand (PdfRenderer opens one page at a time, hence the mutex). */
class PdfReader private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** height / width of every page */
    val ratios: List<Float>,
) : Closeable {
    private val mutex = Mutex()
    val pageCount: Int get() = ratios.size

    /** A white-backed bitmap of the page, [width] px wide (the height is capped at [maxHeight]). */
    suspend fun render(index: Int, width: Int, maxHeight: Int = 4500): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) { renderPage(renderer, index, width, maxHeight) }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        suspend fun open(cr: ContentResolver, uri: Uri): PdfReader = withContext(Dispatchers.IO) {
            val pfd = cr.openFileDescriptor(uri, "r") ?: throw IOException("cannot open")
            try {
                val r = PdfRenderer(pfd)
                val ratios = ArrayList<Float>(r.pageCount)
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { p -> ratios.add(p.height.toFloat() / p.width) }
                }
                PdfReader(pfd, r, ratios)
            } catch (e: Exception) {
                runCatching { pfd.close() }
                throw e
            }
        }

        /** The first page for a thumbnail and the number of pages. */
        suspend fun firstPage(cr: ContentResolver, uri: Uri, width: Int): Pair<Bitmap, Int> {
            val r = open(cr, uri)
            try {
                return r.render(0, width, 4200) to r.pageCount
            } finally {
                r.close()
            }
        }

        private fun renderPage(renderer: PdfRenderer, index: Int, width: Int, maxHeight: Int): Bitmap =
            renderer.openPage(index).use { page ->
                var w = width.coerceAtLeast(16)
                var h = Math.round(w * page.height.toFloat() / page.width).coerceAtLeast(1)
                if (h > maxHeight) {
                    w = Math.round(w * maxHeight.toFloat() / h).coerceAtLeast(1)
                    h = maxHeight
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
    }
}
