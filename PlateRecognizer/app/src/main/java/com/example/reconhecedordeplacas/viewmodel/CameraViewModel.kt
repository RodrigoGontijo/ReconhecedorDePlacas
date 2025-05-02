package com.example.reconhecedordeplacas.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class DetectionStatus(
    val isSending: Boolean = false,
    val lastPlate: String? = null
)

class CameraViewModel(
    private val plateRecognizerClient: PlateRecognizerClient
) : ViewModel() {

    private val _detectionStatus = MutableStateFlow(DetectionStatus())
    val detectionStatus: StateFlow<DetectionStatus> = _detectionStatus

    private var lastDetectionTime = 0L
    private val throttleMillis = 10_000L // 10 segundos

    fun startCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder().build()

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        handleFrame(imageProxy, imageCapture)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                analyzer
            )
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    private fun handleFrame(imageProxy: ImageProxy, imageCapture: ImageCapture) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime >= throttleMillis) {
            lastDetectionTime = currentTime

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(imageCapture.context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        sendToPlateRecognizer(image)
                        image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraViewModel", "Capture failed: ${exception.message}")
                    }
                }
            )
        }

        imageProxy.close()
    }

    private fun sendToPlateRecognizer(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _detectionStatus.value = DetectionStatus(isSending = true)
                val result = plateRecognizerClient.sendImage(bitmap)
                _detectionStatus.value = DetectionStatus(isSending = false, lastPlate = result)
            } catch (e: Exception) {
                Log.e("PlateRecognizer", "Erro ao enviar imagem: ${e.message}")
                _detectionStatus.value = DetectionStatus(isSending = false)
            }
        }
    }
}