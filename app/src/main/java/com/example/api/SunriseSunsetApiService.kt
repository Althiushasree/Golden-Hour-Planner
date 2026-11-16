package com.example.api

import com.example.model.SunriseSunsetResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SunriseSunsetApiService {

    @GET("json")
    suspend fun getSunriseSunset(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("date") date: String,
        @Query("formatted") formatted: Int = 0
    ): Response<SunriseSunsetResponse>
}
