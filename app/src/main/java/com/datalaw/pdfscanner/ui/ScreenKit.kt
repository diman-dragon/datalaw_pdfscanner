package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.ui.theme.Dimens

// The pieces every screen is made of. A screen has nothing of its own: the bar, the cards, the buttons, the texts all come from here and from the theme.

/** The bar on top of a screen. [onBack] = a close button on the left (a screen over another one); without it only the title. [actions] sit on the right. */
@Composable
fun TitleBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val closeLabel = stringResource(R.string.vw_close)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = if (onBack == null) Dimens.screen else 0.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 20.sp) }
            }
            Text(title, Modifier.weight(1f).padding(horizontal = if (onBack == null) 0.dp else 4.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** The bar under a screen: buttons in a row. */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(Dimens.gap), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A button with a sign in it (◀ ▶ − + ⟲ ⟳); [label] is read out to the screen reader. */
@Composable
fun GlyphButton(glyph: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick, modifier.semantics { contentDescription = label }) { Text(glyph, fontSize = 18.sp) }
}

/** The three dots with a menu; the items call [close] when they are done. */
@Composable
fun OverflowMenu(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { open = true }.semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Text("⋮", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        DropdownMenu(open, { open = false }) { content { open = false } }
    }
}

/** A note in the middle of the screen: loading, nothing to show, failed. */
@Composable
fun CenterNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Dimens.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** The number on a page preview. */
@Composable
fun PageBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.thumb)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
