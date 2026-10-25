package com.datalaw.pdfscanner

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.imaging.SheetFit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetFitTest {
    private fun quad(x0: Float, y0: Float, x1: Float, y1: Float) = listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1))

    @Test fun cornerOnTheBorderMeansCut() {
        assertTrue(SheetFit.touchesEdge(quad(0.005f, 0.2f, 0.8f, 0.8f)))
        assertFalse(SheetFit.touchesEdge(quad(0.2f, 0.2f, 0.8f, 0.8f)))
    }

    @Test fun rectangleIsPlausibleSliverIsNot() {
        assertTrue(SheetFit.plausible(quad(0.2f, 0.2f, 0.8f, 0.8f), 1f))
        assertFalse(SheetFit.plausible(quad(0.1f, 0.5f, 0.9f, 0.52f), 1f))
    }

    @Test fun boxAroundTheCorners() {
        val b = SheetFit.bbox(quad(0.2f, 0.3f, 0.8f, 0.9f))
        assertTrue(b[0] == 0.2f && b[1] == 0.3f && b[2] == 0.8f && b[3] == 0.9f)
    }
}
