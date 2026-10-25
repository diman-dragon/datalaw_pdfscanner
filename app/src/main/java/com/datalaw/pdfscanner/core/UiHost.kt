package com.datalaw.pdfscanner.core

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

/** What the Save dialog returns: the file name and whether the text is recognised. */
class SaveAnswer(val name: String, val recognize: Boolean)

sealed interface Dlg {
    data class Choice(val title: UiText, val options: List<Pair<String, UiText>>, val result: CompletableDeferred<String?>) : Dlg
    data class Input(val title: UiText, val initial: String, val ok: UiText, val cancel: UiText, val result: CompletableDeferred<String?>) : Dlg
    data class Save(val title: UiText, val initial: String, val recognize: UiText, val plain: UiText, val cancel: UiText, val result: CompletableDeferred<SaveAnswer?>) : Dlg
    data class Confirm(val message: UiText, val ok: UiText, val cancel: UiText, val danger: Boolean, val result: CompletableDeferred<Boolean>) : Dlg
    data class Alert(val message: UiText, val result: CompletableDeferred<Unit>) : Dlg
}

/** Dialogs, toasts and the folder picker that controllers can await like plain functions. */
class UiHost {
    private val _dialog = MutableStateFlow<Dlg?>(null)
    val dialog: StateFlow<Dlg?> = _dialog.asStateFlow()

    private val _toasts = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val toasts: SharedFlow<UiText> = _toasts.asSharedFlow()

    private val _folderRequests = MutableSharedFlow<CompletableDeferred<Uri?>>(extraBufferCapacity = 1)
    val folderRequests: SharedFlow<CompletableDeferred<Uri?>> = _folderRequests.asSharedFlow()

    private val mutex = Mutex()

    fun toast(t: UiText) { _toasts.tryEmit(t) }

    private suspend fun <T> show(make: (CompletableDeferred<T>) -> Dlg): T = mutex.withLock {
        val d = CompletableDeferred<T>()
        _dialog.value = make(d)
        try { d.await() } finally { _dialog.value = null }
    }

    /** The id of the pressed option, null if dismissed. */
    suspend fun askChoice(title: UiText, options: List<Pair<String, UiText>>): String? =
        show { Dlg.Choice(title, options, it) }

    /** The entered text, null if cancelled. */
    suspend fun askText(title: UiText, initial: String, ok: UiText, cancel: UiText): String? =
        show { Dlg.Input(title, initial, ok, cancel, it) }

    /** The name and "recognize and save" / "save as is"; null if cancelled. */
    suspend fun askSave(title: UiText, initial: String, recognize: UiText, plain: UiText, cancel: UiText): SaveAnswer? =
        show { Dlg.Save(title, initial, recognize, plain, cancel, it) }

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
