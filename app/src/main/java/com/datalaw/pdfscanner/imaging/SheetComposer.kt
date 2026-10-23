package com.datalaw.pdfscanner.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.datalaw.pdfscanner.camera.ShotFile
import com.datalaw.pdfscanner.pdf.PdfPage

/** One scanned thing: its shots (the sides of a card, the parts of a receipt), its real size in mm, whether the sheet turns it. */
class PlacedItem(val sides: List<ShotFile>, val mm: Pair<Double, Double>, val rotate: Boolean, val row: Boolean)

/** Puts an item on an A4 sheet at its REAL size (white sheet, thin outline); a full-page item (A4) fills the sheet. */
object SheetComposer {
    private const val SW = 210.0
    private const val SH = 297.0
    private const val GAP = 12.0
    private const val MARGIN = 10.0

    /** How many parts of a receipt of this width fit in a row on one sheet. */
    fun rowCap(w: Double): Int = Math.floor((SW - 2 * MARGIN + GAP) / (w + GAP)).toInt().coerceAtLeast(1)

    fun compose(item: PlacedItem, side: Int, budget: Int): PdfPage {
        val ppm = maxOf(side, 2339).toDouble() / SH
        val sheetW = Math.round(SW * ppm).toInt()
        val sheetH = Math.round(SH * ppm).toInt()
        val iw = if (item.rotate) item.mm.second else item.mm.first
        val ih = if (item.rotate) item.mm.first else item.mm.second
        val full = iw >= SW - 0.5 && ih >= SH - 0.5
        val n = item.sides.size
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(70, 0, 0, 0); strokeWidth = maxOf(1f, ppm.toFloat() * 0.2f) }

        val total = if (item.row) ih else n * ih + (n - 1) * GAP
        val y0 = if (full) 0.0 else maxOf(15.0, (SH - total) / 3)
        val x0 = if (item.row) (SW - (n * iw + (n - 1) * GAP)) / 2 else (SW - iw) / 2

        val dw = Math.round(item.mm.first * ppm).toInt()
        val dh = Math.round(item.mm.second * ppm).toInt()
        item.sides.forEachIndexed { i, s ->
            val c0 = s.crop?.let { maxOf(it[2] - it[0], it[3] - it[1]) } ?: 1f
            val img = BitmapLoader.load(s.file, minOf(5200, Math.round(maxOf(dw, dh) / c0)))
            val src = region(img, s, item.mm.first / item.mm.second)
            var piece = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            Canvas(piece).drawBitmap(img, src, Rect(0, 0, dw, dh), paint)
            img.recycle()
            if (item.rotate) {
                val r = BitmapLoader.rotate(piece, 90)
                piece.recycle()
                piece = r
            }
            val px = if (item.row) x0 + i * (iw + GAP) else x0
            val py = if (item.row) y0 else y0 + i * (ih + GAP)
            val dst = RectF((px * ppm).toFloat(), (py * ppm).toFloat(), ((px + iw) * ppm).toFloat(), ((py + ih) * ppm).toFloat())
            canvas.drawBitmap(piece, null, dst, paint)
            if (!full) canvas.drawRect(dst, line)
            piece.recycle()
        }
        val enc = JpegCodec.encodeToBudget(sheet, if (full) budget else budget * 2)
        sheet.recycle()
        return PdfPage(enc.jpeg, enc.w, enc.h)
    }

    /** The part of the upright photo that is the item: the frame of the camera, or a centred box of the item's shape. */
    private fun region(img: Bitmap, s: ShotFile, aspect: Double): Rect {
        val c = s.crop
        if (c != null && Math.abs(img.width.toDouble() / img.height - s.vr) < 0.04 * s.vr) {
            val x0 = (c[0] * img.width).toInt().coerceIn(0, img.width - 1)
            val y0 = (c[1] * img.height).toInt().coerceIn(0, img.height - 1)
            val x1 = (c[2] * img.width).toInt().coerceIn(x0 + 1, img.width)
            val y1 = (c[3] * img.height).toInt().coerceIn(y0 + 1, img.height)
            return Rect(x0, y0, x1, y1)
        }
        val w = minOf(img.width.toDouble(), img.height * aspect)
        val h = w / aspect
        val x = ((img.width - w) / 2).toInt()
        val y = ((img.height - h) / 2).toInt()
        return Rect(x, y, (x + w).toInt().coerceAtMost(img.width), (y + h).toInt().coerceAtMost(img.height))
    }
}
