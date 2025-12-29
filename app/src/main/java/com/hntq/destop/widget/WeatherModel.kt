package com.hntq.destop.widget

data class WeatherCurrent(
    val temperature_2m: Double?,
    val precipitation: Double?,
    val time: String?
)

data class WeatherResponse(
    val current: WeatherCurrent?
)

