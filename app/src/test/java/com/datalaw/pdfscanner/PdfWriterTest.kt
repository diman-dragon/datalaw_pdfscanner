package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfWriterTest {
    private fun build(pages: Int): String {
        val bos = ByteArrayOutputStream()
        val page = PdfPage(ByteArray(10) { 1 }, 100, 140)
        runBlocking { PdfWriter.write(bos, pages) { page } }
        return bos.toByteArray().toString(Charsets.ISO_8859_1)
    }

    private fun checkXref(s: String, expectedObjects: Int) {
        assertTrue(s.startsWith("%PDF-1.4"))
        assertTrue(s.endsWith("%%EOF\n"))
        val sx = s.lastIndexOf("startxref")
        val xrefPos = s.substring(sx + "startxref".length).trim().lines().first().toInt()
        assertTrue(s.startsWith("xref", xrefPos))
        val lines = s.substring(xrefPos).lines()
        val count = lines[1].split(" ")[1].toInt()
        var used = 0
        for (k in 1 until count) {
            val e = lines[2 + k]
            assertEquals(20, e.length + 1)
            if (e.endsWith("n ")) {
                val off = e.substring(0, 10).toInt()
                assertTrue("object $k at $off", s.startsWith("$k 0 obj", off))
                used++
            }
        }
        assertEquals(expectedObjects, used)
    }

    // the catalog, the page tree, then for each page: the page, its content and its picture
    @Test fun xrefPointsToEveryObjectOfTwoPages() = checkXref(build(2), 2 + 2 * 3)

    @Test fun xrefPointsToEveryObjectOfOnePage() = checkXref(build(1), 2 + 3)
}
