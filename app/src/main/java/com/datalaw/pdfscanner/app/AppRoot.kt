package com.datalaw.pdfscanner.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.camera.CameraScreen
import com.datalaw.pdfscanner.core.Overlay
import com.datalaw.pdfscanner.core.Tab
import com.datalaw.pdfscanner.core.resolve
import com.datalaw.pdfscanner.docedit.DocEditScreen
import com.datalaw.pdfscanner.library.LibraryScreen
import com.datalaw.pdfscanner.prefs.SettingsScreen
import com.datalaw.pdfscanner.scan.QuickTab
import com.datalaw.pdfscanner.scan.ScanTab
import com.datalaw.pdfscanner.settings.Settings
import com.datalaw.pdfscanner.ui.DialogHost
import com.datalaw.pdfscanner.ui.InfoButton
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.TitleBar
import com.datalaw.pdfscanner.ui.WithLocale
import com.datalaw.pdfscanner.ui.theme.Dimens
import com.datalaw.pdfscanner.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    CompositionLocalProvider(LocalGraph provides vm.graph, LocalSettings provides s) {
        WithLocale(s.lang) { AppTheme(s.theme) { Content(vm) } }
    }
}

@Composable
private fun Content(vm: AppViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    // the collector lives as long as the screen: it must read the language CURRENT at the moment of the message, not the one of the start
    val ctxNow by rememberUpdatedState(ctx)
    LaunchedEffect(Unit) { vm.ui.toasts.collect { snack.showSnackbar(it.resolve(ctxNow)) } }

    // The system folder picker, asked for by controllers through UiHost.
    var pending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pending?.complete(uri)
        pending = null
    }
    LaunchedEffect(Unit) { vm.ui.folderRequests.collect { pending = it; pick.launch(null) } }

    BackHandler(enabled = overlays.isEmpty() && tab != Tab.Library) { vm.goTo(Tab.Library) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val title = when (tab) { Tab.Library -> R.string.tab_library; Tab.Quick -> R.string.tab_quick; Tab.Scan -> R.string.tab_scan; Tab.Settings -> R.string.tab_settings }
            TitleBar(stringResource(title)) {
                if (tab == Tab.Quick || tab == Tab.Scan) InfoButton(stringResource(R.string.help_label)) { vm.showHelp() }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Library -> LibraryScreen(vm, vm.library)
                    Tab.Quick -> QuickTab(vm)
                    Tab.Scan -> ScanTab(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(
                    Triple(Tab.Library, "📚", R.string.nav_library), Triple(Tab.Quick, "⚡", R.string.nav_quick),
                    Triple(Tab.Scan, "📷", R.string.nav_scan), Triple(Tab.Settings, "⚙️", R.string.nav_settings),
                ).forEach { (t, icon, label) ->
                    NavigationBarItem(selected = tab == t, onClick = { vm.goTo(t) }, icon = { Text(icon, fontSize = Dimens.glyph) }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1) })
                }
            }
        }

        overlays.forEach { o ->
            key(o) {
                // The camera is always black, so its text is always white, in any theme.
                CompositionLocalProvider(LocalContentColor provides if (o is Overlay.Camera) Color.White else MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize().background(if (o is Overlay.Camera) Color.Black else MaterialTheme.colorScheme.background)) {
                        when (o) {
                            is Overlay.DocEdit -> DocEditScreen(vm, o.file)
                            is Overlay.Camera -> CameraScreen(o.session) { vm.pop() }
                        }
                    }
                }
            }
        }

        // What is being saved in the background (a scan, an edited document): a line with a bar over everything, so the Library stays usable.
        val saving by vm.ui.progress.collectAsStateWithLifecycle()
        saving?.let { text ->
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                Text(text.resolve(ctx), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        DialogHost(vm.ui)
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
    }
}
