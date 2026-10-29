package com.datalaw.pdfscanner

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.docedit.ZoomMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    @Test fun thePointUnderTheFingerStaysWhereItIs() {
        val (s, o) = ZoomMath.zoomed(1f, Offset.Zero, 2f, Offset(100f, 100f), Offset.Zero, 200f, 200f)
        assertEquals(2f, s, 0.001f)
        assertEquals(-100f, o.x, 0.001f)
        assertEquals(-100f, o.y, 0.001f)
        // the page point (100, 100) is on the screen at 100 * 2 - 100 = 100: where the finger is
        assertEquals(100f, 100f * s + o.x, 0.001f)
    }

    @Test fun theEdgeOfThePageNeverComesIntoView() {
        val (_, o) = ZoomMath.zoomed(1f, Offset.Zero, 2f, Offset(200f, 200f), Offset.Zero, 200f, 200f)
        assertEquals(-200f, o.x, 0.001f) // the far corner of a 200 px page at scale 2
        val (_, o2) = ZoomMath.zoomed(1f, Offset.Zero, 2f, Offset(0f, 0f), Offset(50f, 50f), 200f, 200f)
        assertEquals(0f, o2.x, 0.001f)   // pulled to the right: stops at the left edge of the page
    }

    @Test fun zoomingOutGoesHome() {
        val (s, o) = ZoomMath.zoomed(2f, Offset(-100f, -100f), 1f, Offset(100f, 100f), Offset.Zero, 200f, 200f)
        assertEquals(1f, s, 0.001f)
        assertEquals(0f, o.x, 0.001f)
        assertEquals(0f, o.y, 0.001f)
    }

    @Test fun theZoomHasALimit() {
        val (s, _) = ZoomMath.zoomed(1f, Offset.Zero, 99f, Offset(10f, 10f), Offset.Zero, 200f, 200f)
        assertEquals(ZoomMath.MAX, s, 0.001f)
    }
}
