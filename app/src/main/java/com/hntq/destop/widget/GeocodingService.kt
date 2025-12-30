package com.hntq.destop.widget

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GeocodingService {
    @GET("v3/geocode/regeo")
    fun getAddress(
        @Query("key") key: String,
        @Query("location") location: String,
        @Query("extensions") extensions: String = "base",
        @Query("output") output: String = "json"
    ): Call<GeocodingResponse>
}