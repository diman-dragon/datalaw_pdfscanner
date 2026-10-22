package com.datalaw.pdfscanner

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    registerPlugin(FolderPlugin::class.java)
    registerPlugin(OcrPlugin::class.java)
    super.onCreate(savedInstanceState)
  }
}
