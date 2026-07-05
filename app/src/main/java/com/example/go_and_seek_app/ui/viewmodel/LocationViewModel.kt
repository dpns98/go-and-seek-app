package com.example.go_and_seek_app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.go_and_seek_app.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LocationUiState(
    val isLoading: Boolean = false,
    val imageBase64: String? = null,
    val responseLat: Double? = null,
    val responseLng: Double? = null,
    val heading: Double? = null,
    val distanceMeters: Float? = null,
    val error: String? = null
)

class LocationViewModel(
    private val repository: LocationRepository = LocationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState

    fun fetchInitialLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.value = LocationUiState(isLoading = true)
            val result = repository.getLocation(lat, lon)
            result.fold(
                onSuccess = { response ->
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(lat, lon, response.lat, response.lng, results)
                    _uiState.value = LocationUiState(
                        isLoading = false,
                        imageBase64 = response.image,
                        responseLat = response.lat,
                        responseLng = response.lng,
                        heading = response.heading,
                        distanceMeters = results[0]
                    )
                },
                onFailure = { error ->
                    _uiState.value = LocationUiState(
                        isLoading = false,
                        error = error.message ?: "Unknown error"
                    )
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = LocationUiState()
    }

    fun updateDeviceLocation(deviceLat: Double, deviceLon: Double) {
        val current = _uiState.value
        val rLat = current.responseLat ?: return
        val rLng = current.responseLng ?: return
        val results = FloatArray(1)
        android.location.Location.distanceBetween(deviceLat, deviceLon, rLat, rLng, results)
        _uiState.value = current.copy(distanceMeters = results[0])
    }
}
