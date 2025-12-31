package com.hntq.destop.widget.weather

data class WeatherCurrent(
    val temperature_2m: Double?,
    val weather_code: Int?,
    val time: String?
)

data class WeatherDaily(
    val temperature_2m_max: List<Double>?,
    val temperature_2m_min: List<Double>?,
    val time: List<String>?
)

data class WeatherResponse(
    val current: WeatherCurrent?,
    val daily: WeatherDaily?
)

