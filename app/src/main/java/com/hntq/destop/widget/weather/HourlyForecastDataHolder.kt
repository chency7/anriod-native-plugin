package com.hntq.destop.widget.weather

object HourlyForecastDataHolder {
    var hourlyData: List<HourlyItem> = emptyList()

    data class HourlyItem(
        val time: String,
        val temp: Double,
        val skycon: String,
        val isMock: Boolean = false
    )
}
