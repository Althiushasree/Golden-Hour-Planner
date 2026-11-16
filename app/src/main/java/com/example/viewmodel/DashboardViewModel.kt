package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.location.LocationFetchResult
import com.example.location.LocationHelper
import com.example.model.CountdownInfo
import com.example.model.LocationCoordinates
import com.example.model.SolarTimings
import com.example.repository.SolarFetchResult
import com.example.repository.SolarRepository
import com.example.utils.SolarTimeUtils
import com.example.utils.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed class LocationUiState {
    data object Loading : LocationUiState()
    data class Success(val coordinates: LocationCoordinates) : LocationUiState()
    data object PermissionDenied : LocationUiState()
    data object ServicesDisabled : LocationUiState()
    data class Unavailable(val message: String) : LocationUiState()
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(application)
    private val solarRepository = SolarRepository(application)

    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.Loading)
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _solarState = MutableStateFlow<UiState<SolarTimings>>(UiState.Idle)
    val solarState: StateFlow<UiState<SolarTimings>> = _solarState.asStateFlow()

    private val _countdownState = MutableStateFlow<CountdownInfo?>(null)
    val countdownState: StateFlow<CountdownInfo?> = _countdownState.asStateFlow()

    private var currentCoordinates: LocationCoordinates? = null
    private var selectedTimingsCache: SolarTimings? = null
    private var todayTimingsCache: SolarTimings? = null
    private var tomorrowTimingsCache: SolarTimings? = null

    fun hasLocationPermission(): Boolean = locationHelper.hasLocationPermission()

    fun isLocationEnabled(): Boolean = locationHelper.isLocationEnabled()

    fun onPermissionDenied() {
        _locationState.value = LocationUiState.PermissionDenied
    }

    fun refreshLocationAndSolarData() {
        viewModelScope.launch {
            _locationState.value = LocationUiState.Loading
            when (val result = locationHelper.fetchCurrentLocation()) {
                is LocationFetchResult.Success -> {
                    currentCoordinates = result.coordinates
                    _locationState.value = LocationUiState.Success(result.coordinates)
                    fetchSolarTimingsInternal(result.coordinates, _selectedDate.value)
                }
                is LocationFetchResult.PermissionDenied -> {
                    _locationState.value = LocationUiState.PermissionDenied
                }
                is LocationFetchResult.ServicesDisabled -> {
                    _locationState.value = LocationUiState.ServicesDisabled
                }
                is LocationFetchResult.Unavailable -> {
                    _locationState.value = LocationUiState.Unavailable(result.message)
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (_selectedDate.value == date && selectedTimingsCache?.selectedDate == date) {
            return
        }
        _selectedDate.value = date
        val coords = currentCoordinates
        if (coords != null) {
            viewModelScope.launch {
                fetchSolarTimingsInternal(coords, date)
            }
        }
    }

    fun retrySolarFetch() {
        val coords = currentCoordinates
        if (coords == null) {
            refreshLocationAndSolarData()
        } else {
            viewModelScope.launch {
                fetchSolarTimingsInternal(coords, _selectedDate.value)
            }
        }
    }

    fun recalculateCountdown() {
        val selected = selectedTimingsCache ?: return
        val zoneId = ZoneId.systemDefault()
        val currentToday = LocalDate.now(zoneId)
        if (todayTimingsCache != null && todayTimingsCache?.selectedDate != currentToday) {
            if (tomorrowTimingsCache?.selectedDate == currentToday) {
                todayTimingsCache = tomorrowTimingsCache
                tomorrowTimingsCache = null
            }
            val coords = currentCoordinates
            if (coords != null) {
                viewModelScope.launch {
                    val tomorrowRes = solarRepository.fetchSolarTimings(
                        coords.latitude,
                        coords.longitude,
                        currentToday.plusDays(1)
                    )
                    if (tomorrowRes is SolarFetchResult.Success) {
                        tomorrowTimingsCache = tomorrowRes.timings
                        recalculateCountdown()
                    }
                }
            }
        }

        _countdownState.value = SolarTimeUtils.computeCountdown(
            now = Instant.now(),
            selectedDate = _selectedDate.value,
            selectedTimings = selected,
            todayTimings = todayTimingsCache,
            tomorrowTimings = tomorrowTimingsCache,
            zoneId = zoneId
        )
    }

    private suspend fun fetchSolarTimingsInternal(
        coords: LocationCoordinates,
        date: LocalDate
    ) {
        _solarState.value = UiState.Loading
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val selectedResult = solarRepository.fetchSolarTimings(
            latitude = coords.latitude,
            longitude = coords.longitude,
            date = date
        )

        when (selectedResult) {
            is SolarFetchResult.Success -> {
                val timings = selectedResult.timings
                selectedTimingsCache = timings
                if (date == today) {
                    todayTimingsCache = timings
                } else if (date == tomorrow) {
                    tomorrowTimingsCache = timings
                }

                viewModelScope.launch {
                    val todayDeferred = if (todayTimingsCache?.selectedDate != today) {
                        async { solarRepository.fetchSolarTimings(coords.latitude, coords.longitude, today) }
                    } else null
                    val tomorrowDeferred = if (tomorrowTimingsCache?.selectedDate != tomorrow) {
                        async { solarRepository.fetchSolarTimings(coords.latitude, coords.longitude, tomorrow) }
                    } else null

                    todayDeferred?.await()?.let { res ->
                        if (res is SolarFetchResult.Success) todayTimingsCache = res.timings
                    }
                    tomorrowDeferred?.await()?.let { res ->
                        if (res is SolarFetchResult.Success) tomorrowTimingsCache = res.timings
                    }
                    recalculateCountdown()
                }

                _solarState.value = UiState.Success(timings)
                recalculateCountdown()
            }
            is SolarFetchResult.Error -> {
                _solarState.value = UiState.Error(selectedResult.message)
                _countdownState.value = null
            }
        }
    }
}
