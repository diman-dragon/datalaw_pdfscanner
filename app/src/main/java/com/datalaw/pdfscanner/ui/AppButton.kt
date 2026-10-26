package com.datalaw.pdfscanner.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/** The button of the main action (filled). The label is always ONE line: when it does not fit it is cut with "…", it is never broken into two lines. [big] = the large one of the scan tabs. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, big: Boolean = false) {
    Button(onClick, modifier, enabled = enabled) { ButtonLabel(text, big) }
}

/** The button of a side action (outlined); the label is one line like in [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier, enabled = enabled) { ButtonLabel(text, false) }
}

@Composable
private fun ButtonLabel(text: String, big: Boolean) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
}
