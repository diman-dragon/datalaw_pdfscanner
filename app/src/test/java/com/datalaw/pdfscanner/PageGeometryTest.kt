package com.datalaw.pdfscanner

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.docedit.fitInto
import com.datalaw.pdfscanner.docedit.fromView
import com.datalaw.pdfscanner.docedit.toView
import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {
    @Test fun aQuarterTurnMovesTheCornerClockwise() {
        // the top-left corner of the page is the top-right corner of the picture turned once
        val v = toView(Offset(0f, 0f), 1)
        assertEquals(1f, v.x, 0.001f)
        assertEquals(0f, v.y, 0.001f)
    }

    @Test fun theWayBackGivesTheSamePoint() {
        val o = Offset(0.2f, 0.7f)
        for (k in 0..3) {
            val back = fromView(toView(o, k), k)
            assertEquals(o.x, back.x, 0.001f)
            assertEquals(o.y, back.y, 0.001f)
        }
    }

    @Test fun thePageKeepsAFreeMarginOnEverySide() {
        // a tall page in a wide view: the height is the limit, the page is centred
        val b = fitInto(1000f, 2000f, 1000f, 1500f, 100f)
        assertEquals(100f, b[0], 0.01f)
        assertEquals(800f, b[2], 0.01f)
        assertEquals(1200f, b[3], 0.01f)
        assertEquals(400f, b[1], 0.01f)
        // the width is the limit: the margin is still there, left and right
        val c = fitInto(1000f, 1000f, 2000f, 1000f, 100f)
        assertEquals(100f, c[0], 0.01f)
        assertEquals(800f, c[2], 0.01f)
    }
}
