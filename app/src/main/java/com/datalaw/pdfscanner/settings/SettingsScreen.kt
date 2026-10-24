package com.datalaw.pdfscanner.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** One screen: the save folder, the image quality, theme, language, About. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FolderCard(vm)
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(stringResource(R.string.set_pdfdpi))
            Seg(listOf(0 to stringResource(R.string.set_auto), 150 to "150", 300 to "300", 600 to "600"), s.pdfDpi, { v -> scope.launch { store.setPdfDpi(v) } }, Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.set_pdfdpi_hint), Modifier.padding(top = 6.dp, bottom = 12.dp), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        RowItem(stringResource(R.string.set_theme)) {
            Seg(listOf(ThemeMode.Auto to stringResource(R.string.set_auto), ThemeMode.Light to stringResource(R.string.set_light), ThemeMode.Dark to stringResource(R.string.set_dark)), s.theme, { v -> scope.launch { store.setTheme(v) } })
        }
        RowItem(stringResource(R.string.set_lang)) {
            Seg(listOf(AppLang.Auto to stringResource(R.string.set_auto), AppLang.En to "EN", AppLang.Ru to "RU"), s.lang, { v -> scope.launch { store.setLang(v) } })
        }
        AboutCard()
    }
}

/** One setting: the title, the control on the right, and an optional short explanation under it. */
@Composable
private fun RowItem(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (hint == null) 12.dp else 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        content()
    }
    if (hint != null) Text(hint, Modifier.padding(bottom = 12.dp), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(stringResource(R.string.set_folder), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trail?.last() ?: stringResource(R.string.set_nofolder), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (trail != null && trail.size > 1) Text(trail.dropLast(1).joinToString(TRAIL_SEP), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Button({ vm.chooseFolder() }) { Text(stringResource(if (trail == null) R.string.set_folderpick else R.string.set_folderchange)) }
        }
    }
}

@Composable
private fun AboutCard() {
    Surface(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.about_title).uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_name), Modifier.padding(top = 8.dp, bottom = 4.dp), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("${stringResource(R.string.app_name)} · ${stringResource(R.string.set_version)} ${BuildConfig.VERSION_NAME}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_rights), Modifier.padding(top = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
