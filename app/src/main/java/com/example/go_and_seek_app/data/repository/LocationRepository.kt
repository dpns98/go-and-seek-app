package com.example.go_and_seek_app.data.repository

import com.example.go_and_seek_app.data.model.LocationResponse
import com.example.go_and_seek_app.data.network.RetrofitClient

class LocationRepository {
    private val api = RetrofitClient.locationApiService

    suspend fun getLocation(lat: Double, lon: Double): Result<LocationResponse> {
        return try {
            val response = api.getLocation(lat, lon)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
