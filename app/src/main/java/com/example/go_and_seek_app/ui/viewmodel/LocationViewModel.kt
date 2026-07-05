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
    val lat: Double? = null,
    val lng: Double? = null,
    val heading: Double? = null,
    val error: String? = null
)

class LocationViewModel(
    private val repository: LocationRepository = LocationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState

    fun fetchLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.value = LocationUiState(isLoading = true)
            val result = repository.getLocation(lat, lon)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = LocationUiState(
                        isLoading = false,
                        imageBase64 = response.image,
                        lat = response.lat,
                        lng = response.lng,
                        heading = response.heading
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
}
