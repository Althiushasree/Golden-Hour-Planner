package com.example.repository

import android.content.Context
import com.example.R
import com.example.api.RetrofitClient
import com.example.api.SunriseSunsetApiService
import com.example.model.SolarTimings
import com.example.utils.SolarTimeUtils
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

sealed class SolarFetchResult {
    data class Success(val timings: SolarTimings) : SolarFetchResult()
    data class Error(val message: String) : SolarFetchResult()
}

class SolarRepository(
    private val context: Context,
    private val apiService: SunriseSunsetApiService = RetrofitClient.sunriseSunsetApi
) {

    suspend fun fetchSolarTimings(
        latitude: Double,
        longitude: Double,
        date: LocalDate
    ): SolarFetchResult {
        return try {
            val formattedDate = SolarTimeUtils.formatApiDate(date)
            val response = apiService.getSunriseSunset(
                latitude = latitude,
                longitude = longitude,
                date = formattedDate,
                formatted = 0
            )

            if (!response.isSuccessful) {
                return SolarFetchResult.Error(
                    context.getString(R.string.error_api_http, response.code())
                )
            }

            val body = response.body()
            if (body == null) {
                return SolarFetchResult.Error(
                    context.getString(R.string.error_api_invalid_response)
                )
            }

            if (!body.status.equals("OK", ignoreCase = true)) {
                val statusLabel = body.status ?: "UNKNOWN"
                return SolarFetchResult.Error(
                    context.getString(R.string.error_api_invalid_status, statusLabel)
                )
            }

            val results = body.results
                ?: return SolarFetchResult.Error(
                    context.getString(R.string.error_api_invalid_response)
                )

            val timings = SolarTimeUtils.toSolarTimings(
                selectedDate = date,
                results = results,
                zoneId = ZoneId.systemDefault(),
                lastUpdatedMillis = System.currentTimeMillis()
            )
            SolarFetchResult.Success(timings)
        } catch (_: IOException) {
            SolarFetchResult.Error(context.getString(R.string.error_no_internet))
        } catch (e: Exception) {
            SolarFetchResult.Error(
                e.localizedMessage ?: context.getString(R.string.error_api_invalid_response)
            )
        }
    }
}
