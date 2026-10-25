package com.datalaw.pdfscanner.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.settings.FlashMode

/** The flash as a picture: Auto = a bolt with an A, On = a filled bolt, Off = an outlined bolt crossed out. A tap goes to the next one. */
@Composable
internal fun FlashButton(mode: FlashMode, onClick: () -> Unit) {
    val name = when (mode) { FlashMode.Auto -> R.string.fl_auto; FlashMode.On -> R.string.fl_on; FlashMode.Off -> R.string.fl_off }
    RoundButton(stringResource(R.string.cam_flash) + ": " + stringResource(name), onClick) {
        Box(Modifier.size(24.dp)) {
            Canvas(Modifier.fillMaxSize()) { bolt(mode) }
            if (mode == FlashMode.Auto) Text("A", Modifier.align(Alignment.BottomEnd), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun DrawScope.bolt(mode: FlashMode) {
    val k = size.width / 24f
    val shape = Path().apply {
        moveTo(7f * k, 2f * k); lineTo(7f * k, 13f * k); lineTo(10f * k, 13f * k); lineTo(10f * k, 22f * k)
        lineTo(17f * k, 10f * k); lineTo(13f * k, 10f * k); lineTo(17f * k, 2f * k); close()
    }
    if (mode == FlashMode.Off) {
        drawPath(shape, Color.White.copy(alpha = 0.7f), style = Stroke(1.6f * k))
        drawLine(Color.Black, Offset(3f * k, 3f * k), Offset(21f * k, 21f * k), 4.4f * k, StrokeCap.Round)
        drawLine(Color.White, Offset(3f * k, 3f * k), Offset(21f * k, 21f * k), 2f * k, StrokeCap.Round)
    } else {
        drawPath(shape, Color.White)
    }
}
