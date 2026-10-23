package com.datalaw.pdfscanner.ocr

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.data.PdfFile
import com.datalaw.pdfscanner.pdf.PdfReader
import com.datalaw.pdfscanner.ui.AppViewModel
import com.datalaw.pdfscanner.ui.LocalGraph
import com.datalaw.pdfscanner.ui.LocalSettings
import com.datalaw.pdfscanner.ui.UiText
import com.datalaw.pdfscanner.ui.get
import com.datalaw.pdfscanner.ui.tx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Text recognition (from the viewer menu): languages ru / en / it / sr, this page or the whole document; the text can be copied. */
@Composable
fun OcrScreen(vm: AppViewModel, file: PdfFile, startPage: Int, pages: Int) {
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ocr_title), Modifier.weight(1f), fontSize = 17.sp)
            Text("✕", fontSize = 22.sp, modifier = Modifier.clickable { vm.pop() }.padding(8.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((code, _) in OcrLangs.all) {
                FilterChip(
                    selected = code in settings.ocrLangs,
                    onClick = { scope.launch { graph.settingsStore.setOcrLangs(if (code in settings.ocrLangs) settings.ocrLangs - code else settings.ocrLangs + code) } },
                    label = { Text(code.uppercase()) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ go(false) }, enabled = !busy) { Text(stringResource(R.string.ocr_page)) }
            Button({ go(true) }, enabled = !busy) { Text(stringResource(R.string.ocr_all)) }
            OutlinedButton({ if (text.isNotEmpty()) { clipboard.setText(AnnotatedString(text)); vm.toast(R.string.ocr_copied) } }, enabled = text.isNotEmpty()) { Text(stringResource(R.string.ocr_copy)) }
        }
        status?.let { Text(it.get(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedTextField(text, {}, Modifier.fillMaxWidth().weight(1f), readOnly = true)
    }
}
