package com.datalaw.pdfscanner.scan

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A pictogram and one short sentence, the way the Quick screen tells what will happen. */
@Composable
fun QuickFact(icon: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(icon, Modifier.width(36.dp), fontSize = 20.sp)
        Text(text, fontSize = 15.sp, lineHeight = 20.sp)
    }
}
