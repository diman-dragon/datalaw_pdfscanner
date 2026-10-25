package com.datalaw.pdfscanner.core

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** A text that is resolved where the language is known (the UI), so controllers never touch resources. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

fun tx(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())
fun uiRaw(s: String): UiText = UiText.Raw(s)

fun UiText.resolve(ctx: Context): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args.map { if (it is UiText) it.resolve(ctx) else it }.toTypedArray())
    is UiText.Raw -> text
}

@Composable
fun UiText.get(): String = resolve(LocalContext.current)
