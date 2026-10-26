package com.datalaw.pdfscanner.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** An opened PDF: page proportions up front, pages rendered on demand (PdfRenderer opens one page at a time, hence the mutex). */
class PdfReader private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** height / width of every page */
    val ratios: List<Float>,
) : Closeable {
    private val mutex = Mutex()
    private val lock = Any()
    private var rendering = false
    private var closeAsked = false
    private var closed = false
    val pageCount: Int get() = ratios.size

    /** The pages the viewer drew lately: scrolling back or zooming out shows them at once. Never recycled (a drawn bitmap may still be on the screen), the garbage collector takes them. */
    private val pageCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** A white-backed bitmap of the page, [width] px wide (the height is capped at [maxHeight]). [cache] = the caller never recycles it (the viewer), so it may be handed out again. */
    suspend fun render(index: Int, width: Int, maxHeight: Int = 4500, cache: Boolean = false): Bitmap {
        val key = "$index:$width:$maxHeight"
        if (cache) pageCache.get(key)?.let { return it }
        val bmp = mutex.withLock {
            withContext(Dispatchers.IO) {
                synchronized(lock) {
                    if (closeAsked) throw IOException("document closed")
                    rendering = true
                }
                try {
                    renderPage(renderer, index, width, maxHeight)
                } finally {
                    synchronized(lock) {
                        rendering = false
                        if (closeAsked) shut()
                    }
                }
            }
        }
        if (cache) pageCache.put(key, bmp)
        return bmp
    }

    /** A page that is being drawn is never cut off: the document is closed when that page is done (closing under the renderer leaves the file open and the drawing without its file). */
    override fun close() {
        synchronized(lock) {
            closeAsked = true
            if (!rendering) shut()
        }
    }

    private fun shut() {
        if (closed) return
        closed = true
        pageCache.evictAll()
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
