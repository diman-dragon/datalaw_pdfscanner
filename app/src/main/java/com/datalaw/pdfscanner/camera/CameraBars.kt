package com.datalaw.pdfscanner.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.UiText
import com.datalaw.pdfscanner.core.get
import com.datalaw.pdfscanner.settings.CamMode
import com.datalaw.pdfscanner.settings.FlashMode

/** A round button on the dark glass over the preview. */
@Composable
internal fun RoundButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0x99000000)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Close, the mode (or the counter), the flash. */
@Composable
internal fun CameraTopBar(
    onClose: () -> Unit,
    smart: Boolean,
    forcedAuto: Boolean,
    mode: CamMode,
    onMode: (CamMode) -> Unit,
    badge: UiText?,
    flash: FlashMode,
    onFlash: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        RoundButton(stringResource(R.string.vw_close), onClose) { Text("✕", color = Color.White, fontSize = 18.sp) }
        when {
            smart && forcedAuto -> Text("✨ " + stringResource(R.string.st_auto), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            smart -> ModeToggle(mode, onMode)
            else -> badge?.let { Text(it.get(), color = Color.White, fontSize = 15.sp) }
        }
        FlashButton(flash, onFlash)
    }
}

/** The hint on a dark plate and the shutter. */
@Composable
internal fun CameraBottomBar(hint: String, busy: Boolean, ready: Boolean, onShoot: () -> Unit, modifier: Modifier = Modifier) {
    val shootLabel = stringResource(R.string.scan_shoot)
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hint, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        ShutterButton(busy, shootLabel, enabled = !busy && ready, onShoot)
    }
}

@Composable
private fun ShutterButton(busy: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(7.dp)
            .background(if (busy) Color.Gray else Color.White, CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, onClick = onClick),
    )
}
