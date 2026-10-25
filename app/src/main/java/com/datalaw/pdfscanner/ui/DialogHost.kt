package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.Dlg
import com.datalaw.pdfscanner.core.SaveAnswer
import com.datalaw.pdfscanner.core.UiHost
import com.datalaw.pdfscanner.core.get
import kotlinx.coroutines.delay

/** Draws the dialog that a controller is waiting for: a centred card, wide buttons, the main one filled, the dangerous one red. */
@Composable
fun DialogHost(host: UiHost) {
    val d by host.dialog.collectAsStateWithLifecycle()
    when (val x = d) {
        null -> Unit
        is Dlg.Alert -> Card({ x.result.complete(Unit) }) {
            Text(x.message.get(), Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), lineHeight = 21.sp)
            Wide(true, false, stringResource(R.string.dlg_ok)) { x.result.complete(Unit) }
        }
        is Dlg.Confirm -> Card({ x.result.complete(false) }) {
            Text(x.message.get())
            Wide(true, x.danger, x.ok.get()) { x.result.complete(true) }
            Wide(false, false, x.cancel.get()) { x.result.complete(false) }
        }
        is Dlg.Choice -> Card({ x.result.complete(null) }) {
            Text(x.title.get())
            x.options.forEachIndexed { i, (id, label) ->
                Wide(i == 0, false, label.get()) { x.result.complete(id) }
            }
        }
        is Dlg.Input -> InputCard(x)
        is Dlg.Save -> SaveCard(x)
    }
}

@Composable
private fun Card(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun Wide(main: Boolean, danger: Boolean, text: String, onClick: () -> Unit) {
    if (main) {
        val colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()
        Button(onClick, Modifier.fillMaxWidth(), colors = colors) { Text(text) }
    } else {
        OutlinedButton(onClick, Modifier.fillMaxWidth()) { Text(text) }
    }
}

/** Asks for the keyboard once the dialog window has composed its field. A dialog is a window of its own and composes one frame later than the screen that shows it; a request that comes too early throws and would close the app. */
private suspend fun FocusRequester.requestWhenReady() {
    delay(100)
    try {
        requestFocus()
    } catch (e: Exception) {
        // the field is not there (yet): the dialog still works, the person taps the field
    }
}

@Composable
private fun InputCard(x: Dlg.Input) {
    var v by remember(x) { mutableStateOf(TextFieldValue(x.initial, TextRange(0, x.initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(x) { focus.requestWhenReady() }
    Card({ x.result.complete(null) }) {
        Text(x.title.get())
        OutlinedTextField(
            value = v,
            onValueChange = { v = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Wide(true, false, x.ok.get()) { x.result.complete(v.text) }
        Wide(false, false, x.cancel.get()) { x.result.complete(null) }
    }
}

@Composable
private fun SaveCard(x: Dlg.Save) {
    var v by remember(x) { mutableStateOf(TextFieldValue(x.initial, TextRange(0, x.initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(x) { focus.requestWhenReady() }
    Card({ x.result.complete(null) }) {
        Text(x.title.get())
        OutlinedTextField(
            value = v,
            onValueChange = { v = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Wide(true, false, x.recognize.get()) { x.result.complete(SaveAnswer(v.text, true)) }
        Wide(false, false, x.plain.get()) { x.result.complete(SaveAnswer(v.text, false)) }
        TextButton({ x.result.complete(null) }, Modifier.fillMaxWidth()) { Text(x.cancel.get()) }
    }
}
