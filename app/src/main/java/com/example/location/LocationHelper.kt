package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.R
import com.example.model.LocationCoordinates
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

sealed class LocationFetchResult {
    data class Success(val coordinates: LocationCoordinates) : LocationFetchResult()
    data object PermissionDenied : LocationFetchResult()
    data object ServicesDisabled : LocationFetchResult()
    data class Unavailable(val message: String) : LocationFetchResult()
}

class LocationHelper(private val context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun isLocationEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): LocationFetchResult {
        if (!hasLocationPermission()) {
            return LocationFetchResult.PermissionDenied
        }
        if (!isLocationEnabled()) {
            return LocationFetchResult.ServicesDisabled
        }

        return try {
            val location = fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()

            if (location == null) {
                LocationFetchResult.Unavailable(context.getString(R.string.location_unavailable))
            } else {
                val locality = resolveLocalityName(location.latitude, location.longitude)
                LocationFetchResult.Success(
                    LocationCoordinates(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        localityName = locality,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: SecurityException) {
            LocationFetchResult.PermissionDenied
        } catch (e: Exception) {
            LocationFetchResult.Unavailable(
                e.localizedMessage ?: context.getString(R.string.location_unavailable)
            )
        }
    }

    private suspend fun resolveLocalityName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            val first = addresses.firstOrNull()
                            val label = listOfNotNull(
                                first?.locality ?: first?.subAdminArea,
                                first?.adminArea,
                                first?.countryCode
                            ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
                            if (continuation.isActive) {
                                continuation.resume(label)
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val first = addresses?.firstOrNull()
                    listOfNotNull(
                        first?.locality ?: first?.subAdminArea,
                        first?.adminArea,
                        first?.countryCode
                    ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
