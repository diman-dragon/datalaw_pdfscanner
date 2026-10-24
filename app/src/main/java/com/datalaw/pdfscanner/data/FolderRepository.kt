package com.datalaw.pdfscanner.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream

/** The chosen folder was deleted, moved or its card removed. */
class FolderMissingException(cause: Throwable? = null) : IOException("folder missing", cause)

/** The PDF folder through the Storage Access Framework: list, save (streaming), add (copy), remove, rename, replace, open. */
class FolderRepository(private val context: Context) {
    private val cr: ContentResolver get() = context.contentResolver

    fun treeRoot(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun queryString(uri: Uri, column: String): String? = try {
        cr.query(uri, arrayOf(column), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    fun displayName(uri: Uri): String? = queryString(uri, Document.COLUMN_DISPLAY_NAME)

    fun takePermission(tree: Uri) {
        cr.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    suspend fun list(tree: Uri): List<PdfFile> = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val out = ArrayList<PdfFile>()
        cr.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (Document.MIME_TYPE_DIR == mime) continue
                val pdf = "application/pdf" == mime || name.lowercase().endsWith(".pdf")
                if (!pdf) continue
                out.add(PdfFile(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString(), name, c.getLong(3), c.getLong(4)))
            }
        } ?: throw IOException("cannot list")
        out.sortedByDescending { it.modified }
    }

    private fun safeName(name: String): String {
        val s = name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").trim()
        return s.ifEmpty { "scan" }
    }

    /** Creates a new PDF in the tree and lets [write] stream it. Name without extension (the provider adds .pdf and resolves conflicts). A failed write removes the partial file. */
    suspend fun savePdf(tree: Uri, name: String, write: suspend (OutputStream) -> Unit): PdfFile = withContext(Dispatchers.IO) {
        val safe = safeName(name)
        val dst = try {
            DocumentsContract.createDocument(cr, treeRoot(tree), "application/pdf", safe)
        } catch (e: Exception) {
            val m = e.message ?: ""
            if (e is java.io.FileNotFoundException || m.contains("Missing file") || m.contains("No such file")) throw FolderMissingException(e)
            throw e
        } ?: throw IOException("create failed")
        try {
            val raw = cr.openOutputStream(dst) ?: throw IOException("cannot open")
            BufferedOutputStream(raw, 256 * 1024).use { write(it) }
        } catch (e: Throwable) {
            runCatching { DocumentsContract.deleteDocument(cr, dst) }
            throw e
        }
        PdfFile(dst.toString(), displayName(dst) ?: "$safe.pdf", 0, System.currentTimeMillis())
    }

    suspend fun remove(uri: Uri): Boolean = withContext(Dispatchers.IO) { DocumentsContract.deleteDocument(cr, uri) }

    suspend fun rename(uri: Uri, name: String): Uri? = withContext(Dispatchers.IO) { DocumentsContract.renameDocument(cr, uri, name) }

    /** Overwrites [target] with the contents of [from], then deletes [from]. The original is never deleted or renamed first. */
    suspend fun replaceWith(target: Uri, from: Uri) = withContext(Dispatchers.IO) {
        cr.openInputStream(from)!!.use { i -> cr.openOutputStream(target, "wt")!!.use { o -> i.copyTo(o, 64 * 1024) } }
        runCatching { DocumentsContract.deleteDocument(cr, from) }
        Unit
    }

    fun viewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
