package com.datalaw.pdfscanner

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Renders PDF pages to bitmaps / JPEG data URLs (library thumbnails, viewer). */
internal object PdfPreview {

  /** Encodes the bitmap as a JPEG data URL and recycles it. */
  fun toDataUrl(bmp: Bitmap, quality: Int): String {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
    bmp.recycle()
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }

  /** First page as a JPEG data URL plus the page count of the document. */
  data class First(val data: String, val pages: Int)

  @Throws(Exception::class)
  fun firstPage(cr: ContentResolver, uri: Uri, width: Int): First {
    val pfd = cr.openFileDescriptor(uri, "r") ?: throw Exception("cannot open")
    var renderer: PdfRenderer? = null
    try {
      renderer = PdfRenderer(pfd)
      val pages = renderer.pageCount
      return First(toDataUrl(render(renderer, 0, width), 80), pages)
    } finally {
      renderer?.close()
      pfd.close()
    }
  }

  /** Renders one page to a white-backed bitmap (caller recycles). Height is capped at 4200 px. */
  @Throws(Exception::class)
  fun renderPage(cr: ContentResolver, uri: Uri, index: Int, width: Int): Bitmap {
    val pfd = cr.openFileDescriptor(uri, "r") ?: throw Exception("cannot open")
    var renderer: PdfRenderer? = null
    try {
      renderer = PdfRenderer(pfd)
      return render(renderer, index, width)
    } finally {
      renderer?.close()
      pfd.close()
    }
  }

  private fun render(renderer: PdfRenderer, index: Int, width: Int): Bitmap {
    val page = renderer.openPage(index)
    try {
      var w = width
      var h = maxOf(1, Math.round(w * page.height.toFloat() / page.width))
      if (h > 4200) {
        w = maxOf(1, Math.round(w * 4200f / h))
        h = 4200
      }
      val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      bmp.eraseColor(Color.WHITE)
      page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      return bmp
    } finally {
      page.close()
    }
  }
}
