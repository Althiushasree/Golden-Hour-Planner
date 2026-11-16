package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.api.SunriseSunsetApiService
import com.example.model.SolarResults
import com.example.model.SunriseSunsetResponse
import com.example.repository.SolarFetchResult
import com.example.repository.SolarRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SolarRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `fetchSolarTimings returns Success when API status is OK`() = runTest {
        val fakeApi = object : SunriseSunsetApiService {
            override suspend fun getSunriseSunset(
                latitude: Double,
                longitude: Double,
                date: String,
                formatted: Int
            ): Response<SunriseSunsetResponse> {
                assertEquals(0, formatted)
                assertEquals("2026-11-16", date)
                return Response.success(
                    SunriseSunsetResponse(
                        status = "OK",
                        results = SolarResults(
                            sunrise = "2026-11-16T14:50:00+00:00",
                            sunset = "2026-11-17T00:58:00+00:00",
                            solarNoon = "2026-11-16T19:54:00+00:00",
                            dayLength = "36480",
                            civilTwilightBegin = "2026-11-16T14:21:00+00:00",
                            civilTwilightEnd = "2026-11-17T01:27:00+00:00",
                            nauticalTwilightBegin = null,
                            nauticalTwilightEnd = null,
                            astronomicalTwilightBegin = null,
                            astronomicalTwilightEnd = null
                        )
                    )
                )
            }
        }

        val repository = SolarRepository(context, fakeApi)
        val result = repository.fetchSolarTimings(37.7749, -122.4194, LocalDate.of(2026, 11, 16))
        assertTrue(result is SolarFetchResult.Success)
        val timings = (result as SolarFetchResult.Success).timings
        assertEquals(36480L, timings.dayLengthSeconds)
    }

    @Test
    fun `fetchSolarTimings returns Error when API status is not OK`() = runTest {
        val fakeApi = object : SunriseSunsetApiService {
            override suspend fun getSunriseSunset(
                latitude: Double,
                longitude: Double,
                date: String,
                formatted: Int
            ): Response<SunriseSunsetResponse> {
                return Response.success(
                    SunriseSunsetResponse(status = "INVALID_REQUEST", results = null)
                )
            }
        }

        val repository = SolarRepository(context, fakeApi)
        val result = repository.fetchSolarTimings(37.7749, -122.4194, LocalDate.of(2026, 11, 16))
        assertTrue(result is SolarFetchResult.Error)
        assertTrue((result as SolarFetchResult.Error).message.contains("INVALID_REQUEST"))
    }

    @Test
    fun `fetchSolarTimings returns Error on HTTP 500`() = runTest {
        val fakeApi = object : SunriseSunsetApiService {
            override suspend fun getSunriseSunset(
                latitude: Double,
                longitude: Double,
                date: String,
                formatted: Int
            ): Response<SunriseSunsetResponse> {
                return Response.error(
                    500,
                    "Server Error".toResponseBody("text/plain".toMediaType())
                )
            }
        }

        val repository = SolarRepository(context, fakeApi)
        val result = repository.fetchSolarTimings(37.7749, -122.4194, LocalDate.of(2026, 11, 16))
        assertTrue(result is SolarFetchResult.Error)
        assertTrue((result as SolarFetchResult.Error).message.contains("500"))
    }

    @Test
    fun `fetchSolarTimings returns no internet Error on IOException`() = runTest {
        val fakeApi = object : SunriseSunsetApiService {
            override suspend fun getSunriseSunset(
                latitude: Double,
                longitude: Double,
                date: String,
                formatted: Int
            ): Response<SunriseSunsetResponse> {
                throw IOException("Network unreachable")
            }
        }

        val repository = SolarRepository(context, fakeApi)
        val result = repository.fetchSolarTimings(37.7749, -122.4194, LocalDate.of(2026, 11, 16))
        assertTrue(result is SolarFetchResult.Error)
        assertEquals(context.getString(R.string.error_no_internet), (result as SolarFetchResult.Error).message)
    }
}
