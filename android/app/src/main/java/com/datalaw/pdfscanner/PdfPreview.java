package com.datalaw.pdfscanner;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import java.io.ByteArrayOutputStream;

/** Renders the first page of a PDF to a JPEG data URL. */
final class PdfPreview {
  private PdfPreview() {}

  static String firstPage(ContentResolver cr, Uri uri, int width) throws Exception {
    ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
    if (pfd == null) throw new Exception("cannot open");
    PdfRenderer renderer = null;
    PdfRenderer.Page page = null;
    try {
      renderer = new PdfRenderer(pfd);
      page = renderer.openPage(0);
      int h = Math.max(1, Math.round(width * (float) page.getHeight() / page.getWidth()));
      Bitmap bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
      bmp.eraseColor(Color.WHITE);
      page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
      bmp.recycle();
      return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    } finally {
      if (page != null) page.close();
      if (renderer != null) renderer.close();
      pfd.close();
    }
  }
}
