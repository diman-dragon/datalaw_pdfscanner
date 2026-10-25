package com.datalaw.pdfscanner.recognize

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.core.AppActions
import com.datalaw.pdfscanner.core.UiText
import com.datalaw.pdfscanner.core.get
import com.datalaw.pdfscanner.core.tx
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.ocr.OcrLangs
import com.datalaw.pdfscanner.pdf.PdfReader
import com.datalaw.pdfscanner.ui.AppCard
import com.datalaw.pdfscanner.ui.BottomBar
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.TitleBar
import com.datalaw.pdfscanner.ui.theme.Dimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Text recognition (from the viewer menu): languages ru / en / it / sr, this page or the whole document; the text can be copied. */
@Composable
fun OcrScreen(vm: AppActions, file: PdfFile, startPage: Int, pages: Int) {
    val graph = LocalGraph.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<UiText?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    BackHandler { vm.pop() }
    DisposableEffect(Unit) { onDispose { job?.cancel(); graph.ocr.releaseAsync() } }

    fun go(all: Boolean) {
        val sel = settings.ocrLangs
        if (sel.isEmpty()) { vm.toast(R.string.ocr_nolang); return }
        job?.cancel()
        job = scope.launch {
            busy = true
            text = ""
            try {
                status = tx(R.string.ocr_dl)
                val langs = OcrLangs.tess(sel)
                graph.ocr.ensure(langs)
                val reader = PdfReader.open(graph.app.contentResolver, Uri.parse(file.uri))
                try {
                    val idx = if (all) (0 until reader.pageCount).toList() else listOf(startPage.coerceIn(0, reader.pageCount - 1))
                    val out = ArrayList<String>()
                    for ((k, i) in idx.withIndex()) {
                        status = tx(R.string.ocr_run_n, k + 1, idx.size)
                        val bmp = reader.render(i, 2400)
                        val t = try { graph.ocr.text(bmp, langs).trim() } finally { bmp.recycle() }
                        out.add(if (all) "— ${i + 1} —\n$t" else t)
                        text = out.joinToString("\n\n")
                    }
                } finally {
                    reader.close()
                }
                status = if (text.replace(Regex("[—\\d\\s]"), "").isEmpty()) tx(R.string.ocr_empty) else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = tx(R.string.ocr_fail)
            } finally {
                busy = false
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).systemBarsPadding()) {
        TitleBar(stringResource(R.string.ocr_title), onBack = { vm.pop() })
        Column(Modifier.weight(1f).fillMaxWidth().padding(Dimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                for ((code, _) in OcrLangs.all) {
                    val on = code in settings.ocrLangs
                    AppCard(
                        Modifier.weight(1f), selected = on, horizontalAlignment = Alignment.CenterHorizontally,
                        onClick = { scope.launch { graph.settingsStore.setOcrLangs(if (on) settings.ocrLangs - code else settings.ocrLangs + code) } },
                    ) {
                        Text(code.uppercase(), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium, color = if (on) scheme.primary else scheme.onSurface)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                Button({ go(false) }, Modifier.weight(1f), enabled = !busy) { Text(stringResource(R.string.ocr_page), maxLines = 1) }
                Button({ go(true) }, Modifier.weight(1f), enabled = !busy) { Text(stringResource(R.string.ocr_all), maxLines = 1) }
            }
            status?.let { Text(it.get(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            OutlinedTextField(text, {}, Modifier.fillMaxWidth().weight(1f), readOnly = true)
        }
        BottomBar {
            OutlinedButton({ if (text.isNotEmpty()) { clipboard.setText(AnnotatedString(text)); vm.toast(R.string.ocr_copied) } }, Modifier.weight(1f), enabled = text.isNotEmpty()) { Text(stringResource(R.string.ocr_copy)) }
        }
    }
}
