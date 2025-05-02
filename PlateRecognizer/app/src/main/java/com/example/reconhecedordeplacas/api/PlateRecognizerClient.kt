package com.example.reconhecedordeplacas.api

import android.graphics.Bitmap
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class PlateRecognizerClient(
    private val api: PlateRecognizerApi
) {
    suspend fun sendImage(bitmap: Bitmap): String? {
        val imagePart = bitmap.toMultipartBody("image.jpg")

        val response = api.recognizePlate(
            image = imagePart,
            region = "br".toRequestBody("text/plain".toMediaType())
        )

        return if (response.isSuccessful) {
            response.body()?.string()
        } else {
            Log.e("PlateRecognizer", "Erro: ${response.code()}")
            null
        }
    }
}