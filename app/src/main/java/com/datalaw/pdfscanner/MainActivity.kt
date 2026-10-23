package com.datalaw.pdfscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalaw.pdfscanner.ui.AppRoot
import com.datalaw.pdfscanner.ui.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.factory(graph))
            AppRoot(vm)
        }
    }
}
