package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.Dlg
import com.datalaw.pdfscanner.core.UiHost
import com.datalaw.pdfscanner.core.get
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * Draws the dialog that a controller is waiting for. All of them look alike: a soft card, a round badge with a picture of what is asked (the colour says how
 * serious it is), the question, and wide buttons: the main one filled (red when it destroys something), the others quieter.
 */
@Composable
fun DialogHost(host: UiHost) {
    val d by host.dialog.collectAsStateWithLifecycle()
    val ctx = LocalContext.current // the language chosen in the app (a share window of the system would speak the language of the phone)
    when (val x = d) {
        null -> Unit
        is Dlg.Alert -> Card("ℹ️", Tone.Info, { x.result.complete(Unit) }) {
            Text(x.message.get(), Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
            Wide(Kind.Main, stringResource(R.string.dlg_ok)) { x.result.complete(Unit) }
        }
        is Dlg.Confirm -> Card(if (x.danger) "⚠️" else "❓", if (x.danger) Tone.Danger else Tone.Calm, { x.result.complete(false) }) {
            Text(x.message.get(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Wide(if (x.danger) Kind.Danger else Kind.Main, x.ok.get()) { x.result.complete(true) }
            Wide(Kind.Quiet, x.cancel.get()) { x.result.complete(false) }
        }
        is Dlg.Choice -> Card("📄", Tone.Calm, { x.result.complete(null) }) {
            Text(x.title.get(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            x.options.forEachIndexed { i, (id, label) ->
                Wide(if (i == 0) Kind.Main else Kind.Tonal, label.get()) { x.result.complete(id) }
            }
        }
        is Dlg.Report -> Card("🛠️", Tone.Danger, { x.result.complete(Unit) }) {
            Text(x.title.get(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(x.message.get(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Surface(shape = RoundedCornerShape(Dimens.control), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    x.report,
                    Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(10.dp),
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                )
            }
            Wide(Kind.Main, x.copy.get()) { x.onCopy() }
            Wide(Kind.Tonal, x.send.get()) { x.onSend(ctx) }
            Wide(Kind.Quiet, x.close.get()) { x.result.complete(Unit) }
        }
        is Dlg.Input -> InputCard(x)
    }
}

/** How serious a dialog is: the colour of its badge. */
private enum class Tone { Calm, Info, Danger }

/** What a wide button means: Main = the answer wanted, Danger = the same but it destroys something, Tonal = another good answer, Quiet = leave it. */
private enum class Kind { Main, Danger, Tonal, Quiet }

@Composable
private fun Card(icon: String, tone: Tone, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val bridge = rememberLocaleBridge() // taken HERE: inside the Dialog the language of the app is already lost
    Dialog(onDismissRequest = onDismiss) {
        bridge.Provide {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Badge(icon, tone)
                    content()
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: String, tone: Tone) {
    val scheme = MaterialTheme.colorScheme
    val bg = when (tone) {
        Tone.Calm -> scheme.primaryContainer
        Tone.Info -> scheme.secondaryContainer
        Tone.Danger -> scheme.errorContainer
    }
    Box(Modifier.size(56.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Text(icon, fontSize = Dimens.glyphMid) }
}

@Composable
private fun Wide(kind: Kind, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val m = Modifier.fillMaxWidth().height(50.dp)
    val scheme = MaterialTheme.colorScheme
    when (kind) {
        Kind.Main -> Button(onClick, m, shape = shape) { Label(text) }
        Kind.Danger -> Button(onClick, m, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)) { Label(text) }
        Kind.Tonal -> FilledTonalButton(onClick, m, shape = shape) { Label(text) }
        Kind.Quiet -> TextButton(onClick, m, shape = shape) { Label(text) }
    }
}

@Composable
private fun Label(text: String) = Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)

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
    Card("✏️", Tone.Calm, { x.result.complete(null) }) {
        Text(x.title.get(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = v,
            onValueChange = { v = it },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Wide(Kind.Main, x.ok.get()) { x.result.complete(v.text) }
        Wide(Kind.Quiet, x.cancel.get()) { x.result.complete(null) }
    }
}
