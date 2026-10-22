package com.datalaw.pdfscanner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** PDF folder via Storage Access Framework: pick, list, add (copy), streaming save, remove, rename, open, preview, in-app view. */
@CapacitorPlugin(name = "Folder")
class FolderPlugin : Plugin() {

  private fun cr(): ContentResolver = context.contentResolver

  private fun treeRoot(tree: Uri): Uri =
    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

  private fun queryName(uri: Uri, column: String): String? {
    try {
      cr().query(uri, arrayOf(column), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0)
      }
    } catch (_: Exception) {
    }
    return null
  }

  @PluginMethod
  fun pickFolder(call: PluginCall) {
    val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    i.addFlags(
      Intent.FLAG_GRANT_READ_URI_PERMISSION
        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    )
    startActivityForResult(call, i, "pickFolderResult")
  }

  @ActivityCallback
  private fun pickFolderResult(call: PluginCall?, result: ActivityResult) {
    if (call == null) return
    val data = result.data
    if (result.resultCode != Activity.RESULT_OK || data?.data == null) {
      call.reject("cancelled")
      return
    }
    val tree = data.data!!
    cr().takePersistableUriPermission(
      tree,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    val name = queryName(treeRoot(tree), Document.COLUMN_DISPLAY_NAME)
    val o = JSObject()
    o.put("uri", tree.toString())
    o.put("name", name ?: "")
    call.resolve(o)
  }

  @PluginMethod
  fun list(call: PluginCall) {
    val t = call.getString("tree")
    if (t == null) {
      call.reject("tree required")
      return
    }
    val tree = Uri.parse(t)
    val children =
      DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val cols = arrayOf(
      Document.COLUMN_DOCUMENT_ID,
      Document.COLUMN_DISPLAY_NAME,
      Document.COLUMN_MIME_TYPE,
      Document.COLUMN_SIZE,
      Document.COLUMN_LAST_MODIFIED,
    )
    val files = JSArray()
    try {
      cr().query(children, cols, null, null, null)?.use { c ->
        while (c.moveToNext()) {
          val name = c.getString(1)
          val mime = c.getString(2)
          if (Document.MIME_TYPE_DIR == mime) continue
          val pdf =
            "application/pdf" == mime || (name != null && name.lowercase().endsWith(".pdf"))
          if (!pdf) continue
          val o = JSObject()
          o.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString())
          o.put("name", name)
          o.put("size", c.getLong(3))
          o.put("modified", c.getLong(4))
          files.put(o)
        }
      }
    } catch (e: Exception) {
      call.reject("list failed: ${e.message}")
      return
    }
    val r = JSObject()
    r.put("files", files)
    call.resolve(r)
  }

  @PluginMethod
  fun add(call: PluginCall) {
    if (call.getString("tree") == null) {
      call.reject("tree required")
      return
    }
    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
    i.addCategory(Intent.CATEGORY_OPENABLE)
    i.type = "application/pdf"
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    startActivityForResult(call, i, "addResult")
  }

  @ActivityCallback
  private fun addResult(call: PluginCall?, result: ActivityResult) {
    if (call == null) return
    val data = result.data
    if (result.resultCode != Activity.RESULT_OK || data == null) {
      call.reject("cancelled")
      return
    }
    val sources = ArrayList<Uri>()
    data.clipData?.let { clip ->
      for (k in 0 until clip.itemCount) {
        sources.add(clip.getItemAt(k).uri)
      }
    } ?: data.data?.let { sources.add(it) }

    val parent = treeRoot(Uri.parse(call.getString("tree")))
    // Copy off the main thread: files can be large.
    Thread {
      var added = 0
      for (src in sources) {
        var dst: Uri? = null
        try {
          var name = queryName(src, OpenableColumns.DISPLAY_NAME) ?: "document"
          if (name.lowercase().endsWith(".pdf")) name = name.substring(0, name.length - 4)
          dst = DocumentsContract.createDocument(cr(), parent, "application/pdf", name)
          if (dst == null) continue
          cr().openInputStream(src)?.use { input ->
            cr().openOutputStream(dst)?.use { out ->
              val buf = ByteArray(64 * 1024)
              var len: Int
              while (input.read(buf).also { len = it } > 0) out.write(buf, 0, len)
            }
          }
          added++
        } catch (_: Exception) {
          dst?.let {
            try {
              DocumentsContract.deleteDocument(cr(), it)
            } catch (_: Exception) {
            }
          }
        }
      }
      val o = JSObject()
      o.put("added", added)
      call.resolve(o)
    }.start()
  }

  @PluginMethod
  fun remove(call: PluginCall) {
    val u = call.getString("uri")
    if (u == null) {
      call.reject("uri required")
      return
    }
    try {
      if (DocumentsContract.deleteDocument(cr(), Uri.parse(u))) call.resolve()
      else call.reject("delete failed")
    } catch (e: Exception) {
      call.reject("delete failed: ${e.message}")
    }
  }

  @PluginMethod
  fun rename(call: PluginCall) {
    val u = call.getString("uri")
    val name = call.getString("name")
    if (u == null || name == null) {
      call.reject("uri and name required")
      return
    }
    try {
      val n = DocumentsContract.renameDocument(cr(), Uri.parse(u), name)
      if (n == null) {
        call.reject("rename failed")
        return
      }
      val o = JSObject()
      o.put("uri", n.toString())
      call.resolve(o)
    } catch (e: Exception) {
      call.reject("rename failed: ${e.message}")
    }
  }

  @PluginMethod
  fun open(call: PluginCall) {
    val u = call.getString("uri")
    if (u == null) {
      call.reject("uri required")
      return
    }
    val i = Intent(Intent.ACTION_VIEW)
    i.setDataAndType(Uri.parse(u), "application/pdf")
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      context.startActivity(i)
      call.resolve()
    } catch (_: ActivityNotFoundException) {
      call.reject("no_viewer")
    }
  }

  @PluginMethod
  fun preview(call: PluginCall) {
    val u = call.getString("uri")
    if (u == null) {
      call.reject("uri required")
      return
    }
    try {
      val f = PdfPreview.firstPage(cr(), Uri.parse(u), call.getInt("width", 360))
      val o = JSObject()
      o.put("data", f.data)
      o.put("pages", f.pages)
      call.resolve(o)
    } catch (e: Exception) {
      call.reject("preview failed: ${e.message}")
    }
  }

  // In-app viewer: viewOpen -> viewPage (many) -> viewClose.
  private val viewer = PdfViewer()

  @PluginMethod
  fun viewOpen(call: PluginCall) {
    val u = call.getString("uri")
    if (u == null) {
      call.reject("uri required")
      return
    }
    try {
      val ratios = JSArray()
      val id = viewer.open(cr(), Uri.parse(u), ratios)
      val o = JSObject()
      o.put("id", id)
      o.put("ratios", ratios)
      call.resolve(o)
    } catch (e: Exception) {
      call.reject("view failed: ${e.message}")
    }
  }

  @PluginMethod
  fun viewPage(call: PluginCall) {
    val id = call.getString("id")
    if (id == null) {
      call.reject("id required")
      return
    }
    try {
      val o = JSObject()
      o.put(
        "data",
        viewer.page(
          id,
          call.getInt("index", 0),
          call.getInt("width", 1200),
          call.getInt("quality", 85),
        ),
      )
      call.resolve(o)
    } catch (e: Exception) {
      call.reject("page failed: ${e.message}")
    }
  }

  @PluginMethod
  fun viewClose(call: PluginCall) {
    viewer.close(call.getString("id"))
    call.resolve()
  }

  override fun handleOnDestroy() {
    viewer.closeAll()
  }

  // Streaming save: open -> write chunks (base64) -> close. Keeps big multi-page PDFs off the bridge in one piece.
  private val streams = HashMap<String, OutputStream>()
  private val streamUris = HashMap<String, Uri>()

  /** Creates a new PDF in the tree. Name without extension; provider adds ".pdf" and resolves conflicts. */
  @PluginMethod
  fun saveOpen(call: PluginCall) {
    val t = call.getString("tree")
    val name = call.getString("name")
    if (t == null || name == null) {
      call.reject("tree and name required")
      return
    }
    try {
      var safe = name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").trim() // the provider turns a path separator into a missing sub-folder
      if (safe.isEmpty()) safe = "scan"
      val dst = DocumentsContract.createDocument(cr(), treeRoot(Uri.parse(t)), "application/pdf", safe)
      if (dst == null) {
        call.reject("create failed")
        return
      }
      val out = cr().openOutputStream(dst)
      val id = UUID.randomUUID().toString()
      streams[id] = out!!
      streamUris[id] = dst
      val o = JSObject()
      o.put("id", id)
      call.resolve(o)
    } catch (e: Exception) {
      val m = e.message.toString()
      // the chosen folder was deleted / moved / its card removed: the app asks for a new one
      call.reject(
        (if (m.contains("Missing file") || m.contains("No such file") "folder_missing: " else "save failed: ") + m,
      )
    }
  }

  /** Overwrites the document `uri` with the contents of `from` (then deletes `from`). The original is never deleted or renamed. */
  @PluginMethod
  fun replaceWith(call: PluginCall) {
    val u = call.getString("uri")
    val f = call.getString("from")
    if (u == null || f == null) {
      call.reject("uri and from required")
      return
    }
    Thread {
      try {
        val dst = Uri.parse(u)
        val src = Uri.parse(f)
        cr().openInputStream(src).use { input ->
          cr().openOutputStream(dst, "wt").use { out ->
            if (input == null || out == null) throw java.io.IOException("cannot open")
            val buf = ByteArray(64 * 1024)
            var len: Int
            while (input.read(buf).also { len = it } > 0) out.write(buf, 0, len)
          }
        }
        try {
          DocumentsContract.deleteDocument(cr(), src)
        } catch (_: Exception) {
        }
        val o = JSObject()
        o.put("uri", dst.toString())
        call.resolve(o)
      } catch (e: Exception) {
        call.reject("replace failed: ${e.message}")
      }
    }.start()
  }

  @PluginMethod
  fun saveWrite(call: PluginCall) {
    val out = streams[call.getString("id")]
    val data = call.getString("data")
    if (out == null || data == null) {
      call.reject("bad stream")
      return
    }
    try {
      out.write(Base64.decode(data, Base64.DEFAULT))
      call.resolve()
    } catch (e: Exception) {
      call.reject("write failed: ${e.message}")
    }
  }

  @PluginMethod
  fun saveClose(call: PluginCall) {
    val id = call.getString("id")
    val out = streams.remove(id)
    val dst = streamUris.remove(id)
    if (out == null || dst == null) {
      call.reject("bad stream")
      return
    }
    try {
      out.close()
      val shown = queryName(dst, Document.COLUMN_DISPLAY_NAME)
      val o = JSObject()
      o.put("uri", dst.toString())
      o.put("name", shown ?: "")
      call.resolve(o)
    } catch (e: Exception) {
      call.reject("close failed: ${e.message}")
    }
  }

  /** Cancels an unfinished save and removes the partial file. */
  @PluginMethod
  fun saveAbort(call: PluginCall) {
    val id = call.getString("id")
    val out = streams.remove(id)
    val dst = streamUris.remove(id)
    try {
      out?.close()
    } catch (_: Exception) {
    }
    try {
      if (dst != null) DocumentsContract.deleteDocument(cr(), dst)
    } catch (_: Exception) {
    }
    call.resolve()
  }
}
