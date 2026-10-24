package com.datalaw.pdfscanner.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun mm(v: Double) = v.toString().removeSuffix(".0")

/** What is scanned: Full auto on top, then every type as a cell with a picture of its real shape, the name and the size; all on the screen at once. */
@Composable
fun TypeGrid(selectedId: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScanTypes.all.filter { it.free }.forEach { t -> AutoCell(t, t.id == selectedId) { onSelect(t.id) } }
        ScanTypes.all.filter { !it.free }.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t -> TypeCell(t, t.id == selectedId, { onSelect(t.id) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun cellBorder(on: Boolean) = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))

/** Full auto: one wide cell, no size. */
@Composable
private fun AutoCell(t: ScanType, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (on) scheme.primary.copy(alpha = 0.14f) else scheme.surface)
            .border(cellBorder(on), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", fontSize = 22.sp)
        Text(stringResource(t.nameRes), Modifier.padding(start = 10.dp), fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) scheme.primary else scheme.onSurface)
    }
}

@Composable
private fun TypeCell(t: ScanType, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val ink = if (on) scheme.primary else scheme.onSurfaceVariant
    Column(
        modifier
            .clip(shape)
            .background(if (on) scheme.primary.copy(alpha = 0.14f) else scheme.surface)
            .border(cellBorder(on), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(38.dp)) { glyph(t, ink) }
        Text(stringResource(t.nameRes), Modifier.padding(top = 4.dp), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${mm(t.mm.first)}×${mm(t.mm.second)}", fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The item as a rectangle of its real proportions; a two-sided one (a card) has a second one behind it. */
private fun DrawScope.glyph(t: ScanType, ink: Color) {
    val ratio = (t.mm.first / t.mm.second).toFloat()
    val k = if (t.sides == 2) 0.82f else 1f
    val box = size.width * k
    val w = if (ratio >= 1f) box else box * ratio
    val h = if (ratio >= 1f) box / ratio else box
    val r = CornerRadius(3.dp.toPx())
    val line = Stroke(1.6.dp.toPx())
    fun rect(x: Float, y: Float) {
        drawRoundRect(ink.copy(alpha = 0.22f), Offset(x, y), Size(w, h), r)
        drawRoundRect(ink, Offset(x, y), Size(w, h), r, style = line)
    }
    val shift = if (t.sides == 2) size.width * 0.18f else 0f
    val x0 = (size.width - w - shift) / 2
    val y0 = (size.height - h - shift) / 2
    if (t.sides == 2) rect(x0 + shift, y0)
    rect(x0, y0 + shift)
}
