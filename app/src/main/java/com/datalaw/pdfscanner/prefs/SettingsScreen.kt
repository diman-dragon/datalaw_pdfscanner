package com.datalaw.pdfscanner.prefs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.BuildConfig
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.settings.AppLang
import com.datalaw.pdfscanner.settings.ThemeMode
import com.datalaw.pdfscanner.ui.AppCard
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.TRAIL_SEP
import com.datalaw.pdfscanner.ui.folderTrail
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlinx.coroutines.launch

/** One screen of cards, all built the same way: the title and the explanation on the left, the control under them across the whole width. */
@Composable
fun SettingsScreen(vm: AppActions) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FolderCard(vm)
        SettingCard(stringResource(R.string.set_pdfdpi), stringResource(R.string.set_pdfdpi_hint)) {
            Seg(listOf(0 to stringResource(R.string.set_auto), 150 to "150", 300 to "300", 600 to "600"), s.pdfDpi, { v -> scope.launch { store.setPdfDpi(v) } }, Modifier.fillMaxWidth(), fill = true)
        }
        SettingCard(stringResource(R.string.set_theme)) {
            Seg(listOf(ThemeMode.Auto to stringResource(R.string.set_auto), ThemeMode.Light to stringResource(R.string.set_light), ThemeMode.Dark to stringResource(R.string.set_dark)), s.theme, { v -> scope.launch { store.setTheme(v) } }, Modifier.fillMaxWidth(), fill = true)
        }
        SettingCard(stringResource(R.string.set_lang)) {
            Seg(listOf(AppLang.Auto to stringResource(R.string.set_auto), AppLang.En to "EN", AppLang.Ru to "RU"), s.lang, { v -> scope.launch { store.setLang(v) } }, Modifier.fillMaxWidth(), fill = true)
        }
        AboutCard()
    }
}

/** A setting: the title, an optional explanation, the control. */
@Composable
private fun SettingCard(title: String, hint: String? = null, control: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            control()
        }
    }
}

/** The save folder: its name, the way to it, and the button. */
@Composable
private fun FolderCard(vm: AppActions) {
    val s = LocalSettings.current
    val trail = folderTrail(s.folderUri)
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📁", fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_folder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trail?.last() ?: stringResource(R.string.set_nofolder), style = MaterialTheme.typography.titleMedium)
                if (trail != null && trail.size > 1) Text(trail.dropLast(1).joinToString(TRAIL_SEP), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Button({ vm.chooseFolder() }) { Text(stringResource(if (trail == null) R.string.set_folderpick else R.string.set_folderchange)) }
        }
    }
}

@Composable
private fun AboutCard() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.about_title).uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_name), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("${stringResource(R.string.app_name)} · ${stringResource(R.string.set_version)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_rights), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
