package com.datalaw.pdfscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import com.datalaw.pdfscanner.pdf.PdfWord
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The languages of the OCR screen: the app code -> Tesseract data names (Serbian: Cyrillic + Latin). */
object OcrLangs {
    val all = listOf("ru" to listOf("rus"), "en" to listOf("eng"), "it" to listOf("ita"), "sr" to listOf("srp", "srp_latn"))
    fun tess(selected: Set<String>): List<String> = all.filter { it.first in selected }.flatMap { it.second }
}

/**
 * Where the language data comes from. The first address is a fixed release of tessdata_fast, the second the moving branch (tried only when the
 * release has no such file). [sha256] = the checksum of each file (lowercase hex): a language listed there is verified after the download.
 * Fill it in with `sha256sum rus.traineddata ...` of the release files; until then the files are only checked for size.
 */
object TessData {
    const val RELEASE = "4.1.0"
    fun urls(lang: String) = listOf(
        "https://github.com/tesseract-ocr/tessdata_fast/raw/$RELEASE/$lang.traineddata",
        "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$lang.traineddata",
    )
    val sha256: Map<String, String> = emptyMap()
}

/** Tesseract on the phone. Language data is downloaded once (the first use needs the internet) into the app's private folder. */
class OcrEngine(private val context: Context) {
    private val lock = Any()
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tess: TessBaseAPI? = null
    private var key: String? = null

    private fun dataRoot() = File(context.filesDir, "tess")
    private fun langFile(l: String) = File(File(dataRoot(), "tessdata"), "$l.traineddata")

    suspend fun ensure(langs: List<String>) = withContext(Dispatchers.IO) {
        File(dataRoot(), "tessdata").mkdirs()
        for (l in langs) {
            val f = langFile(l)
            if (f.exists() && f.length() > 100_000) continue
            val tmp = File(f.path + ".part")
            var last: IOException? = null
            val got = TessData.urls(l).any { url -> try { fetch(url, tmp); true } catch (e: IOException) { last = e; false } }
            if (!got) { tmp.delete(); throw last ?: IOException("download failed") }
            if (tmp.length() < 100_000) { tmp.delete(); throw IOException("language data too small") }
            TessData.sha256[l]?.let { want -> if (sha256(tmp) != want) { tmp.delete(); throw IOException("language data does not match its checksum") } }
            if (!tmp.renameTo(f)) throw IOException("cannot store language data")
        }
    }

    private fun fetch(url: String, to: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { i -> to.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val buf = ByteArray(64 * 1024); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(langs: List<String>): TessBaseAPI {
        val k = langs.joinToString("+")
        tess?.let { if (key == k) return it }
        tess?.recycle()
        val api = TessBaseAPI()
        if (!api.init(dataRoot().absolutePath, k)) { api.recycle(); tess = null; throw IOException("tesseract init failed") }
        tess = api
        key = k
        return api
    }

    suspend fun text(bmp: Bitmap, langs: List<String>): String = withContext(Dispatchers.Default) {
        synchronized(lock) {
            val api = open(langs)
            api.setImage(bmp)
            api.getUTF8Text() ?: ""
        }
    }

    /** Words with their boxes (0..1 of the page) for the invisible text layer of a searchable PDF. */
    suspend fun words(bmp: Bitmap, langs: List<String>): List<PdfWord> = withContext(Dispatchers.Default) {
        val hocr = synchronized(lock) {
            val api = open(langs)
            api.setImage(bmp)
            api.getHOCRText(0) ?: ""
        }
        parseHocr(hocr, bmp.width, bmp.height)
    }

    /** Never blocks the caller: a recognition in progress holds the lock. */
    fun releaseAsync() { cleanup.launch { release() } }

    fun release() = synchronized(lock) {
        tess?.recycle()
        tess = null
        key = null
    }

    companion object {
        private val wordRe = Regex("<span[^>]*class=['\"]ocrx_word['\"][^>]*title=['\"]([^'\"]*)['\"][^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        private val bboxRe = Regex("bbox (\\d+) (\\d+) (\\d+) (\\d+)")
        private val confRe = Regex("x_wconf (\\d+)")
        private val tagRe = Regex("<[^>]+>")

        private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

        fun parseHocr(hocr: String, w: Int, h: Int): List<PdfWord> {
            val out = ArrayList<PdfWord>()
            for (m in wordRe.findAll(hocr)) {
                val title = m.groupValues[1]
                val b = bboxRe.find(title) ?: continue
                val conf = confRe.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 100
                if (conf < 30) continue
                val t = unescape(m.groupValues[2].replace(tagRe, "")).trim()
                if (t.isEmpty()) continue
                val (x0, y0, x1, y1) = b.destructured
                out.add(PdfWord(t, x0.toFloat() / w, y0.toFloat() / h, x1.toFloat() / w, y1.toFloat() / h))
            }
            return out
        }
    }
}
