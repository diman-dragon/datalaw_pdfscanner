package com.datalaw.pdfscanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The sizes of the app: every screen takes its margins and corners from here. */
object Dimens {
    val screen = 16.dp   // the margin of a screen
    val gap = 8.dp       // between cells and cards
    val card = 16.dp     // the corner of a card
    val control = 12.dp  // the corner of a switch
    val thumb = 8.dp     // the corner of a page preview
    val glyph = 22.sp    // an emoji used as an icon: small (tab icon, a cell)
    val glyphMid = 28.sp // ... medium (a card)
    val glyphBig = 48.sp // ... big (an empty screen)
}

/** Six roles of text: body 15 (the default), small 13 (explanations), caption 12 (dates, sizes, tab names), title 17 (names of screens and cards), headline 22 bold (the name of the app), label 15 bold (buttons). */
val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)
