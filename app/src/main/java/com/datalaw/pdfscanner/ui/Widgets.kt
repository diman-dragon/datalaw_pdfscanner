package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.ui.theme.Dimens

/** A segmented switch: a row of options, the chosen one filled with the accent. [fill] = the options share the width equally (give it a width). */
@Composable
fun <T> Seg(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val shape = RoundedCornerShape(Dimens.control)
    val scheme = MaterialTheme.colorScheme
    Row(modifier.clip(shape).border(BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = 0.5f)), shape)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .background(if (on) scheme.primary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(v) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center, color = if (on) scheme.onPrimary else scheme.onSurface)
            }
        }
    }
}
