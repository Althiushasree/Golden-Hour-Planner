package com.example.model

import com.google.gson.annotations.SerializedName

data class SunriseSunsetResponse(
    @SerializedName("results") val results: SolarResults?,
    @SerializedName("status") val status: String?
)

data class SolarResults(
    @SerializedName("sunrise") val sunrise: String?,
    @SerializedName("sunset") val sunset: String?,
    @SerializedName("solar_noon") val solarNoon: String?,
    @SerializedName("day_length") val dayLength: String?,
    @SerializedName("civil_twilight_begin") val civilTwilightBegin: String?,
    @SerializedName("civil_twilight_end") val civilTwilightEnd: String?,
    @SerializedName("nautical_twilight_begin") val nauticalTwilightBegin: String?,
    @SerializedName("nautical_twilight_end") val nauticalTwilightEnd: String?,
    @SerializedName("astronomical_twilight_begin") val astronomicalTwilightBegin: String?,
    @SerializedName("astronomical_twilight_end") val astronomicalTwilightEnd: String?
)
