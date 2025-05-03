package com.example.reconhecedordeplacas.api

data class PlateRecognitionResponse(
    val results: List<PlateResult>
)

data class PlateResult(
    val plate: String,
    val vehicle: Vehicle?
)

data class Vehicle(
    val make_model: List<MakeModel>?
)

data class MakeModel(
    val name: String
)