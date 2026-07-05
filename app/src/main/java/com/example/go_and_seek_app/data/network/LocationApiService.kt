package com.example.go_and_seek_app.data.network

import com.example.go_and_seek_app.data.model.LocationResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LocationApiService {
    @GET("location")
    suspend fun getLocation(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("width") width: Int,
        @Query("height") height: Int
    ): LocationResponse
}
