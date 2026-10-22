package com.datalaw.pdfscanner

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.getcapacitor.JSArray
import java.util.UUID

/** Opened PDFs for the in-app viewer: page proportions up front, pages rendered on demand. */
internal class PdfViewer {
  private data class Doc(
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer,
  )

  private val docs = HashMap<String, Doc>()

  /** Opens the document, fills ratios with height/width of every page, returns the session id. */
  @Synchronized
  @Throws(Exception::class)
  fun open(cr: ContentResolver, uri: Uri, ratios: JSArray): String {
    val pfd = cr.openFileDescriptor(uri, "r") ?: throw Exception("cannot open")
    var r: PdfRenderer? = null
    try {
      r = PdfRenderer(pfd)
      for (i in 0 until r.pageCount) {
        val p = r.openPage(i)
        try {
          ratios.put(p.height.toDouble() / p.width)
        } finally {
          p.close()
        }
      }
    } catch (e: Exception) {
      r?.close()
      pfd.close()
      throw e
    }
    val id = UUID.randomUUID().toString()
    docs[id] = Doc(pfd, r!!)
    return id
  }

  /** Renders one page to a JPEG data URL, width in pixels. */
  @Synchronized
  @Throws(Exception::class)
  fun page(id: String, index: Int, width: Int, quality: Int): String {
    val d = docs[id] ?: throw Exception("closed")
    var w = width.coerceIn(200, 3200)
    val page = d.renderer.openPage(index)
    try {
      var h = maxOf(1, Math.round(w * page.height.toFloat() / page.width))
      if (h > 4500) {
        w = maxOf(1, Math.round(w * 4500f / h))
        h = 4500
      }
      val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      bmp.eraseColor(Color.WHITE)
      page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      return PdfPreview.toDataUrl(bmp, quality.coerceIn(50, 100))
    } finally {
      page.close()
    }
  }

  @Synchronized
  fun close(id: String?) {
    if (id == null) return
    val d = docs.remove(id) ?: return
    try {
      d.renderer.close()
      d.pfd.close()
    } catch (_: Exception) {
    }
  }

  @Synchronized
  fun closeAll() {
    ArrayList(docs.keys).forEach { close(it) }
  }
}
