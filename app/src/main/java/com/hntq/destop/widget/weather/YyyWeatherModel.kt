package com.hntq.destop.widget.weather

import com.google.gson.annotations.SerializedName

data class YyyWeatherResponse(
    val status: String?, // 示例："ok"
    val result: YyyResult?
)

data class YyyResult(
    val realtime: YyyRealtime?,
    val hourly: YyyHourly?
)

data class YyyHourly(
    val status: String?,
    val temperature: List<YyyHourlyData>?,
    val skycon: List<YyyHourlySkycon>?,
    val precipitation: List<YyyHourlyData>?
)

data class YyyHourlyData(
    val datetime: String?,
    val value: Double?
)

data class YyyHourlySkycon(
    val datetime: String?,
    val value: String?
)

data class YyyRealtime(
    val status: String?, // 示例："ok"
    val temperature: Double?, // 示例：18.69
    val humidity: Double?, // 示例：0.42
    val skycon: String?, // 示例："PARTLY_CLOUDY_DAY"
    @SerializedName("air_quality") val airQuality: YyyAirQuality?
)

data class YyyAirQuality(
    val aqi: YyyAqi?,
    val description: YyyAqiDesc?
)

data class YyyAqi(
    val chn: Int? // 示例：36
)

data class YyyAqiDesc(
    val chn: String? // 示例："优"
)
