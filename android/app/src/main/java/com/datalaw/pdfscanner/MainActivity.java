package com.datalaw.pdfscanner;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(FolderPlugin.class);
    registerPlugin(OcrPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
