package com.datalaw.pdfscanner.pdf

import java.io.OutputStream
import java.util.Locale

/**
 * Streaming PDF writer, page width = A4 (595.28 pt). Pages come one at a time, so only one page is in memory.
 * The words of a page become an invisible text layer (render mode 3, a Type3 font with a ToUnicode map: works for Cyrillic and Latin).
 */
object PdfWriter {
    private const val PAGE_W = 595.28

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun hex2(n: Int) = String.format(Locale.US, "%02x", n)
    private fun utf16hex(cp: Int): String {
        if (cp < 0x10000) return String.format(Locale.US, "%04x", cp)
        val v = cp - 0x10000
        return String.format(Locale.US, "%04x%04x", 0xd800 + (v shr 10), 0xdc00 + (v and 0x3ff))
    }

    suspend fun write(out: OutputStream, n: Int, getPage: suspend (Int) -> PdfPage) {
        var len = 0L
        val offs = HashMap<Int, Long>()
        fun putB(b: ByteArray) { out.write(b); len += b.size }
        fun putS(s: String) = putB(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(num: Int, dict: String, bytes: ByteArray) {
            offs[num] = len
            putS("$num 0 obj\n<< $dict /Length ${bytes.size} >>\nstream\n")
            putB(bytes)
            putS("\nendstream\nendobj\n")
        }

        // objects: 1 catalog, 2 pages, 3.. the pages, then 3 for the text font, then the rest as they come
        val font = 3 + n
        val toUni = font + 1
        val glyph = font + 2
        var next = 3 + n + 3
        val codes = LinkedHashMap<Int, Int>() // code point -> code 1..255
        val chars = ArrayList<Int>()
        fun codeOf(cp: Int): Int {
            codes[cp]?.let { return it }
            if (codes.size >= 255) return 0
            val c = codes.size + 1
            codes[cp] = c
            chars.add(cp)
            return c
        }

        putS("%PDF-1.4\n")
        offs[1] = len
        putS("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offs[2] = len
        putS("2 0 obj\n<< /Type /Pages /Count $n /Kids [${(0 until n).joinToString(" ") { "${3 + it} 0 R" }}] >>\nendobj\n")

        for (i in 0 until n) {
            val p = getPage(i)
            val ph = PAGE_W * p.h / p.w
            val cont = next++
            val im = next++
            obj(im, "/Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode", p.jpeg)
            val draw = "q ${f2(PAGE_W)} 0 0 ${f2(ph)} 0 0 cm /Im0 Do Q"
            val parts = ArrayList<String>()
            for (wd in p.words) {
                val bytes = ArrayList<Int>()
                for (cp in wd.t.codePoints().toArray()) {
                    val c = codeOf(cp)
                    if (c != 0) bytes.add(c)
                }
                if (bytes.isEmpty()) continue
                val hPt = (wd.y1 - wd.y0) * ph
                val wPt = (wd.x1 - wd.x0) * PAGE_W
                val fs = maxOf(2.0, hPt * 0.85)
                val tz = wPt / (bytes.size * fs) * 100
                parts.add("/F1 ${f2(fs)} Tf ${f1(tz)} Tz 1 0 0 1 ${f2(wd.x0 * PAGE_W)} ${f2(ph - wd.y1 * ph + hPt * 0.2)} Tm <${bytes.joinToString("") { hex2(it) }}> Tj")
            }
            val content = if (parts.isNotEmpty()) "$draw\nBT 3 Tr\n${parts.joinToString("\n")}\nET" else draw
            offs[3 + i] = len
            val fontRes = if (parts.isNotEmpty()) " /Font << /F1 $font 0 R >>" else ""
            putS("${3 + i} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f2(PAGE_W)} ${f2(ph)}] /Resources << /XObject << /Im0 $im 0 R >>$fontRes >> /Contents $cont 0 R >>\nendobj\n")
            obj(cont, "", content.toByteArray(Charsets.ISO_8859_1))
        }

        if (codes.isNotEmpty()) { // the text font draws nothing; only the code -> Unicode map lets viewers search and copy the text
            val cnt = chars.size
            obj(glyph, "", "1000 0 d0".toByteArray(Charsets.ISO_8859_1))
            offs[font] = len
            val procs = chars.indices.joinToString(" ") { "/g${it + 1} $glyph 0 R" }
            val diffs = chars.indices.joinToString(" ") { "/g${it + 1}" }
            val widths = chars.joinToString(" ") { "1000" }
            putS("$font 0 obj\n<< /Type /Font /Subtype /Type3 /FontBBox [0 0 1000 1000] /FontMatrix [0.001 0 0 0.001 0 0] /CharProcs << $procs >> /Encoding << /Type /Encoding /Differences [1 $diffs] >> /FirstChar 1 /LastChar $cnt /Widths [$widths] /Resources << >> /ToUnicode $toUni 0 R >>\nendobj\n")
            val map = StringBuilder()
            var s = 0
            while (s < cnt) {
                val part = chars.subList(s, minOf(cnt, s + 100))
                map.append("${part.size} beginbfchar\n")
                part.forEachIndexed { k, cp -> map.append("<${hex2(s + k + 1)}> <${utf16hex(cp)}>\n") }
                map.append("endbfchar\n")
                s += 100
            }
            val cmap = "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n1 begincodespacerange\n<00> <FF>\nendcodespacerange\n" +
                map + "endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend"
            obj(toUni, "", cmap.toByteArray(Charsets.ISO_8859_1))
        }

        val total = next
        val xref = len
        val x = StringBuilder("xref\n0 $total\n0000000000 65535 f \n")
        for (i in 1 until total) {
            val o = offs[i]
            x.append(if (o == null) "0000000000 65535 f \n" else String.format(Locale.US, "%010d 00000 n \n", o))
        }
        putS("${x}trailer\n<< /Size $total /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.flush()
    }
}
