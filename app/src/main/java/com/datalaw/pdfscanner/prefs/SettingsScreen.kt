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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.BuildConfig
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.settings.AppLang
import com.datalaw.pdfscanner.settings.ThemeMode
import com.datalaw.pdfscanner.ui.PrimaryButton
import com.datalaw.pdfscanner.ui.AppCard
import com.datalaw.pdfscanner.ui.CycleRow
import com.datalaw.pdfscanner.ui.nextOf
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.TRAIL_SEP
import com.datalaw.pdfscanner.ui.folderTrail
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlinx.coroutines.launch

/** One screen of cards, all built the same way: the folder, then the settings that change by a tap on the card (the value is on the right), then About. */
@Composable
fun SettingsScreen(vm: AppActions) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    val auto = stringResource(R.string.set_auto)
    val dpis = listOf(0, 150, 300, 600)
    val themes = listOf(ThemeMode.Auto to auto, ThemeMode.Light to stringResource(R.string.set_light), ThemeMode.Dark to stringResource(R.string.set_dark))
    val langs = listOf(AppLang.Auto to auto, AppLang.En to "EN", AppLang.Ru to "RU")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.screen), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
        FolderCard(vm)
        CycleRow(stringResource(R.string.set_pdfdpi), if (s.pdfDpi == 0) auto else s.pdfDpi.toString(), { scope.launch { store.setPdfDpi(nextOf(dpis, s.pdfDpi)) } }, stringResource(R.string.set_pdfdpi_hint))
        CycleRow(stringResource(R.string.set_theme), themes.first { it.first == s.theme }.second, { scope.launch { store.setTheme(nextOf(themes.map { it.first }, s.theme)) } })
        CycleRow(stringResource(R.string.set_lang), langs.first { it.first == s.lang }.second, { scope.launch { store.setLang(nextOf(langs.map { it.first }, s.lang)) } })
        AboutCard()
    }
}

/** The save folder: its name, the way to it, and the button. */
@Composable
private fun FolderCard(vm: AppActions) {
    val s = LocalSettings.current
    val trail = folderTrail(s.folderUri)
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📁", fontSize = Dimens.glyphMid)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_folder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trail?.last() ?: stringResource(R.string.set_nofolder), style = MaterialTheme.typography.titleMedium)
                if (trail != null && trail.size > 1) Text(trail.dropLast(1).joinToString(TRAIL_SEP), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            PrimaryButton(stringResource(if (trail == null) R.string.set_folderpick else R.string.set_folderchange), { vm.chooseFolder() })
        }
    }
}

/** About: everything on the middle line. */
@Composable
private fun AboutCard() {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(Dimens.screen), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.about_title).uppercase(), style = MaterialTheme.typography.bodySmall, color = quiet, textAlign = TextAlign.Center)
            Text(stringResource(R.string.about_name), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("${stringResource(R.string.app_name)} · ${stringResource(R.string.set_version)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = quiet, textAlign = TextAlign.Center)
            Text(stringResource(R.string.about_rights), style = MaterialTheme.typography.bodySmall, color = quiet, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
