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
}

/** Four roles of text: body 15 (the default), small 13 (explanations, dates), title 17 (names of screens and cards), label 15 bold (buttons). */
val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)
