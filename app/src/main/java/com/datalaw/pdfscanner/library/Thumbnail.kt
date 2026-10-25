package com.datalaw.pdfscanner.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.pdf.PdfReader
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.PageBadge
import com.datalaw.pdfscanner.ui.theme.Dimens

/** The first page of a PDF with the number of sheets in the corner (cached). */
@Composable
fun PdfThumbnail(file: PdfFile, modifier: Modifier = Modifier, widthPx: Int = 360, rounded: Boolean = true) {
    val graph = LocalGraph.current
    val key = "${file.uri}|${file.modified}|${file.size}"
    val thumb by produceState<Pair<Bitmap, Int>?>(graph.thumbs.get(key), key) {
        if (value == null) {
            value = try {
                PdfReader.firstPage(graph.app.contentResolver, Uri.parse(file.uri), widthPx).also { graph.thumbs.put(key, it) }
            } catch (e: Exception) {
                null
            }
        }
    }
    Box((if (rounded) modifier.clip(RoundedCornerShape(Dimens.thumb)) else modifier).background(MaterialTheme.colorScheme.surfaceVariant)) {
        thumb?.let { (bmp, pages) ->
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            PageBadge("$pages", Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}
