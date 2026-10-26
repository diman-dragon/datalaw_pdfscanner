package com.datalaw.pdfscanner.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.datalaw.pdfscanner.R
import com.datalaw.pdfscanner.ui.PrimaryButton

/** The state of the camera permission and the way to ask for it again. */
internal class CameraPermission(val granted: Boolean, val ask: () -> Unit)

/** Remembers whether the camera may be used; asks once when the screen opens. */
@Composable
internal fun rememberCameraPermission(): CameraPermission {
    val appCtx = LocalContext.current.applicationContext
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(appCtx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    return CameraPermission(granted) { launcher.launch(Manifest.permission.CAMERA) }
}

/** What is on the screen while there is no permission: the reason, "Allow camera" and "Close". */
@Composable
internal fun CameraPermissionPrompt(onGrant: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.cam_perm), color = Color.White)
        PrimaryButton(stringResource(R.string.cam_grant), onGrant, Modifier.padding(top = 16.dp))
        PrimaryButton(stringResource(R.string.vw_close), onClose, Modifier.padding(top = 8.dp))
    }
}
