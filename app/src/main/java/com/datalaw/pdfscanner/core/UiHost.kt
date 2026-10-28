package com.datalaw.pdfscanner.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Dlg {
    data class Choice(val title: UiText, val options: List<Pair<String, UiText>>, val result: CompletableDeferred<String?>) : Dlg
    data class Input(val title: UiText, val initial: String, val ok: UiText, val cancel: UiText, val result: CompletableDeferred<String?>) : Dlg
    data class Confirm(val message: UiText, val ok: UiText, val cancel: UiText, val danger: Boolean, val result: CompletableDeferred<Boolean>) : Dlg
    data class Alert(val message: UiText, val result: CompletableDeferred<Unit>) : Dlg
    /** The crash report: a title, what to do, the report itself, and the three buttons (copy, send, close). */
    data class Report(val title: UiText, val message: UiText, val report: String, val copy: UiText, val send: UiText, val close: UiText, val onCopy: () -> Unit, val onSend: (Context) -> Unit, val result: CompletableDeferred<Unit>) : Dlg
}

/** Dialogs, toasts and the folder picker that controllers can await like plain functions. */
class UiHost {
    private val _dialog = MutableStateFlow<Dlg?>(null)
    val dialog: StateFlow<Dlg?> = _dialog.asStateFlow()

    private val _toasts = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val toasts: SharedFlow<UiText> = _toasts.asSharedFlow()

    private val _folderRequests = MutableSharedFlow<CompletableDeferred<Uri?>>(extraBufferCapacity = 1)
    val folderRequests: SharedFlow<CompletableDeferred<Uri?>> = _folderRequests.asSharedFlow()

    private val _progress = MutableStateFlow<UiText?>(null)
    /** What is being saved in the background right now (the line with the progress bar at the top of the app), null = nothing. */
    val progress: StateFlow<UiText?> = _progress.asStateFlow()
    private var running = 0

    /** Called on the main thread when a background save starts; every [beginProgress] is closed by an [endProgress]. */
    fun beginProgress(t: UiText) { running++; _progress.value = t; Breadcrumbs.note("save begins") }
    fun setProgress(t: UiText) { _progress.value = t }
    fun endProgress() { running = (running - 1).coerceAtLeast(0); if (running == 0) _progress.value = null; Breadcrumbs.note("save ends") }

    private val mutex = Mutex()

    fun toast(t: UiText) { _toasts.tryEmit(t) }

    private suspend fun <T> show(make: (CompletableDeferred<T>) -> Dlg): T = mutex.withLock {
        val d = CompletableDeferred<T>()
        _dialog.value = make(d)
        Breadcrumbs.note("dialog " + (_dialog.value?.let { it::class.simpleName } ?: "?"))
        try { d.await() } finally { _dialog.value = null }
    }

    /** Shows the crash report until the person closes it. */
    suspend fun askReport(title: UiText, message: UiText, report: String, copy: UiText, send: UiText, close: UiText, onCopy: () -> Unit, onSend: (Context) -> Unit) {
        show<Unit> { Dlg.Report(title, message, report, copy, send, close, onCopy, onSend, it) }
    }

    /** The id of the pressed option, null if dismissed. */
    suspend fun askChoice(title: UiText, options: List<Pair<String, UiText>>): String? =
        show { Dlg.Choice(title, options, it) }

    /** The entered text, null if cancelled. */
    suspend fun askText(title: UiText, initial: String, ok: UiText, cancel: UiText): String? =
        show { Dlg.Input(title, initial, ok, cancel, it) }

    suspend fun askConfirm(message: UiText, ok: UiText, cancel: UiText, danger: Boolean = false): Boolean =
        show { Dlg.Confirm(message, ok, cancel, danger, it) }

    suspend fun askAlert(message: UiText) {
        show<Unit> { Dlg.Alert(message, it) }
    }

    /** Opens the system folder picker; null if cancelled. */
    suspend fun pickFolder(): Uri? {
        val d = CompletableDeferred<Uri?>()
        _folderRequests.emit(d)
        return d.await()
    }
}
