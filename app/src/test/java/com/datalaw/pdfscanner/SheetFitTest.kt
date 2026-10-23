package com.datalaw.pdfscanner

import androidx.compose.ui.geometry.Offset
import com.datalaw.pdfscanner.imaging.SheetFit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetFitTest {
    private fun quad(x0: Float, y0: Float, x1: Float, y1: Float) = listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1))
    private val frame = floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f)

    @Test fun sheetInsideTheFrameFits() = assertFalse(SheetFit.outside(quad(0.2f, 0.2f, 0.8f, 0.8f), frame))

    @Test fun sheetPastTheFrameDoesNotFit() = assertTrue(SheetFit.outside(quad(0.2f, 0.2f, 0.95f, 0.8f), frame))

    @Test fun cornerOnTheBorderMeansCut() {
        assertTrue(SheetFit.touchesEdge(quad(0.005f, 0.2f, 0.8f, 0.8f)))
        assertFalse(SheetFit.touchesEdge(quad(0.2f, 0.2f, 0.8f, 0.8f)))
    }

    @Test fun rectangleIsPlausibleSliverIsNot() {
        assertTrue(SheetFit.plausible(quad(0.2f, 0.2f, 0.8f, 0.8f), 1f))
        assertFalse(SheetFit.plausible(quad(0.1f, 0.5f, 0.9f, 0.52f), 1f))
    }
}
