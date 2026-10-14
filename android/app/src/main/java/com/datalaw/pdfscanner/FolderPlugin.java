package com.datalaw.pdfscanner;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.OpenableColumns;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** PDF folder via Storage Access Framework: pick, list, add (copy), save, remove, rename, open, preview. */
@CapacitorPlugin(name = "Folder")
public class FolderPlugin extends Plugin {

  private ContentResolver cr() {
    return getContext().getContentResolver();
  }

  private Uri treeRoot(Uri tree) {
    return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
  }

  private String queryName(Uri uri, String column) {
    try (Cursor c = cr().query(uri, new String[] {column}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    return null;
  }

  @PluginMethod
  public void pickFolder(PluginCall call) {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    i.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(call, i, "pickFolderResult");
  }

  @ActivityCallback
  private void pickFolderResult(PluginCall call, ActivityResult result) {
    if (call == null) return;
    Intent data = result.getData();
    if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
      call.reject("cancelled");
      return;
    }
    Uri tree = data.getData();
    cr().takePersistableUriPermission(
        tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    String name = queryName(treeRoot(tree), Document.COLUMN_DISPLAY_NAME);
    JSObject o = new JSObject();
    o.put("uri", tree.toString());
    o.put("name", name == null ? "" : name);
    call.resolve(o);
  }

  @PluginMethod
  public void list(PluginCall call) {
    String t = call.getString("tree");
    if (t == null) {
      call.reject("tree required");
      return;
    }
    Uri tree = Uri.parse(t);
    Uri children =
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    String[] cols = {
      Document.COLUMN_DOCUMENT_ID,
      Document.COLUMN_DISPLAY_NAME,
      Document.COLUMN_MIME_TYPE,
      Document.COLUMN_SIZE,
      Document.COLUMN_LAST_MODIFIED
    };
    JSArray files = new JSArray();
    try (Cursor c = cr().query(children, cols, null, null, null)) {
      while (c != null && c.moveToNext()) {
        String name = c.getString(1);
        String mime = c.getString(2);
        if (Document.MIME_TYPE_DIR.equals(mime)) continue;
        boolean pdf =
            "application/pdf".equals(mime) || (name != null && name.toLowerCase().endsWith(".pdf"));
        if (!pdf) continue;
        JSObject o = new JSObject();
        o.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString());
        o.put("name", name);
        o.put("size", c.getLong(3));
        o.put("modified", c.getLong(4));
        files.put(o);
      }
    } catch (Exception e) {
      call.reject("list failed: " + e.getMessage());
      return;
    }
    JSObject r = new JSObject();
    r.put("files", files);
    call.resolve(r);
  }

  @PluginMethod
  public void add(PluginCall call) {
    if (call.getString("tree") == null) {
      call.reject("tree required");
      return;
    }
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/pdf");
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    startActivityForResult(call, i, "addResult");
  }

  @ActivityCallback
  private void addResult(PluginCall call, ActivityResult result) {
    if (call == null) return;
    Intent data = result.getData();
    if (result.getResultCode() != Activity.RESULT_OK || data == null) {
      call.reject("cancelled");
      return;
    }
    List<Uri> sources = new ArrayList<>();
    if (data.getClipData() != null) {
      for (int k = 0; k < data.getClipData().getItemCount(); k++) {
        sources.add(data.getClipData().getItemAt(k).getUri());
      }
    } else if (data.getData() != null) {
      sources.add(data.getData());
    }
    Uri parent = treeRoot(Uri.parse(call.getString("tree")));
    // Copy off the main thread: files can be large.
    new Thread(
            () -> {
              int added = 0;
              for (Uri src : sources) {
                Uri dst = null;
                try {
                  String name = queryName(src, OpenableColumns.DISPLAY_NAME);
                  if (name == null) name = "document";
                  if (name.toLowerCase().endsWith(".pdf")) name = name.substring(0, name.length() - 4);
                  dst = DocumentsContract.createDocument(cr(), parent, "application/pdf", name);
                  if (dst == null) continue;
                  try (InputStream in = cr().openInputStream(src);
                      OutputStream out = cr().openOutputStream(dst)) {
                    byte[] buf = new byte[64 * 1024];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                  }
                  added++;
                } catch (Exception e) {
                  if (dst != null) {
                    try {
                      DocumentsContract.deleteDocument(cr(), dst);
                    } catch (Exception ignored) {
                    }
                  }
                }
              }
              JSObject o = new JSObject();
              o.put("added", added);
              call.resolve(o);
            })
        .start();
  }

  @PluginMethod
  public void remove(PluginCall call) {
    String u = call.getString("uri");
    if (u == null) {
      call.reject("uri required");
      return;
    }
    try {
      if (DocumentsContract.deleteDocument(cr(), Uri.parse(u))) call.resolve();
      else call.reject("delete failed");
    } catch (Exception e) {
      call.reject("delete failed: " + e.getMessage());
    }
  }

  @PluginMethod
  public void rename(PluginCall call) {
    String u = call.getString("uri");
    String name = call.getString("name");
    if (u == null || name == null) {
      call.reject("uri and name required");
      return;
    }
    try {
      Uri n = DocumentsContract.renameDocument(cr(), Uri.parse(u), name);
      if (n == null) {
        call.reject("rename failed");
        return;
      }
      JSObject o = new JSObject();
      o.put("uri", n.toString());
      call.resolve(o);
    } catch (Exception e) {
      call.reject("rename failed: " + e.getMessage());
    }
  }

  @PluginMethod
  public void open(PluginCall call) {
    String u = call.getString("uri");
    if (u == null) {
      call.reject("uri required");
      return;
    }
    Intent i = new Intent(Intent.ACTION_VIEW);
    i.setDataAndType(Uri.parse(u), "application/pdf");
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      getContext().startActivity(i);
      call.resolve();
    } catch (ActivityNotFoundException e) {
      call.reject("no_viewer");
    }
  }

  @PluginMethod
  public void preview(PluginCall call) {
    String u = call.getString("uri");
    if (u == null) {
      call.reject("uri required");
      return;
    }
    try {
      JSObject o = new JSObject();
      o.put("data", PdfPreview.firstPage(cr(), Uri.parse(u), call.getInt("width", 360)));
      call.resolve(o);
    } catch (Exception e) {
      call.reject("preview failed: " + e.getMessage());
    }
  }

  /** Writes a new PDF (base64) into the tree. Name without extension; provider adds ".pdf" and resolves conflicts. */
  @PluginMethod
  public void save(PluginCall call) {
    String t = call.getString("tree");
    String name = call.getString("name");
    String data = call.getString("data");
    if (t == null || name == null || data == null) {
      call.reject("tree, name and data required");
      return;
    }
    Uri dst = null;
    try {
      byte[] bytes = Base64.decode(data, Base64.DEFAULT);
      dst = DocumentsContract.createDocument(cr(), treeRoot(Uri.parse(t)), "application/pdf", name);
      if (dst == null) {
        call.reject("create failed");
        return;
      }
      try (OutputStream out = cr().openOutputStream(dst)) {
        out.write(bytes);
      }
      JSObject o = new JSObject();
      o.put("uri", dst.toString());
      String shown = queryName(dst, Document.COLUMN_DISPLAY_NAME);
      o.put("name", shown == null ? name : shown);
      call.resolve(o);
    } catch (Exception e) {
      if (dst != null) {
        try {
          DocumentsContract.deleteDocument(cr(), dst);
        } catch (Exception ignored) {
        }
      }
      call.reject("save failed: " + e.getMessage());
    }
  }
}
