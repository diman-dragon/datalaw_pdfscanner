package com.datalaw.pdfscanner.scan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.settings.Settings
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚡", fontSize = 56.sp)
        Text(stringResource(R.string.quick_hint), Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Button({ vm.startQuick() }) { Text(stringResource(R.string.scan_shoot), fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        Text(targetText(s), Modifier.padding(top = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** SCAN: choose what is scanned (A4, A5, card, business card, passport, receipt) and shoot. */
@Composable
fun ScanTab(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    val type = ScanTypes.byId(s.scanType)
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.sc_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanTypes.all.forEach { t ->
                FilterChip(selected = t.id == type.id, onClick = { scope.launch { store.setScanType(t.id) } }, label = { Text(stringResource(t.nameRes)) })
            }
        }
        val size = "${type.mm.first.toString().removeSuffix(".0")} × ${type.mm.second.toString().removeSuffix(".0")} mm"
        val two = if (type.sides == 2) " · ${stringResource(R.string.sc_twosides)}" else ""
        val eg = type.examplesRes?.let { " · ${stringResource(it)}" } ?: ""
        Text("$size$two$eg", fontSize = 14.sp)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 56.sp)
            Button({ vm.startScan() }, Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.scan_shoot), fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        }
        Text(targetText(s), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
