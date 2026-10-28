package com.datalaw.pdfscanner.pdf

import java.io.OutputStream
import java.util.Locale

/** Streaming PDF writer, page width = A4 (595.28 pt). Pages come one at a time, so only one page is in memory. */
object PdfWriter {
    private const val PAGE_W = 595.28

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

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

        // objects: 1 catalog, 2 pages, 3.. the pages, then for every page its content and its picture as they come
        var next = 3 + n

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
            offs[3 + i] = len
            putS("${3 + i} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f2(PAGE_W)} ${f2(ph)}] /Resources << /XObject << /Im0 $im 0 R >> >> /Contents $cont 0 R >>\nendobj\n")
            obj(cont, "", draw.toByteArray(Charsets.ISO_8859_1))
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
