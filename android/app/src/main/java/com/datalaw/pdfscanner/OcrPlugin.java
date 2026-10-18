package com.datalaw.pdfscanner;

import android.graphics.Bitmap;
import android.net.Uri;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/**
 * Text recognition (Tesseract). Language data (tessdata_fast) is downloaded once into app storage;
 * pages are rendered from the PDF and recognized on a worker thread.
 */
@CapacitorPlugin(name = "Ocr")
public class OcrPlugin extends Plugin {
  private static final String BASE = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/";
  private static final int OCR_WIDTH = 2400;
  private final Object lock = new Object();
  private TessBaseAPI tess;
  private String tessLangs;

  private File dataDir() {
    return new File(getContext().getFilesDir(), "tess"); // holds tessdata/
  }

  private File langFile(String lang) {
    return new File(new File(dataDir(), "tessdata"), lang + ".traineddata");
  }

  private List<String> langs(PluginCall call) {
    try {
      JSONArray a = call.getArray("langs");
      if (a == null || a.length() == 0) return null;
      List<String> out = new ArrayList<>();
      for (int i = 0; i < a.length(); i++) {
        String l = a.getString(i);
        if (!l.matches("[a-z_]{2,12}")) return null; // also keeps the name path-safe
        out.add(l);
      }
      return out;
    } catch (Exception e) {
      return null;
    }
  }

  private void download(String lang) throws IOException {
    File target = langFile(lang);
    File dir = target.getParentFile();
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs");
    File tmp = new File(dir, lang + ".part");
    HttpURLConnection c = (HttpURLConnection) new URL(BASE + lang + ".traineddata").openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(30000);
    c.setInstanceFollowRedirects(true);
    try {
      if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
      try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
      }
    } finally {
      c.disconnect();
    }
    if (tmp.length() < 100 * 1024 || !tmp.renameTo(target)) {
      tmp.delete();
      throw new IOException("bad language file " + lang);
    }
  }

  /** Makes sure the language files exist (downloads the missing ones). */
  @PluginMethod
  public void ensure(PluginCall call) {
    final List<String> list = langs(call);
    if (list == null) {
      call.reject("langs required");
      return;
    }
    new Thread(() -> {
      try {
        for (String l : list) if (!langFile(l).exists()) download(l);
        call.resolve();
      } catch (Exception e) {
        call.reject("download failed: " + e.getMessage());
      }
    }).start();
  }

  /** Recognizes one page of a PDF: {uri, page, langs} -> {text}. */
  @PluginMethod
  public void recognize(PluginCall call) {
    final String u = call.getString("uri");
    final int page = call.getInt("page", 0);
    final List<String> list = langs(call);
    if (u == null || list == null) {
      call.reject("uri and langs required");
      return;
    }
    new Thread(() -> {
      try {
        Bitmap bmp = PdfPreview.renderPage(getContext().getContentResolver(), Uri.parse(u), page, OCR_WIDTH);
        String text;
        try {
          text = runOcr(bmp, list, false);
        } finally {
          bmp.recycle();
        }
        JSObject o = new JSObject();
        o.put("text", text == null ? "" : text);
        call.resolve(o);
      } catch (Exception e) {
        call.reject("ocr failed: " + e.getMessage());
      }
    }).start();
  }

  /** One engine for all pages; the language set decides whether it must be re-created. */
  private String runOcr(Bitmap bmp, List<String> list, boolean hocr) throws IOException {
    synchronized (lock) {
      String key = String.join("+", list);
      if (tess == null || !key.equals(tessLangs)) {
        if (tess != null) tess.recycle();
        tess = null;
        TessBaseAPI api = new TessBaseAPI();
        if (!api.init(dataDir().getAbsolutePath(), key)) {
          api.recycle();
          throw new IOException("init failed");
        }
        tess = api;
        tessLangs = key;
      }
      tess.setImage(bmp);
      String r = hocr ? tess.getHOCRText(0) : tess.getUTF8Text();
      return r == null ? "" : r;
    }
  }

  /** Recognizes a JPEG sent as base64: {data, langs} -> {hocr} (word boxes), for the searchable text layer of a saved PDF. */
  @PluginMethod
  public void recognizeImage(PluginCall call) {
    final String data = call.getString("data");
    final List<String> list = langs(call);
    if (data == null || list == null) {
      call.reject("data and langs required");
      return;
    }
    new Thread(
            () -> {
              try {
                byte[] b = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
                if (bmp == null) throw new IOException("bad image");
                String h;
                try {
                  h = runOcr(bmp, list, true);
                } finally {
                  bmp.recycle();
                }
                JSObject o = new JSObject();
                o.put("hocr", h);
                call.resolve(o);
              } catch (Exception e) {
                call.reject("ocr failed: " + e.getMessage());
              }
            })
        .start();
  }

  @PluginMethod
  public void release(PluginCall call) {
    releaseEngine();
    call.resolve();
  }

  private void releaseEngine() {
    synchronized (lock) {
      if (tess != null) {
        tess.recycle();
        tess = null;
        tessLangs = null;
      }
    }
  }

  @Override
  protected void handleOnDestroy() {
    releaseEngine();
  }
}
