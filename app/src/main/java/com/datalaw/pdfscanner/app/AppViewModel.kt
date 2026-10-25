package com.datalaw.pdfscanner.app

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.core.AppGraph
import com.datalaw.pdfscanner.core.CrashReport
import com.datalaw.pdfscanner.core.Deps
import com.datalaw.pdfscanner.core.FolderAccess
import com.datalaw.pdfscanner.core.Overlay
import com.datalaw.pdfscanner.core.Tab
import com.datalaw.pdfscanner.core.UiHost
import com.datalaw.pdfscanner.core.UiText
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.core.uiRaw
import com.datalaw.pdfscanner.data.FolderPath
import com.datalaw.pdfscanner.library.LibraryController
import com.datalaw.pdfscanner.scan.QuickSession
import com.datalaw.pdfscanner.scan.ScanSession
import com.datalaw.pdfscanner.scan.ScanTypes
import com.datalaw.pdfscanner.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Navigation state (tab + overlay stack) and the controllers behind the screens. */
class AppViewModel(val graph: AppGraph) : ViewModel(), AppActions {
    val ui: UiHost = graph.ui

    /** null until the settings are read from disk (the first frame shows nothing instead of a wrong state). */
    val settings: StateFlow<Settings?> = graph.settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tab = MutableStateFlow(Tab.Library)
    val tab: StateFlow<Tab> = _tab.asStateFlow()
    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays: StateFlow<List<Overlay>> = _overlays.asStateFlow()
    private val _libVersion = MutableStateFlow(0)
    override val libVersion: StateFlow<Int> = _libVersion.asStateFlow()

    override val deps = Deps(graph, viewModelScope, { settings.value ?: Settings() }, this)
    val library = LibraryController(deps)
    private val folder = FolderAccess(deps)

    init {
        // a crash is never silent: what ended the last run is told at this start
        CrashReport.pending(graph.app)?.let { r -> viewModelScope.launch { showCrash(r) } }
    }

    private suspend fun showCrash(r: CrashReport.Report) {
        if (ui.askConfirm(tx(R.string.crash_ask, tx(r.reason)), tx(R.string.crash_copy), tx(R.string.vw_close))) {
            CrashReport.copy(graph.app, r)
            ui.toast(tx(R.string.crash_copied))
        }
    }

    override fun goTo(tab: Tab) { _tab.value = tab }
    override fun push(o: Overlay) { _overlays.value = _overlays.value + o }
    override fun pop() { _overlays.value = _overlays.value.dropLast(1) }
    override fun libraryChanged() { _libVersion.value++; library.refresh() }

    override fun toast(@StringRes id: Int) = ui.toast(tx(id))

    /** The explanation of the scan tab that is open (kept off the screen so it does not clutter it), with the save place. */
    fun showHelp() {
        viewModelScope.launch {
            val p = (settings.value ?: Settings()).folderUri?.let { FolderPath.of(it) }
            val where: UiText = if (p == null) tx(R.string.scan_nofolder_label) else {
                val tail = p.path.split('/').filter { it.isNotEmpty() }.joinToString("") { " › $it" }
                tx(R.string.help_saveto, tx(if (p.primary) R.string.set_internal else R.string.set_volume_sd), uiRaw(tail))
            }
            ui.askAlert(tx(if (tab.value == Tab.Scan) R.string.help_scan else R.string.help_quick, where))
        }
    }

    override fun chooseFolder() { viewModelScope.launch { folder.choose() } }

    override fun startQuick() {
        viewModelScope.launch {
            if (folder.ensure()) push(Overlay.Camera(QuickSession(deps)))
        }
    }

    override fun startScan() {
        viewModelScope.launch {
            if (folder.ensure()) push(Overlay.Camera(ScanSession(deps, ScanTypes.byId((settings.value ?: Settings()).scanType))))
        }
    }

    companion object {
        fun factory(g: AppGraph): ViewModelProvider.Factory = viewModelFactory { initializer { AppViewModel(g) } }
    }
}
