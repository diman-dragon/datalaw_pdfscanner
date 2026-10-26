package com.datalaw.pdfscanner.viewer

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.core.Overlay
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.imaging.BitmapLoader
import com.datalaw.pdfscanner.pdf.PdfReader
import com.datalaw.pdfscanner.ui.BottomBar
import com.datalaw.pdfscanner.ui.CenterNote
import com.datalaw.pdfscanner.ui.GlyphButton
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.OverflowMenu
import com.datalaw.pdfscanner.ui.RotateButtons
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.TitleBar
import com.datalaw.pdfscanner.ui.ToolRow
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Loading : Loaded
    data object Failed : Loaded
    data class Ok(val reader: PdfReader) : Loaded
}

/** A request to zoom to [z] keeping the point ([fx], [fy]) of the screen where it is (the tap, the middle of two fingers, the middle of the screen). */
private class ZoomReq(val z: Float, val fx: Float, val fy: Float)

private const val MAX_ZOOM = 4f
private const val TAP_ZOOM = 2.5f

/** The viewer: strip or page by page, turn, zoom with a double tap or two fingers (at the place touched); the menu leads to text recognition and to the editor. */
@Composable
fun ViewerScreen(vm: AppActions, file: PdfFile) {
    val graph = LocalGraph.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val libVersion by vm.libVersion.collectAsStateWithLifecycle()
    BackHandler { vm.pop() }

    val loaded by produceState<Loaded>(Loaded.Loading, file.uri, libVersion) {
        val r = try { PdfReader.open(graph.app.contentResolver, Uri.parse(file.uri)) } catch (e: Exception) { null }
        value = if (r != null) Loaded.Ok(r) else Loaded.Failed
        awaitDispose { r?.close() }
    }

    var strip by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }    // the zoom on the screen
    var wanted by remember { mutableFloatStateOf(1f) }  // the zoom asked for; [zoom] follows it one request at a time
    var turns by remember { mutableIntStateOf(0) }      // the page is only turned to look at it, the file is not changed
    var page by remember { mutableIntStateOf(0) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val stripH = rememberScrollState()   // sideways scroll of the strip
    val pageH = rememberScrollState()    // sideways scroll of a page in the page mode
    val pageV = rememberScrollState()    // up-down scroll of a page in the page mode
    val reader = (loaded as? Loaded.Ok)?.reader
    val n = reader?.pageCount ?: 0
    val pager = rememberPagerState { maxOf(n, 1) }
    val zoomReqs = remember { Channel<ZoomReq>(Channel.CONFLATED) }
    val gapPx = with(density) { Dimens.gap.toPx() }

    fun ratioOf(i: Int): Float {
        val r = reader?.ratios?.getOrNull(i) ?: 1.4f
        return if (turns % 2 != 0) 1f / r else r
    }

    LaunchedEffect(strip, n) {
        if (n == 0) return@LaunchedEffect
        if (strip) listState.scrollToItem(page.coerceAtMost(n - 1)) else pager.scrollToPage(page.coerceAtMost(n - 1))
    }
    LaunchedEffect(strip, n) {
        if (n == 0) return@LaunchedEffect
        if (strip) snapshotFlow { listState.firstVisibleItemIndex }.collect { page = it }
        else snapshotFlow { pager.currentPage }.collect { page = it }
    }
    LaunchedEffect(pager.currentPage) { pageH.scrollTo(0); pageV.scrollTo(0) }

    // Zoom keeps the point under the finger where it is: the scroll is moved to where that point lands after the zoom. One request at a time, so the numbers always match the screen.
    LaunchedEffect(strip, n) {
        for (r in zoomReqs) {
            val z0 = zoom
            val z1 = r.z.coerceIn(1f, MAX_ZOOM)
            val w = box.width.toFloat()
            val h = box.height.toFloat()
            if (n == 0 || w <= 0f || h <= 0f || z1 == z0) { zoom = z1; continue }
            val k = z1 / z0
            if (strip) {
                fun hOf(i: Int, z: Float) = w * z * ratioOf(i)
                // the point of the strip under the finger: which page, and how far down it
                var y = r.fy + listState.firstVisibleItemScrollOffset
                for (j in 0 until listState.firstVisibleItemIndex) y += hOf(j, z0) + gapPx
                var p = 0
                var rest = y
                while (p < n - 1 && rest > hOf(p, z0)) { rest -= hOf(p, z0) + gapPx; p++ }
                val f = (rest / hOf(p, z0)).coerceIn(0f, 1f)
                // the same point after the zoom
                var ny = f * hOf(p, z1) - r.fy
                for (j in 0 until p) ny += hOf(j, z1) + gapPx
                var idx = 0
                while (idx < n - 1 && ny >= hOf(idx, z1) + gapPx) { ny -= hOf(idx, z1) + gapPx; idx++ }
                val x0 = stripH.value + r.fx
                zoom = z1
                listState.scrollToItem(idx, ny.roundToInt().coerceAtLeast(0))
                withFrameNanos { }
                stripH.scrollTo((x0 * k - r.fx).roundToInt().coerceAtLeast(0))
            } else {
                val pg = pager.currentPage.coerceIn(0, n - 1)
                val top0 = max(0f, (h - w * z0 * ratioOf(pg)) / 2)
                val top1 = max(0f, (h - w * z1 * ratioOf(pg)) / 2)
                val cy = pageV.value + r.fy - top0
                val cx = pageH.value + r.fx
                zoom = z1
                withFrameNanos { }
                pageV.scrollTo((cy * k - r.fy + top1).roundToInt().coerceAtLeast(0))
                pageH.scrollTo((cx * k - r.fx).roundToInt().coerceAtLeast(0))
            }
        }
    }

    fun askZoom(z: Float, fx: Float, fy: Float) {
        val z1 = z.coerceIn(1f, MAX_ZOOM)
        wanted = z1
        zoomReqs.trySend(ZoomReq(z1, fx, fy))
    }
    fun goPage(i: Int) { val t = i.coerceIn(0, maxOf(n - 1, 0)); scope.launch { if (strip) listState.animateScrollToItem(t) else pager.animateScrollToPage(t) } }

    val prevLabel = stringResource(R.string.vw_prev)
    val nextLabel = stringResource(R.string.vw_next)
    val zoomInLabel = stringResource(R.string.vw_zoomin)
    val zoomOutLabel = stringResource(R.string.vw_zoomout)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        TitleBar(file.name, onBack = { vm.pop() }) {
            if (n > 0) Text(stringResource(R.string.vw_page_n, page + 1, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OverflowMenu(stringResource(R.string.lib_menu)) { close ->
                DropdownMenuItem({ Text(stringResource(R.string.ocr_menu)) }, { close(); vm.push(Overlay.Ocr(file, page, n)) })
                DropdownMenuItem({ Text(stringResource(R.string.vw_editpage)) }, { close(); vm.push(Overlay.DocEdit(file, page)) })
                DropdownMenuItem({ Text(stringResource(R.string.vw_editdoc)) }, { close(); vm.push(Overlay.DocEdit(file)) })
                DropdownMenuItem({ Text(stringResource(R.string.lib_openext)) }, {
                    close()
                    try { ctx.startActivity(graph.folders.viewIntent(Uri.parse(file.uri))) } catch (e: ActivityNotFoundException) { vm.toast(R.string.lib_noviewer) }
                })
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (loaded) {
                Loaded.Loading -> Unit
                Loaded.Failed -> CenterNote(stringResource(R.string.vw_fail))
                is Loaded.Ok -> {
                    val rd = reader ?: return@Box
                    BoxWithConstraints(
                        Modifier.fillMaxSize()
                            .onSizeChanged { box = it }
                            .pinchZoom({ wanted }) { z, c -> askZoom(z, c.x, c.y) }
                            .doubleTapZoom { pt -> askZoom(if (wanted > 1.2f) 1f else TAP_ZOOM, pt.x, pt.y) },
                    ) {
                        val screenW = maxWidth
                        val baseW = box.width * 1.5f   // pages are drawn this wide at least: a zoom up to 1.5x needs no new drawing
                        if (strip) {
                            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                                items(rd.pageCount) { i ->
                                    Box(Modifier.fillMaxWidth().horizontalScroll(stripH)) { PdfPageView(rd, i, screenW * zoom, baseW, turns) }
                                }
                            }
                        } else {
                            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = zoom <= 1.01f) { i ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(Modifier.verticalScroll(pageV).horizontalScroll(pageH)) { PdfPageView(rd, i, screenW * zoom, baseW, turns) }
                                }
                            }
                        }
                    }
                }
            }
        }

        ToolRow {
            RotateButtons { d -> turns = ((turns + d) % 4 + 4) % 4 }
            Seg(listOf(true to stringResource(R.string.vw_strip), false to stringResource(R.string.vw_pages)), strip, { strip = it }, Modifier.weight(1f), fill = true)
        }
        BottomBar {
            GlyphButton("◀", prevLabel, { goPage(page - 1) }, Modifier.weight(1f))
            GlyphButton("−", zoomOutLabel, { askZoom(wanted / 1.25f, box.width / 2f, box.height / 2f) }, Modifier.weight(1f))
            GlyphButton("+", zoomInLabel, { askZoom(wanted * 1.25f, box.width / 2f, box.height / 2f) }, Modifier.weight(1f))
            GlyphButton("▶", nextLabel, { goPage(page + 1) }, Modifier.weight(1f))
        }
    }
}

/**
 * A page at the given width. The bitmap is drawn at least [base] px wide and then at steps of 400 px; while the zoom is still moving the old bitmap is simply
 * stretched by the GPU (nothing waits), and only the size it stops at is drawn anew.
 */
@Composable
private fun PdfPageView(reader: PdfReader, index: Int, width: Dp, base: Float, turns: Int) {
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

/** Two fingers change the zoom around the middle between them; one finger is left to the scrolling. */
private fun Modifier.pinchZoom(get: () -> Float, set: (Float, Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size >= 2) {
                val z = ev.calculateZoom()
                if (z != 1f) {
                    set(get() * z, ev.calculateCentroid())
                    ev.changes.forEach { it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}

/** A double tap zooms in at the place tapped, and the next one returns to the normal size. */
private fun Modifier.doubleTapZoom(onTap: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onDoubleTap = { onTap(it) })
}
