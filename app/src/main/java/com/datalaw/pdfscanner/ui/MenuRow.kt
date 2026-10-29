package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.datalaw.pdfscanner.ui.theme.Dimens

/** One row of a menu: a small picture and the name; red when it destroys something. */
@Composable
fun MenuRow(icon: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = color, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Text(icon, fontSize = Dimens.glyph) },
        onClick = onClick,
    )
}

/** A thin line between the usual rows of a menu and the destructive one. */
@Composable
fun MenuDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
