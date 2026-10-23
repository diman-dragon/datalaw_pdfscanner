package com.datalaw.pdfscanner.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.settings.CamMode
import com.datalaw.pdfscanner.settings.Settings
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.AppViewModel
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.TRAIL_SEP
import com.datalaw.pdfscanner.ui.folderTrail
import kotlinx.coroutines.launch

@Composable
private fun targetText(s: Settings): String {
    val trail = folderTrail(s.folderUri) ?: return stringResource(R.string.scan_nofolder_label)
    return stringResource(R.string.scan_to_n, trail.joinToString(TRAIL_SEP))
}

/** QUICK SCAN: nothing to choose, one big button. */
@Composable
fun QuickTab(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚡", fontSize = 56.sp)
        Text(stringResource(R.string.quick_title), Modifier.padding(top = 8.dp, bottom = 14.dp), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
            QuickFact("📐", stringResource(R.string.quick_f_frame))
            QuickFact("🎯", stringResource(R.string.quick_f_auto))
            QuickFact("⚠️", stringResource(R.string.quick_f_fit))
            QuickFact("📷", stringResource(R.string.quick_f_quality, s.pdfDpi))
        }
        ModeRow(s.camMode, stringResource(if (s.camMode == CamMode.Auto) R.string.quick_mode_auto_hint else R.string.quick_mode_frame_hint)) { m -> scope.launch { store.setCamMode(m) } }
        Spacer(Modifier.height(18.dp))
        Button({ vm.startQuick() }) { Text(stringResource(R.string.scan_shoot), fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        Text(targetText(s), Modifier.padding(top = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** SCAN: choose what is scanned (A4, A5, card, business card, passport, receipt) and the mode, then shoot. Everything fits one screen. */
@Composable
fun ScanTab(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    val type = ScanTypes.byId(s.scanType)
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TypeGrid(type.id) { id -> scope.launch { store.setScanType(id) } }
        val size = "${type.mm.first.toString().removeSuffix(".0")} × ${type.mm.second.toString().removeSuffix(".0")} mm"
        val two = if (type.sides == 2) " · ${stringResource(R.string.sc_twosides)}" else ""
        val eg = type.examplesRes?.let { " · ${stringResource(it)}" } ?: ""
        Text("$size$two$eg", Modifier.padding(top = 12.dp), fontSize = 14.sp, textAlign = TextAlign.Center)
        ModeRow(s.camMode, stringResource(if (s.camMode == CamMode.Auto) R.string.sc_mode_auto_hint else R.string.sc_mode_frame_hint)) { m -> scope.launch { store.setCamMode(m) } }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Button({ vm.startScan() }) { Text("📷  " + stringResource(R.string.scan_shoot), fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        }
        Text(stringResource(R.string.sc_hint), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(targetText(s), Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** "Shooting mode: [Frame | Auto]" and one line that says what the chosen mode does. */
@Composable
private fun ModeRow(mode: CamMode, hint: String, onSelect: (CamMode) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.mode_title), fontSize = 15.sp)
        Seg(listOf(CamMode.Frame to stringResource(R.string.mode_frame), CamMode.Auto to stringResource(R.string.mode_auto)), mode, onSelect)
    }
    Text(hint, Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
