package com.datalaw.pdfscanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.datalaw.pdfscanner.ui.theme.Dimens

/** A page whose content stands in the middle of the screen, across and down; when it is taller than the screen it scrolls. */
@Composable
fun CenterColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(Dimens.screen), contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
