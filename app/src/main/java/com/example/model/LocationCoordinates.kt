package com.example.model

data class LocationCoordinates(
    val latitude: Double,
    val longitude: Double,
    val localityName: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
