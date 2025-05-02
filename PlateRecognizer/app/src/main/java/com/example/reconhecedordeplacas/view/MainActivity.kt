package com.example.reconhecedordeplacas.view

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.reconhecedordeplacas.viewmodel.CameraViewModel
import org.koin.androidx.compose.getViewModel

class MainActivity : ComponentActivity() {

    private var permissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                permissionGranted = true
                launchCameraUI()
            } else {
                Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_LONG).show()
            }
        }

        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCameraUI() {
        setContent {
            VehicleWatcherApp()
        }
    }
}

@Composable
fun VehicleWatcherApp(viewModel: CameraViewModel = getViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val detectionStatus by viewModel.detectionStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startCamera(previewView, lifecycleOwner)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (detectionStatus.isSending) {
            Text(
                text = "Enviando...",
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )
        } else if (detectionStatus.lastPlate != null) {
            Text(
                text = "Última placa: ${detectionStatus.lastPlate}",
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )
        }
    }
}