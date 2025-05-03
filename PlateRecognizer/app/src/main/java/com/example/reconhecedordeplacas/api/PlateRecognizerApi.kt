package com.example.reconhecedordeplacas.api

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PlateRecognizerApi {
    @Multipart
    @POST("v1/plate-reader/")
    suspend fun recognizePlate(
        @Part image: MultipartBody.Part
    ): PlateRecognitionResponse
}