package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The "i" of the title bar: a letter in a ring, in the colours of the theme (the touch area is 48 dp). */
@Composable
fun InfoButton(label: String, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(26.dp).border(1.5.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
            Text("i", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif)
        }
    }
}
