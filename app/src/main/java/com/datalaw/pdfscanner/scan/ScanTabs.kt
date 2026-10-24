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

/** The shooting mode: Frame or Auto. */
@Composable
private fun ModeSwitch(mode: CamMode, onSelect: (CamMode) -> Unit) {
    Seg(listOf(CamMode.Frame to stringResource(R.string.mode_frame), CamMode.Auto to stringResource(R.string.mode_auto)), mode, onSelect)
}

@Composable
private fun ShootButton(onClick: () -> Unit) {
    Button(onClick) { Text("📷  " + stringResource(R.string.scan_shoot), fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
}

/** QUICK SCAN: the mode and one big button; what it all means is in the (i) of the title bar. */
@Composable
fun QuickTab(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ModeSwitch(s.camMode) { m -> scope.launch { store.setCamMode(m) } }
        Spacer(Modifier.height(32.dp))
        ShootButton { vm.startQuick() }
    }
}

/** SCAN: what is scanned (Full auto, A4, A5, card, business card, passport, receipt), the mode, and the button. Everything fits one screen. */
@Composable
fun ScanTab(vm: AppViewModel) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    val type = ScanTypes.byId(s.scanType)
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TypeGrid(type.id) { id -> scope.launch { store.setScanType(id) } }
        if (!type.free) {
            Spacer(Modifier.height(16.dp))
            ModeSwitch(s.camMode) { m -> scope.launch { store.setCamMode(m) } }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ShootButton { vm.startScan() }
        }
    }
}
