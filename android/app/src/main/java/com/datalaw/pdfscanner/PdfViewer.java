package com.datalaw.pdfscanner;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.getcapacitor.JSArray;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Opened PDFs for the in-app viewer: page proportions up front, pages rendered on demand. */
final class PdfViewer {
  private static final class Doc {
    final ParcelFileDescriptor pfd;
    final PdfRenderer renderer;

    Doc(ParcelFileDescriptor pfd, PdfRenderer renderer) {
      this.pfd = pfd;
      this.renderer = renderer;
    }
  }

  private final Map<String, Doc> docs = new HashMap<>();

  /** Opens the document, fills ratios with height/width of every page, returns the session id. */
  synchronized String open(ContentResolver cr, Uri uri, JSArray ratios) throws Exception {
    ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
    if (pfd == null) throw new Exception("cannot open");
    PdfRenderer r = null;
    try {
      r = new PdfRenderer(pfd);
      for (int i = 0; i < r.getPageCount(); i++) {
        PdfRenderer.Page p = r.openPage(i);
        try {
          ratios.put(Double.valueOf((double) p.getHeight() / p.getWidth()));
        } finally {
          p.close();
        }
      }
    } catch (Exception e) {
      if (r != null) r.close();
      pfd.close();
      throw e;
    }
    String id = UUID.randomUUID().toString();
    docs.put(id, new Doc(pfd, r));
    return id;
  }

  /** Renders one page to a JPEG data URL, width in pixels. */
  synchronized String page(String id, int index, int width) throws Exception {
    Doc d = docs.get(id);
    if (d == null) throw new Exception("closed");
    int w = Math.max(200, Math.min(width, 2400));
    PdfRenderer.Page page = d.renderer.openPage(index);
    try {
      int h = Math.max(1, Math.round(w * (float) page.getHeight() / page.getWidth()));
      if (h > 4000) {
        w = Math.max(1, Math.round(w * 4000f / h));
        h = 4000;
      }
      Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      bmp.eraseColor(Color.WHITE);
      page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      return PdfPreview.toDataUrl(bmp, 85);
    } finally {
      page.close();
    }
  }

  synchronized void close(String id) {
    Doc d = docs.remove(id);
    if (d == null) return;
    try {
      d.renderer.close();
      d.pfd.close();
    } catch (Exception ignored) {
    }
  }

  synchronized void closeAll() {
    for (String id : new java.util.ArrayList<>(docs.keySet())) close(id);
  }
}
