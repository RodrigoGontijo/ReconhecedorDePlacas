package com.example.reconhecedordeplacas.viewmodel

import android.app.Application
import android.graphics.*
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.reconhecedordeplacas.api.PlateRecognizerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class CameraViewModel(
    application: Application,
    private val plateRecognizerClient: PlateRecognizerClient
) : AndroidViewModel(application) {

    private val _detectionStatus = MutableStateFlow(DetectionStatus())
    val detectionStatus: StateFlow<DetectionStatus> = _detectionStatus

    private var lastDetectionTime = 0L
    private val throttleMillis = 10_000L

    private lateinit var tflite: Interpreter

    private var overlay: ImageView? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileDescriptor = getApplication<Application>().assets.openFd("vehicle_detection.tflite")
                val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                val modelBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                tflite = Interpreter(modelBuffer)
                Log.d("TFLite", "Modelo carregado com sucesso")
            } catch (e: Exception) {
                Log.e("TFLite", "Erro ao carregar modelo: ${e.message}")
            }
        }
    }

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
                        handleFrame(imageProxy, imageCapture, previewView)
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
            Log.d("CameraViewModel", "Camera iniciada com sucesso")
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    private fun handleFrame(imageProxy: ImageProxy, imageCapture: ImageCapture, previewView: PreviewView) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime >= throttleMillis) {
            lastDetectionTime = currentTime

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(getApplication()),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val originalBitmap = image.toBitmap()
                        val bitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawBitmap(originalBitmap, 0f, 0f, null)

                        Log.d("CameraViewModel", "Imagem capturada com sucesso")
                        if (::tflite.isInitialized) {
                            Log.d("CameraViewModel", "Modelo TFLite está inicializado")
                            detectVehicle(bitmap) { hasVehicle, rects ->
                                if (hasVehicle) {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        Toast.makeText(getApplication(), "Veículo detectado!", Toast.LENGTH_SHORT).show()
                                        drawOverlayOnPreview(previewView, rects)
                                    }
                                    drawBoundingBoxes(bitmap, rects)
                                    saveImage(bitmap)
                                    sendToPlateRecognizer(bitmap)
                                } else {
                                    Log.d("CameraViewModel", "Nenhum veículo detectado")
                                }
                            }
                        } else {
                            Log.e("CameraViewModel", "Modelo TFLite não está inicializado")
                        }
                        image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraViewModel", "Erro ao capturar imagem: ${exception.message}")
                    }
                }
            )
        }

        imageProxy.close()
    }

    private fun detectVehicle(bitmap: Bitmap, onResult: (Boolean, List<RectF>) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
                    .build()

                val tensorImage = TensorImage.fromBitmap(bitmap)
                val resizedImage = imageProcessor.process(tensorImage)

                val output = Array(1) { Array(10) { FloatArray(4) } }
                val classes = Array(1) { FloatArray(10) }
                val scores = Array(1) { FloatArray(10) }
                val numDetections = FloatArray(1)

                val outputMap = mapOf(
                    0 to output,
                    1 to classes,
                    2 to scores,
                    3 to numDetections
                )

                Log.d("TFLite", "Executando inferência")
                tflite.runForMultipleInputsOutputs(arrayOf(resizedImage.buffer), outputMap)

                val foundRects = mutableListOf<RectF>()

                for (i in 0 until numDetections[0].toInt()) {
                    if (scores[0][i] > 0.5f) {
                        val box = output[0][i]
                        foundRects.add(
                            RectF(
                                box[1] * bitmap.width,
                                box[0] * bitmap.height,
                                box[3] * bitmap.width,
                                box[2] * bitmap.height
                            )
                        )
                    }
                }

                Log.d("TFLite", "Inferência concluída. Veículo detectado: ${foundRects.isNotEmpty()}")
                onResult(foundRects.isNotEmpty(), foundRects)

            } catch (e: Exception) {
                Log.e("TFLite", "Erro na inferência: ${e.message}")
                onResult(false, emptyList())
            }
        }
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, boxes: List<RectF>) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        for (box in boxes) {
            canvas.drawRect(box, paint)
        }
    }

    private fun drawOverlayOnPreview(previewView: PreviewView, boxes: List<RectF>) {
        previewView.post {
            val overlayImage = Bitmap.createBitmap(previewView.width, previewView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(overlayImage)
            val paint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }

            for (box in boxes) {
                val rect = RectF(
                    box.left,
                    box.top,
                    box.right,
                    box.bottom
                )

                canvas.drawRect(rect, paint)
            }

            if (overlay == null) {
                overlay = ImageView(previewView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                (previewView.parent as? FrameLayout)?.addView(overlay)
            }

            overlay?.bringToFront()
            overlay?.setImageBitmap(overlayImage)
        }
    }

    private fun saveImage(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "vehicle_detected_$timestamp.jpg"
            val file = File(getApplication<Application>().getExternalFilesDir(null), fileName)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
            Log.d("CameraViewModel", "Imagem salva com sucesso: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Erro ao salvar imagem: ${e.message}")
        }
    }

    private fun sendToPlateRecognizer(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _detectionStatus.value = DetectionStatus(true, null)
                Log.d("PlateRecognizer", "Enviando imagem para PlateRecognizer")
                val result = plateRecognizerClient.sendImage(bitmap)
                _detectionStatus.value = DetectionStatus(false, result)
                Log.d("PlateRecognizer", "Resposta recebida: $result")
            } catch (e: Exception) {
                Log.e("PlateRecognizer", "Erro ao enviar imagem: ${e.message}")
                _detectionStatus.value = DetectionStatus(false, null)
            }
        }
    }
}

fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

data class DetectionStatus(
    val isSending: Boolean = false,
    val lastPlate: String? = null
)