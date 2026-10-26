package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.viewer.StripFocus
import org.junit.Assert.assertEquals
import org.junit.Test

class StripFocusTest {
    // three pages, 100 px high at zoom 1, 10 px between them
    private val h: (Int, Float) -> Float = { _, z -> 100f * z }

    @Test fun topOfTheScreenCountsThePagesAboveAndTheirGaps() {
        assertEquals(225f, StripFocus.top(2, 5, 1f, 10f, h), 0.01f)
    }

    @Test fun pointAtFindsThePageAndTheDistanceDownIt() {
        val (page, f) = StripFocus.pointAt(150f, 1f, 3, 10f, h)
        assertEquals(1, page)
        assertEquals(0.4f, f, 0.001f)
    }

    @Test fun zoomingKeepsThePointOfThePage() {
        assertEquals(290f, StripFocus.yOf(1, 0.4f, 2f, 10f, h), 0.01f)
        val (idx, off) = StripFocus.itemAt(290f, 2f, 3, 10f, h)
        assertEquals(1, idx)
        assertEquals(80f, off, 0.01f)
    }

    @Test fun aPositionAboveTheStripIsTheTopOfTheFirstPage() {
        val (idx, off) = StripFocus.itemAt(-30f, 1f, 3, 10f, h)
        assertEquals(0, idx)
        assertEquals(0f, off, 0.01f)
    }
}
