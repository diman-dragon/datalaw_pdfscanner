package com.datalaw.pdfscanner.viewer

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.pdf.PdfReader
import com.datalaw.pdfscanner.ui.AppViewModel
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.Overlay
import kotlinx.coroutines.launch

private sealed interface Loaded {
    data object Loading : Loaded
    data object Failed : Loaded
    data class Ok(val reader: PdfReader) : Loaded
}

/** The viewer: strip or page by page, pinch / double-tap zoom; the menu leads to text recognition and to the editor. */
@Composable
fun ViewerScreen(vm: AppViewModel, file: PdfFile) {
    val graph = LocalGraph.current
    val ctx = LocalContext.current
    val libVersion by vm.libVersion.collectAsState()
    BackHandler { vm.pop() }

    val loaded by produceState<Loaded>(Loaded.Loading, file.uri, libVersion) {
        val r = try { PdfReader.open(graph.app.contentResolver, Uri.parse(file.uri)) } catch (e: Exception) { null }
        value = if (r != null) Loaded.Ok(r) else Loaded.Failed
        awaitDispose { r?.close() }
    }

    var strip by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var page by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val n = (loaded as? Loaded.Ok)?.reader?.pageCount ?: 0
    val pager = rememberPagerState { maxOf(n, 1) }

    LaunchedEffect(strip, n) {
        if (n == 0) return@LaunchedEffect
        if (strip) listState.scrollToItem(page.coerceAtMost(n - 1)) else pager.scrollToPage(page.coerceAtMost(n - 1))
    }
    LaunchedEffect(strip, n) {
        if (n == 0) return@LaunchedEffect
        if (strip) snapshotFlow { listState.firstVisibleItemIndex }.collect { page = it }
        else snapshotFlow { pager.currentPage }.collect { page = it }
    }
    fun goPage(i: Int) { val t = i.coerceIn(0, maxOf(n - 1, 0)); scope.launch { if (strip) listState.animateScrollToItem(t) else pager.animateScrollToPage(t) } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✕", fontSize = 22.sp, modifier = Modifier.clickable { vm.pop() }.padding(10.dp))
            Text(file.name, Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            if (n > 0) Text(stringResource(R.string.vw_page_n, page + 1, n), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                Text("⋮", fontSize = 22.sp, modifier = Modifier.clickable { menu = true }.padding(10.dp))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.vw_mode)) }, { menu = false; strip = !strip })
                    DropdownMenuItem({ Text(stringResource(R.string.ocr_menu)) }, { menu = false; vm.push(Overlay.Ocr(file, page, n)) })
                    DropdownMenuItem({ Text(stringResource(R.string.vw_editpage)) }, { menu = false; vm.push(Overlay.DocEdit(file, page)) })
                    DropdownMenuItem({ Text(stringResource(R.string.vw_editdoc)) }, { menu = false; vm.push(Overlay.DocEdit(file)) })
                    DropdownMenuItem({ Text(stringResource(R.string.lib_openext)) }, {
                        menu = false
                        try { ctx.startActivity(graph.folders.viewIntent(Uri.parse(file.uri))) } catch (e: ActivityNotFoundException) { vm.toast(R.string.lib_noviewer) }
                    })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val l = loaded) {
                Loaded.Loading -> Unit
                Loaded.Failed -> Text(stringResource(R.string.vw_fail), Modifier.align(Alignment.Center))
                is Loaded.Ok -> {
                    val reader = l.reader
                    BoxWithConstraints(Modifier.fillMaxSize().pinchZoom({ zoom }, { zoom = it }).doubleTapZoom({ zoom }, { zoom = it })) {
                        val screenW = maxWidth
                        if (strip) {
                            val hs = rememberScrollState()
                            LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(reader.pageCount) { i ->
                                    Box(Modifier.fillMaxWidth().horizontalScroll(hs)) { PdfPageView(reader, i, screenW * zoom) }
                                }
                            }
                        } else {
                            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = zoom <= 1.01f) { i ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) { PdfPageView(reader, i, screenW * zoom) }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            for ((label, action) in listOf<Pair<String, () -> Unit>>(
                "◀" to { goPage(page - 1) }, "−" to { zoom = (zoom / 1.25f).coerceIn(1f, 4f) },
                "+" to { zoom = (zoom * 1.25f).coerceIn(1f, 4f) }, "▶" to { goPage(page + 1) },
            )) {
                Text(label, fontSize = 22.sp, modifier = Modifier.clickable(onClick = action).padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }
}

/** A page at the given width; the bitmap is rendered on demand at a width rounded up to 200 px (so zooming does not re-render every pixel). */
@Composable
private fun PdfPageView(reader: PdfReader, index: Int, width: Dp) {
    val px = with(LocalDensity.current) { width.toPx() }
    val renderW = (((px / 200f).toInt() + 1) * 200).coerceAtMost(3200)
    val bmp by produceState<Bitmap?>(null, reader, index, renderW) {
        value = try { reader.render(index, renderW) } catch (e: Exception) { null }
    }
    Box(Modifier.width(width).aspectRatio(1f / reader.ratios[index]).background(Color.White)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

/** Two fingers change the zoom; one finger is left to the scrolling. */
private fun Modifier.pinchZoom(get: () -> Float, set: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size >= 2) {
                val z = ev.calculateZoom()
                if (z != 1f) {
                    set((get() * z).coerceIn(1f, 4f))
                    ev.changes.forEach { it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}

private fun Modifier.doubleTapZoom(get: () -> Float, set: (Float) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onDoubleTap = { set(if (get() > 1.2f) 1f else 2f) })
}
