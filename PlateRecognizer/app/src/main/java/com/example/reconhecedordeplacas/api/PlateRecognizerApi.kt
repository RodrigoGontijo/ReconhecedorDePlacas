package com.example.reconhecedordeplacas.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

interface PlateRecognizerApi {
    @retrofit2.http.Multipart
    @retrofit2.http.POST("v1/plate-reader/")
    suspend fun recognizePlate(
        @retrofit2.http.Part image: MultipartBody.Part,
        @retrofit2.http.Part("regions") region: okhttp3.RequestBody = "br".toRequestBody("text/plain".toMediaType())
    ): Response<ResponseBody>
}