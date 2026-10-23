package com.datalaw.pdfscanner.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.BuildConfig
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.FolderPath
import com.datalaw.pdfscanner.ui.AppViewModel
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.Seg
import kotlinx.coroutines.launch

/** Two swipeable screens: "PDF and files" and "App" (theme, language, camera focus, About). */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf(R.string.set_s_pdf, R.string.set_s_app).forEachIndexed { i, res ->
                val on = pager.currentPage == i
                Column(Modifier.weight(1f).clickable { scope.launch { pager.animateScrollToPage(i) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(res), Modifier.padding(vertical = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(thickness = 3.dp, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (i == 0) PdfPage(vm) else AppPage()
            }
        }
    }
}

@Composable
private fun RowItem(title: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        content()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun PdfPage(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    val path = s.folderUri?.let { FolderPath.of(it) }
    val place = when {
        path == null -> stringResource(R.string.set_nofolder)
        path.primary -> listOf(stringResource(R.string.set_internal), path.path).filter { it.isNotEmpty() }.joinToString("/")
        else -> listOf(stringResource(R.string.set_volume_sd), path.path).filter { it.isNotEmpty() }.joinToString("/")
    }
    RowItem(stringResource(R.string.set_folder)) { Button({ vm.chooseFolder() }) { Text(stringResource(R.string.set_folderpick)) } }
    Text(place, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    RowItem(stringResource(R.string.set_pdfdpi)) { Seg(listOf(150, 300, 600).map { it to "$it" }, s.pdfDpi, { v -> scope.launch { store.setPdfDpi(v) } }) }
    Text(stringResource(R.string.set_dpi_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
    RowItem(stringResource(R.string.set_pdfsearch)) {
        Seg(listOf(false to stringResource(R.string.set_off), true to stringResource(R.string.set_on)), s.pdfSearch, { v -> scope.launch { store.setPdfSearch(v) } })
    }
}

@Composable
private fun AppPage() {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    RowItem(stringResource(R.string.set_theme)) {
        Seg(listOf(ThemeMode.Auto to stringResource(R.string.set_auto), ThemeMode.Light to stringResource(R.string.set_light), ThemeMode.Dark to stringResource(R.string.set_dark)), s.theme, { v -> scope.launch { store.setTheme(v) } })
    }
    RowItem(stringResource(R.string.set_lang)) {
        Seg(listOf(AppLang.Auto to stringResource(R.string.set_auto), AppLang.En to "EN", AppLang.Ru to "RU"), s.lang, { v -> scope.launch { store.setLang(v) } })
    }
    RowItem(stringResource(R.string.set_focus)) {
        Seg(listOf(FocusMode.Smart to stringResource(R.string.set_focus_smart), FocusMode.System to stringResource(R.string.set_focus_system)), s.focus, { v -> scope.launch { store.setFocus(v) } })
    }
    Surface(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.about_title).uppercase(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_name), Modifier.padding(top = 8.dp, bottom = 4.dp), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("${stringResource(R.string.app_name)} · ${stringResource(R.string.set_version)} ${BuildConfig.VERSION_NAME}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_rights), Modifier.padding(top = 14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
