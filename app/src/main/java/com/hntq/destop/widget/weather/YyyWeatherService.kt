package com.hntq.destop.widget.weather

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface YyyWeatherService {
    @GET("api/cyweather")
    fun getWeather(
        @Query("apikey") apikey: String,
        @Query("type") type: String = "realtime",
        @Query("location") location: String,
        @Query("city") city: String,
        @Query("alert") alert: String = "true",
        @Query("hours") hours: Int = 1,
        @Query("days") days: Int = 1,
        @Query("dailysteps") dailysteps: Int = 7,
        @Query("hourlysteps") hourlysteps: Int = 24
    ): Call<YyyWeatherResponse>
}
