package com.datalaw.pdfscanner.docedit

import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.ui.SecondaryButton
import com.datalaw.pdfscanner.ui.PrimaryButton
import com.datalaw.pdfscanner.ui.AppCard
import com.datalaw.pdfscanner.ui.BottomBar
import com.datalaw.pdfscanner.ui.CenterNote
import com.datalaw.pdfscanner.ui.GlyphButton
import com.datalaw.pdfscanner.ui.OverflowMenu
import com.datalaw.pdfscanner.ui.PageBadge
import com.datalaw.pdfscanner.ui.TitleBar
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlinx.coroutines.launch

@Composable
fun DocEditScreen(vm: AppActions, file: PdfFile) {
    val ctl = remember { DocEditController(vm.deps, file) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { ctl.start(); onDispose { ctl.close() } }

    val idx = ctl.editing
    val pagesLayout = ctl.layoutPages
    // one page at a time: Back closes the document (the grid is a button away); the grid: Back from a page returns to it
    BackHandler {
        if (idx >= 0 && !pagesLayout) ctl.editing = -1 else ctl.requestClose()
    }
    // the page the person is on is remembered, so that the document opens on it next time
    LaunchedEffect(idx, pagesLayout) { if (pagesLayout && idx >= 0) ctl.rememberPage(idx) }

    if (idx in ctl.pages.indices) {
        val p = ctl.pages[idx]
        val last = !ctl.sequence || idx == ctl.pages.size - 1
        val nextLabel = when {
            pagesLayout -> R.string.sc_save
            !ctl.sequence -> R.string.ms_apply
            last -> R.string.ed_done
            else -> R.string.rs_next
        }
        PageEditor(
            ctl, p, idx + 1, ctl.pages.size,
            showBack = ctl.sequence && idx > 0,
            nextLabel = nextLabel,
            onBack = { ctl.editing = idx - 1 },
            onNext = {
                when {
                    pagesLayout -> ctl.save()
                    last -> { ctl.editing = -1; ctl.sequence = false }
                    else -> ctl.editing = idx + 1
                }
            },
            onDelete = { scope.launch { if (ctl.delete(idx)) { if (pagesLayout) ctl.editing = idx.coerceAtMost(ctl.pages.size - 1) else ctl.editing = -1 } } },
            onCancel = { if (pagesLayout) ctl.requestClose() else ctl.editing = -1 },
            onSwipe = { d -> if (idx + d in ctl.pages.indices) ctl.editing = idx + d },
            nextEnabled = !pagesLayout || (ctl.hasChanges && !ctl.busy),
            onGrid = if (pagesLayout) ({ ctl.showGrid() }) else null,
        )
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        TitleBar(file.name, onBack = { ctl.requestClose() }) {
            if (!ctl.loading && ctl.pages.isNotEmpty()) GlyphButton("▭", stringResource(R.string.de_layout_pages), { ctl.showPages() })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ctl.loading) {
                CenterNote(stringResource(if (ctl.busy) R.string.scan_saving else R.string.de_loading))
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(Dimens.screen),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gap), verticalArrangement = Arrangement.spacedBy(Dimens.gap),
                ) {
                    itemsIndexed(ctl.pages, key = { _, p -> p.index }) { i, p ->
                        PageCard(ctl, p, i, onEdit = { ctl.sequence = false; ctl.editing = i })
                    }
                }
            }
        }
        BottomBar {
            SecondaryButton(stringResource(R.string.de_all), { ctl.sequence = true; ctl.editing = 0 }, enabled = !ctl.loading && ctl.pages.isNotEmpty())
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.sc_save), { ctl.save() }, enabled = !ctl.loading && !ctl.busy)
        }
    }
}

@Composable
private fun PageCard(ctl: DocEditController, p: EditPage, i: Int, onEdit: () -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(p.quad, p.look, p.turns) { try { p.thumb = ctl.makeThumb(p) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
    AppCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            p.thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            PageBadge("${i + 1}", Modifier.align(Alignment.BottomStart).padding(6.dp))
            OverflowMenu(stringResource(R.string.lib_menu), Modifier.align(Alignment.TopEnd)) { close ->
                DropdownMenuItem({ Text(stringResource(R.string.sc_edit)) }, { close(); onEdit() })
                if (i > 0) DropdownMenuItem({ Text(stringResource(R.string.sc_left)) }, { close(); ctl.move(i, -1) })
                if (i < ctl.pages.size - 1) DropdownMenuItem({ Text(stringResource(R.string.sc_right)) }, { close(); ctl.move(i, 1) })
                if (ctl.pages.size > 1) DropdownMenuItem({ Text(stringResource(R.string.sc_delete), color = MaterialTheme.colorScheme.error) }, { close(); scope.launch { ctl.delete(i) } })
            }
        }
    }
}
