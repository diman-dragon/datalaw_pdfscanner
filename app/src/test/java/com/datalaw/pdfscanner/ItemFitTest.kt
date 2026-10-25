package com.datalaw.pdfscanner

import com.datalaw.pdfscanner.scan.ItemFit
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemFitTest {
    @Test fun portraitPageFillsTheSheet() {
        val (w, h) = ItemFit.mm(210.0 / 297.0)
        assertEquals(210.0, w, 0.01); assertEquals(297.0, h, 0.01)
    }

    @Test fun landscapePageFillsTheSheetTurned() {
        val (w, h) = ItemFit.mm(297.0 / 210.0)
        assertEquals(297.0, w, 0.01); assertEquals(210.0, h, 0.01)
    }

    @Test fun narrowReceiptIsFittedByHeight() {
        val (w, h) = ItemFit.mm(0.4)
        assertEquals(110.8, w, 0.1); assertEquals(277.0, h, 0.01)
    }

    @Test fun cardKeepsItsShape() {
        val (w, h) = ItemFit.mm(1.586)
        assertEquals(277.0, w, 0.01); assertEquals(174.65, h, 0.1)
        assertEquals(1.586, w / h, 0.01)
    }
}
