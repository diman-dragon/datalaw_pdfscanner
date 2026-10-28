package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.pdf.PdfTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTargetTest {
    // A4 is 11.69 in long
    @Test fun dpiMeansTheLongSideOfA4() {
        assertEquals(1754, PdfTarget.forDpi(150).side)
        assertEquals(3508, PdfTarget.forDpi(300).side)
        assertEquals(7016, PdfTarget.forDpi(600).side)
    }

    @Test fun autoIsTheBestTheCameraGivesUpTo6000() {
        assertEquals(6000, PdfTarget.forDpi(0).side)
    }

    @Test fun aBiggerPageGetsABiggerBudget() {
        val sizes = listOf(150, 300, 0, 600).map { PdfTarget.forDpi(it).budget }
        assertTrue(sizes.zipWithNext().all { (a, b) -> a < b })
    }
}
