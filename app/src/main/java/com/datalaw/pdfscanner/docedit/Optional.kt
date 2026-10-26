package com.datalaw.pdfscanner.docedit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The picture, the sharpness and the automatic frame are extras: when one of them fails (memory, OpenCV) the editor goes on without it instead of closing the app. null = it failed. */
internal suspend fun <T> optional(block: () -> T): T? = try {
    withContext(Dispatchers.Default) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    null
}
