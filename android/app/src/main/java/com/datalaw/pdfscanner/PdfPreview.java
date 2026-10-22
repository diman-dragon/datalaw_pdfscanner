package com.datalaw.pdfscanner;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import java.io.ByteArrayOutputStream;

/** Renders PDF pages to bitmaps / JPEG data URLs (library thumbnails, viewer). */
final class PdfPreview {
  private PdfPreview() {}

  /** Encodes the bitmap as a JPEG data URL and recycles it. */
  static String toDataUrl(Bitmap bmp, int quality) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
    bmp.recycle();
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
  }

  /** First page as a JPEG data URL plus the page count of the document. */
  static final class First {
    final String data;
    final int pages;

    First(String data, int pages) {
      this.data = data;
      this.pages = pages;
    }
  }

  static First firstPage(ContentResolver cr, Uri uri, int width) throws Exception {
    ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
    if (pfd == null) throw new Exception("cannot open");
    PdfRenderer renderer = null;
    try {
      renderer = new PdfRenderer(pfd);
      int pages = renderer.getPageCount();
      return new First(toDataUrl(render(renderer, 0, width), 80), pages);
    } finally {
      if (renderer != null) renderer.close();
      pfd.close();
    }
  }

  /** Renders one page to a white-backed bitmap (caller recycles). Height is capped at 4200 px. */
  static Bitmap renderPage(ContentResolver cr, Uri uri, int index, int width) throws Exception {
    ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
    if (pfd == null) throw new Exception("cannot open");
    PdfRenderer renderer = null;
    try {
      renderer = new PdfRenderer(pfd);
      return render(renderer, index, width);
    } finally {
      if (renderer != null) renderer.close();
      pfd.close();
    }
  }

  private static Bitmap render(PdfRenderer renderer, int index, int width) {
    PdfRenderer.Page page = renderer.openPage(index);
    try {
      int w = width;
      int h = Math.max(1, Math.round(w * (float) page.getHeight() / page.getWidth()));
      if (h > 4200) {
        w = Math.max(1, Math.round(w * 4200f / h));
        h = 4200;
      }
      Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      bmp.eraseColor(Color.WHITE);
      page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      return bmp;
    } finally {
      page.close();
    }
  }
}
