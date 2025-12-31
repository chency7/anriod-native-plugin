package com.hntq.destop.widget.weather

import android.content.Context
import android.content.SharedPreferences

object WeatherCache {
    private const val PREFS_NAME = "hourly_weather_cache"
    private const val KEY_TEMP = "temp"
    private const val KEY_SKYCON = "skycon"
    private const val KEY_AQI_DESC = "aqi_desc"
    private const val KEY_AQI_VAL = "aqi_val"
    private const val KEY_LOCATION = "location"
    private const val KEY_LAST_UPDATE = "last_update"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCache(
        context: Context,
        temp: Double,
        skycon: String,
        aqiDesc: String,
        aqiVal: Int,
        location: String
    ) {
        getPrefs(context).edit().apply {
            putFloat(KEY_TEMP, temp.toFloat())
            putString(KEY_SKYCON, skycon)
            putString(KEY_AQI_DESC, aqiDesc)
            putInt(KEY_AQI_VAL, aqiVal)
            putString(KEY_LOCATION, location)
            putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            apply()
        }
    }

    fun getCache(context: Context): CachedData? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_TEMP)) return null
        
        return CachedData(
            temp = prefs.getFloat(KEY_TEMP, 18.0f).toDouble(),
            skycon = prefs.getString(KEY_SKYCON, "CLEAR_DAY") ?: "CLEAR_DAY",
            aqiDesc = prefs.getString(KEY_AQI_DESC, "") ?: "",
            aqiVal = prefs.getInt(KEY_AQI_VAL, 0),
            location = prefs.getString(KEY_LOCATION, "正在定位") ?: "正在定位",
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        )
    }

    data class CachedData(
        val temp: Double,
        val skycon: String,
        val aqiDesc: String,
        val aqiVal: Int,
        val location: String,
        val lastUpdate: Long
    )
}
