package com.datalaw.pdfscanner.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.settings.LibView

/** Rows / cards switch drawn as two pictograms (no text); the chosen one is filled with the accent. */
@Composable
fun ViewToggle(selected: LibView, onSelect: (LibView) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.clip(shape).border(BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = 0.5f)), shape)) {
        listOf(LibView.List to stringResource(R.string.lib_list), LibView.Cards to stringResource(R.string.lib_cards)).forEach { (v, label) ->
            val on = v == selected
            val ink = if (on) scheme.onPrimary else scheme.onSurface
            Box(
                Modifier
                    .background(if (on) scheme.primary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(v) }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Canvas(Modifier.size(20.dp)) { if (v == LibView.List) rowsGlyph(ink) else cardsGlyph(ink) }
            }
        }
    }
}

private fun DrawScope.rowsGlyph(ink: Color) {
    val th = size.height * 0.2f
    for (i in 0..2) {
        val y = size.height * (0.15f + 0.35f * i)
        drawRoundRect(ink, Offset(0f, y - th / 2), Size(th, th), CornerRadius(th * 0.25f))
        drawRoundRect(ink, Offset(size.width * 0.32f, y - th / 2), Size(size.width * 0.68f, th), CornerRadius(th / 2))
    }
}

private fun DrawScope.cardsGlyph(ink: Color) {
    val gap = size.width * 0.14f
    val cell = (size.width - gap) / 2
    for (r in 0..1) for (c in 0..1) drawRoundRect(ink, Offset(c * (cell + gap), r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.22f))
}
