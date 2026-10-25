package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.pdf.PdfPage
import com.datalaw.pdfscanner.pdf.PdfWord
import com.datalaw.pdfscanner.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfWriterTest {
    private fun build(withText: Boolean): String {
        val bos = ByteArrayOutputStream()
        val words = if (withText) listOf(PdfWord("Привет", 0.1f, 0.1f, 0.4f, 0.15f), PdfWord("world", 0.5f, 0.1f, 0.8f, 0.15f)) else emptyList()
        val page = PdfPage(ByteArray(10) { 1 }, 100, 140, words)
        runBlocking { PdfWriter.write(bos, 2) { page } }
        return bos.toByteArray().toString(Charsets.ISO_8859_1)
    }

    private fun checkXref(s: String) {
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
        assertTrue(used >= 2 + 2 + 2 * 2 - 2)
    }

    @Test fun xrefPointsToObjects() = checkXref(build(false))

    @Test fun xrefPointsToObjectsWithTextLayer() = checkXref(build(true))

    @Test fun textLayerHasUnicodeMap() {
        val s = build(true)
        assertTrue(s.contains("/ToUnicode"))
        assertTrue(s.contains("3 Tr"))
        assertTrue(s.contains("<01> <041f>")) // code 1 -> U+041F, Cyrillic capital Pe (first letter of "Привет")
    }
}
