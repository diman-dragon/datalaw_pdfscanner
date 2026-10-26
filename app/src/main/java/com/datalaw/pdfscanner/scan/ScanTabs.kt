package com.datalaw.pdfscanner.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.settings.CamMode
import com.datalaw.pdfscanner.ui.CenterColumn
import com.datalaw.pdfscanner.ui.PrimaryButton
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.Seg
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlinx.coroutines.launch

/** The shooting mode: Frame or Auto. */
@Composable
private fun ModeSwitch(mode: CamMode, onSelect: (CamMode) -> Unit) {
    Seg(listOf(CamMode.Frame to stringResource(R.string.mode_frame), CamMode.Auto to stringResource(R.string.mode_auto)), mode, onSelect)
}

@Composable
private fun ShootButton(onClick: () -> Unit) {
    PrimaryButton("📷  " + stringResource(R.string.scan_shoot), onClick, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), big = true)
}

/** QUICK SCAN: the mode and one big button; what it all means is in the (i) of the title bar. */
@Composable
fun QuickTab(vm: AppActions) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    CenterColumn {
        ModeSwitch(s.camMode) { m -> scope.launch { store.setCamMode(m) } }
        Spacer(Modifier.height(32.dp))
        ShootButton { vm.startQuick() }
    }
}

/**
 * SCAN: the same page as the quick one, with the sizes added. First the switch Frame / Auto; Auto is the full automatic scan (no size to choose, the camera
 * finds the item by itself), Frame shows the ready sizes under the switch; last the button.
 */
@Composable
fun ScanTab(vm: AppActions) {
    val s = LocalSettings.current
    val store = LocalGraph.current.settingsStore
    val scope = rememberCoroutineScope()
    CenterColumn {
        ModeSwitch(s.camMode) { m -> scope.launch { store.setCamMode(m) } }
        if (s.camMode == CamMode.Frame) {
            Spacer(Modifier.height(16.dp))
            TypeGrid(ScanTypes.preset(s.scanType).id) { id -> scope.launch { store.setScanType(id) } }
        }
        Spacer(Modifier.height(32.dp))
        ShootButton { vm.startScan() }
    }
}
