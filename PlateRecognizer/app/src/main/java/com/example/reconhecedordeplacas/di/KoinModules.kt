package com.example.reconhecedordeplacas.di

import android.app.Application
import com.example.reconhecedordeplacas.api.PlateRecognizerApi
import com.example.reconhecedordeplacas.api.PlateRecognizerClient
import com.example.reconhecedordeplacas.viewmodel.CameraViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {

    single<Application> { androidContext() as Application }

    single {
        Retrofit.Builder()
            .baseUrl("https://api.platerecognizer.com")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor {
                        val request = it.request().newBuilder()
                            .addHeader("Authorization", "Token 1e4ea982cb8aefd6bbc62b1e59a7fc025beacdd8")
                            .build()
                        it.proceed(request)
                    }
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })

                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlateRecognizerApi::class.java)
    }

    single { PlateRecognizerClient(get()) }

    viewModel { CameraViewModel(get(), get()) }
}