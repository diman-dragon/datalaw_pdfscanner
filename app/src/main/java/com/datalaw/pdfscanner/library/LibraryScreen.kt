package com.datalaw.pdfscanner.library

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.settings.LibView
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.Overlay
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.Tab
import com.datalaw.pdfscanner.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val settings = LocalSettings.current
    val graph = LocalGraph.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.library.state.collectAsState()
    LaunchedEffect(settings.folderUri) { vm.library.refresh() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.library.add(uris)
    }

    Column(Modifier.fillMaxSize()) {
        if (state is LibState.Ok) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Seg(listOf(LibView.List to stringResource(R.string.lib_list), LibView.Cards to stringResource(R.string.lib_cards)), settings.libView, { v -> scope.launch { graph.settingsStore.setLibView(v) } })
                Button({ picker.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.lib_add)) }
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
                    val onOpen: (PdfFile) -> Unit = { vm.push(Overlay.Viewer(it)) }
                    val menu: @Composable (PdfFile) -> Unit = { f ->
                        FileMenu(f, onEdit = { vm.push(Overlay.DocEdit(f)) }, onExternal = {
                            try { ctx.startActivity(graph.folders.viewIntent(Uri.parse(f.uri))) } catch (e: ActivityNotFoundException) { vm.toast(R.string.lib_noviewer) }
                        }, onRename = { vm.library.rename(f) }, onDelete = { vm.library.delete(f) })
                    }
                    if (settings.libView == LibView.List) {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(s.files, key = { it.uri }) { f -> FileRow(f, onOpen, menu); HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
                        }
                    } else {
                        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 48.sp)
        Text(title, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
        Text(hint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (action != null) Button(onAction, Modifier.padding(top = 16.dp)) { Text(action) }
    }
}

@Composable
private fun meta(f: PdfFile): String {
    val loc = LocalConfiguration.current.locales[0]
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, loc).format(Date(f.modified))
    val size = if (f.size >= 1_000_000) "%.1f MB".format(loc, f.size / 1_000_000.0) else "${(f.size / 1000).coerceAtLeast(1)} KB"
    return "$date · $size"
}

@Composable
private fun FileRow(f: PdfFile, onOpen: (PdfFile) -> Unit, menu: @Composable (PdfFile) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PdfThumbnail(f, Modifier.width(56.dp).height(76.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(meta(f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        menu(f)
    }
}

@Composable
private fun FileCard(f: PdfFile, onOpen: (PdfFile) -> Unit, menu: @Composable (PdfFile) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(f) }) {
        PdfThumbnail(f, Modifier.fillMaxWidth().aspectRatio(0.72f), 480)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(meta(f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            menu(f)
        }
    }
}

@Composable
private fun FileMenu(f: PdfFile, onEdit: () -> Unit, onExternal: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Icon(Icons.Default.MoreVert, stringResource(R.string.lib_menu), Modifier.size(40.dp).clickable { expanded = true }.padding(8.dp))
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.lib_edit)) }, { expanded = false; onEdit() })
            DropdownMenuItem({ Text(stringResource(R.string.lib_openext)) }, { expanded = false; onExternal() })
            DropdownMenuItem({ Text(stringResource(R.string.lib_rename)) }, { expanded = false; onRename() })
            DropdownMenuItem({ Text(stringResource(R.string.lib_delete), color = MaterialTheme.colorScheme.error) }, { expanded = false; onDelete() })
        }
    }
}
