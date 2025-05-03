package com.example.reconhecedordeplacas.api


import android.graphics.Bitmap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class PlateRecognizerClient(private val api: PlateRecognizerApi) {

    suspend fun sendImage(bitmap: Bitmap): PlateRecognitionResponse {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val imageBytes = stream.toByteArray()

        val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
        val imagePart = MultipartBody.Part.createFormData("upload", "image.jpg", requestBody)

        return api.recognizePlate(imagePart)
    }
}