package com.datalaw.pdfscanner.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.pdf.PdfReader
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A page at the given width. The bitmap is drawn at least [base] px wide and then at steps of 400 px; while the zoom is still moving the old bitmap is simply
 * stretched by the GPU (nothing waits), and only the size it stops at is drawn anew.
 */
@Composable
internal fun PdfPageView(reader: PdfReader, index: Int, width: Dp, base: Float, turns: Int) {
    val px = with(LocalDensity.current) { width.toPx() }
    val renderW = max(((px / 400f).toInt() + 1) * 400f, base).toInt().coerceAtMost(3200)
    val odd = turns % 2 != 0
    val ratio = reader.ratios[index]
    val bmp by produceState<Bitmap?>(null, reader, index, renderW, turns) {
        if (value != null) delay(250)
        val got = try {
            val w = if (odd) (renderW / ratio).toInt() else renderW
            val raw = reader.render(index, w, cache = true)
            if (turns % 4 == 0) raw else withContext(Dispatchers.Default) { BitmapLoader.rotate(raw, turns * 90) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        if (got != null) value = got
    }
    Box(Modifier.width(width).aspectRatio(if (odd) ratio else 1f / ratio).background(Color.White)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}
