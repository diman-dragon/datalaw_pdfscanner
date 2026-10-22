package com.datalaw.pdfscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Text recognition (Tesseract). Language data (tessdata_fast) is downloaded once into app storage;
 * pages are rendered from the PDF and recognized on a worker thread.
 */
@CapacitorPlugin(name = "Ocr")
class OcrPlugin : Plugin() {
  companion object {
    private const val BASE = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
    private const val OCR_WIDTH = 2400
  }

  private val lock = Any()
  private var tess: TessBaseAPI? = null
  private var tessLangs: String? = null

  private fun dataDir(): File = File(context.filesDir, "tess") // holds tessdata/

  private fun langFile(lang: String): File = File(File(dataDir(), "tessdata"), "$lang.traineddata")

  private fun langs(call: PluginCall): List<String>? {
    return try {
      val a = call.getArray("langs") ?: return null
      if (a.length() == 0) return null
      val out = ArrayList<String>()
      for (i in 0 until a.length()) {
        val l = a.getString(i)
        if (!l.matches(Regex("[a-z_]{2,12}"))) return null // also keeps the name path-safe
        out.add(l)
      }
      out
    } catch (_: Exception) {
      null
    }
  }

  @Throws(IOException::class)
  private fun download(lang: String) {
    val target = langFile(lang)
    val dir = target.parentFile!!
    if (!dir.exists() && !dir.mkdirs()) throw IOException("mkdirs")
    val tmp = File(dir, "$lang.part")
    val c = URL("$BASE$lang.traineddata").openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 30000
    c.instanceFollowRedirects = true
    try {
      if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
      c.inputStream.use { input ->
        FileOutputStream(tmp).use { out ->
          val buf = ByteArray(64 * 1024)
          var n: Int
          while (input.read(buf).also { n = it } > 0) out.write(buf, 0, n)
        }
      }
    } finally {
      c.disconnect()
    }
    if (tmp.length() < 100 * 1024 || !tmp.renameTo(target)) {
      tmp.delete()
      throw IOException("bad language file $lang")
    }
  }

  /** Makes sure the language files exist (downloads the missing ones). */
  @PluginMethod
  fun ensure(call: PluginCall) {
    val list = langs(call)
    if (list == null) {
      call.reject("langs required")
      return
    }
    Thread {
      try {
        for (l in list) if (!langFile(l).exists()) download(l)
        call.resolve()
      } catch (e: Exception) {
        call.reject("download failed: ${e.message}")
      }
    }.start()
  }

  /** Recognizes one page of a PDF: {uri, page, langs} -> {text}. */
  @PluginMethod
  fun recognize(call: PluginCall) {
    val u = call.getString("uri")
    val page = call.getInt("page", 0)
    val list = langs(call)
    if (u == null || list == null) {
      call.reject("uri and langs required")
      return
    }
    Thread {
      try {
        val bmp = PdfPreview.renderPage(context.contentResolver, Uri.parse(u), page, OCR_WIDTH)
        val text = try {
          runOcr(bmp, list, false)
        } finally {
          bmp.recycle()
        }
        val o = JSObject()
        o.put("text", text ?: "")
        call.resolve(o)
      } catch (e: Exception) {
        call.reject("ocr failed: ${e.message}")
      }
    }.start()
  }

  /** One engine for all pages; the language set decides whether it must be re-created. */
  @Throws(IOException::class)
  private fun runOcr(bmp: Bitmap, list: List<String>, hocr: Boolean): String {
    synchronized(lock) {
      val key = list.joinToString("+")
      if (tess == null || key != tessLangs) {
        tess?.recycle()
        tess = null
        val api = TessBaseAPI()
        if (!api.init(dataDir().absolutePath, key)) {
          api.recycle()
          throw IOException("init failed")
        }
        tess = api
        tessLangs = key
      }
      tess!!.setImage(bmp)
      val r = if (hocr) tess!!.getHOCRText(0) else tess!!.getUTF8Text()
      return r ?: ""
    }
  }

  /** Recognizes a JPEG sent as base64: {data, langs} -> {hocr} (word boxes), for the searchable text layer of a saved PDF. */
  @PluginMethod
  fun recognizeImage(call: PluginCall) {
    val data = call.getString("data")
    val list = langs(call)
    if (data == null || list == null) {
      call.reject("data and langs required")
      return
    }
    Thread {
      try {
        val b = Base64.decode(data, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: throw IOException("bad image")
        val h = try {
          runOcr(bmp, list, true)
        } finally {
          bmp.recycle()
        }
        val o = JSObject()
        o.put("hocr", h)
        call.resolve(o)
      } catch (e: Exception) {
        call.reject("ocr failed: ${e.message}")
      }
    }.start()
  }

  @PluginMethod
  fun release(call: PluginCall) {
    releaseEngine()
    call.resolve()
  }

  private fun releaseEngine() {
    synchronized(lock) {
      tess?.recycle()
      tess = null
      tessLangs = null
    }
  }

  override fun handleOnDestroy() {
    releaseEngine()
  }
}
