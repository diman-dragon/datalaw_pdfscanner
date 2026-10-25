package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.camera.FrameMath
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameMathTest {
    @Test fun frameHasTheAskedShape() {
        val ratio = 210f / 297f
        val aspect = 3f / 4f
        val f = FrameMath.frame(ratio, aspect)
        assertEquals(ratio, (f[2] - f[0]) * aspect / (f[3] - f[1]), 0.001f)
    }

    @Test fun frameIsCentred() {
        val f = FrameMath.frame(210f / 297f, 3f / 4f)
        assertEquals(1f, f[0] + f[2], 0.001f)
        assertEquals(1f, f[1] + f[3], 0.001f)
    }

    @Test fun tallFrameTakesTheHeightWideFrameTakesTheWidth() {
        assertEquals(0.04f, FrameMath.frame(210f / 297f, 3f / 4f)[1], 0.001f)
        assertEquals(0.04f, FrameMath.frame(297f / 210f, 3f / 4f)[0], 0.001f)
    }
}
