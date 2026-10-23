package com.datalaw.pdfscanner.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.settings.QuickMode

/** Frame / Auto on the camera: the chosen one is white. */
@Composable
fun ModeToggle(mode: QuickMode, onSelect: (QuickMode) -> Unit) {
    val pill = RoundedCornerShape(20.dp)
    Row(Modifier.clip(pill).background(Color(0x99000000))) {
        listOf(QuickMode.Frame to stringResource(R.string.mode_frame), QuickMode.Auto to stringResource(R.string.mode_auto)).forEach { (m, label) ->
            val on = m == mode
            Text(
                label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(pill).background(if (on) Color.White else Color.Transparent).clickable { onSelect(m) }.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
