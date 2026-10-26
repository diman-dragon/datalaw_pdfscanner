package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.datalaw.pdfscanner.ui.theme.Dimens

/** A setting with a few values: the name (and an explanation) on the left, the current value on the right; a tap on the card moves to the next value. */
@Composable
fun CycleRow(title: String, value: String, onNext: () -> Unit, hint: String? = null) {
    AppCard(Modifier.fillMaxWidth(), onClick = onNext) {
        Row(Modifier.fillMaxWidth().padding(Dimens.screen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.screen)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End, maxLines = 1)
        }
    }
}

/** The value after [current] in [options]; after the last one the first again. */
fun <T> nextOf(options: List<T>, current: T): T = options[(options.indexOf(current) + 1) % options.size]
