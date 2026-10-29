package com.datalaw.pdfscanner.library

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.core.Overlay
import com.datalaw.pdfscanner.core.Tab
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.settings.LibView
import com.datalaw.pdfscanner.settings.Settings
import com.datalaw.pdfscanner.ui.MenuDivider
import com.datalaw.pdfscanner.ui.MenuRow
import com.datalaw.pdfscanner.ui.PrimaryButton
import com.datalaw.pdfscanner.ui.AppCard
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.OverflowMenu
import com.datalaw.pdfscanner.ui.theme.Dimens
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(vm: AppActions, lib: LibraryController) {
    val settings = LocalSettings.current
    val graph = LocalGraph.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by lib.state.collectAsStateWithLifecycle()
    LaunchedEffect(settings.folderUri) { lib.refresh() }

    Column(Modifier.fillMaxSize()) {
        if (state is LibState.Ok) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                ViewToggle(settings.libView) { v -> scope.launch { graph.settingsStore.setLibView(v) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                LibState.Loading -> Unit
                LibState.NoFolder -> Empty("📁", stringResource(R.string.lib_nofolder), stringResource(R.string.lib_nofolder_hint), stringResource(R.string.lib_choose)) { vm.goTo(Tab.Settings) }
                LibState.Error -> Empty("⚠️", stringResource(R.string.lib_error), stringResource(R.string.lib_error_hint), stringResource(R.string.lib_choose)) { vm.goTo(Tab.Settings) }
                is LibState.Ok -> if (s.files.isEmpty()) {
                    Empty("📄", stringResource(R.string.lib_empty), stringResource(R.string.lib_empty_hint), null) {}
                } else {
                    val onOpen: (PdfFile) -> Unit = { vm.push(Overlay.DocEdit(it)) }
                    val menu: @Composable (PdfFile) -> Unit = { f ->
                        FileMenu(f, onExternal = {
                            try { ctx.startActivity(graph.folders.viewIntent(Uri.parse(f.uri))) } catch (e: ActivityNotFoundException) { vm.toast(R.string.lib_noviewer) }
                        }, onRename = { lib.rename(f) }, onDelete = { lib.delete(f) })
                    }
                    if (settings.libView == LibView.List) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.screen, end = Dimens.screen, top = 4.dp, bottom = Dimens.screen), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                            items(s.files, key = { it.uri }) { f -> FileRow(f, onOpen, menu) }
                        }
                    } else {
                        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dimens.screen, end = Dimens.screen, top = 4.dp, bottom = Dimens.screen), horizontalArrangement = Arrangement.spacedBy(Dimens.gap), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                            items(s.files, key = { it.uri }) { f -> FileCard(f, onOpen, menu) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(icon: String, title: String, hint: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = Dimens.glyphBig)
        Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(hint, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) PrimaryButton(action, onAction, Modifier.padding(top = 20.dp))
    }
}

/** The date and the size of a file, apart (a cell shows them one under the other, a row in one line). */
@Composable
private fun metaParts(f: PdfFile): Pair<String, String> {
    val loc = LocalConfiguration.current.locales[0]
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, loc).format(Date(f.modified))
    val size = if (f.size >= 1_000_000) "%.1f MB".format(loc, f.size / 1_000_000.0) else "${(f.size / 1000).coerceAtLeast(1)} KB"
    return date to size
}

@Composable
private fun FileRow(f: PdfFile, onOpen: (PdfFile) -> Unit, menu: @Composable (PdfFile) -> Unit) {
    val (date, size) = metaParts(f)
    AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(f) }) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PdfThumbnail(f, Modifier.width(42.dp).height(56.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("$date · $size", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            menu(f)
        }
    }
}

@Composable
private fun FileCard(f: PdfFile, onOpen: (PdfFile) -> Unit, menu: @Composable (PdfFile) -> Unit) {
    val (date, size) = metaParts(f)
    AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(f) }) {
        Box {
            PdfThumbnail(f, Modifier.fillMaxWidth().aspectRatio(0.72f), rounded = false)
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))) { menu(f) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(date, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(size, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileMenu(f: PdfFile, onExternal: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    OverflowMenu(stringResource(R.string.lib_menu)) { close ->
        MenuRow("↗", stringResource(R.string.lib_openext)) { close(); onExternal() }
        MenuRow("✏️", stringResource(R.string.lib_rename)) { close(); onRename() }
        MenuDivider()
        MenuRow("🗑️", stringResource(R.string.lib_delete), danger = true) { close(); onDelete() }
    }
}
