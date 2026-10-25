package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.datalaw.pdfscanner.ui.theme.Dimens

/** The one card of the app: a rounded cell with a border; the chosen one is tinted with the accent. Settings, the library and the scan types are all made of it. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.card)
    val border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.35f))
    val base = modifier.clip(shape).background(if (selected) scheme.primary.copy(alpha = 0.14f) else scheme.surface).border(border, shape)
    Column(if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base, horizontalAlignment = horizontalAlignment, content = content)
}
