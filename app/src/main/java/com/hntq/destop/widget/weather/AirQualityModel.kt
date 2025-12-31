package com.hntq.destop.widget.weather

data class AirQualityCurrent(
    val us_aqi: Double?,
    val time: String?
)

data class AirQualityResponse(
    val current: AirQualityCurrent?
)

