package com.datalaw.pdfscanner.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import com.datalaw.pdfscanner.ui.AppViewModel
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.TRAIL_SEP
import com.datalaw.pdfscanner.ui.folderTrail
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

/** One setting: the title, the control on the right, and an optional short explanation under it. */
@Composable
private fun RowItem(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (hint == null) 12.dp else 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        content()
    }
    if (hint != null) Text(hint, Modifier.padding(bottom = 12.dp), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/** The save folder as a card: the folder name big, the way to it small. */
@Composable
private fun FolderCard(vm: AppViewModel) {
    val s = LocalSettings.current
    val trail = folderTrail(s.folderUri)
    Surface(Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📁", fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_folder), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trail?.last() ?: stringResource(R.string.set_nofolder), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (trail != null && trail.size > 1) Text(trail.dropLast(1).joinToString(TRAIL_SEP), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Button({ vm.chooseFolder() }) { Text(stringResource(if (trail == null) R.string.set_folderpick else R.string.set_folderchange)) }
        }
    }
}

@Composable
private fun PdfPage(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    FolderCard(vm)
    RowItem(stringResource(R.string.set_pdfdpi), stringResource(R.string.set_pdfdpi_hint)) { Seg(listOf(150, 300, 600).map { it to "$it" }, s.pdfDpi, { v -> scope.launch { store.setPdfDpi(v) } }) }
    RowItem(stringResource(R.string.set_pdfsearch), stringResource(R.string.set_pdfsearch_hint)) {
        Seg(listOf(false to stringResource(R.string.set_off), true to stringResource(R.string.set_on)), s.pdfSearch, { v -> scope.launch { store.setPdfSearch(v) } })
    }
}

@Composable
private fun AppPage() {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    RowItem(stringResource(R.string.set_theme), stringResource(R.string.set_theme_hint)) {
        Seg(listOf(ThemeMode.Auto to stringResource(R.string.set_auto), ThemeMode.Light to stringResource(R.string.set_light), ThemeMode.Dark to stringResource(R.string.set_dark)), s.theme, { v -> scope.launch { store.setTheme(v) } })
    }
    RowItem(stringResource(R.string.set_lang), stringResource(R.string.set_lang_hint)) {
        Seg(listOf(AppLang.Auto to stringResource(R.string.set_auto), AppLang.En to "EN", AppLang.Ru to "RU"), s.lang, { v -> scope.launch { store.setLang(v) } })
    }
    RowItem(stringResource(R.string.set_focus), stringResource(R.string.set_focus_hint)) {
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
