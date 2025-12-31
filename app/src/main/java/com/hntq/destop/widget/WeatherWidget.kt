package com.hntq.destop.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class WeatherWidget : AppWidgetProvider() {
    companion object {
        private const val YYY_BASE_URL = "https://api.yyy001.com/"
        private const val GEO_BASE_URL = "https://restapi.amap.com/"
        private const val ACTION_AUTO_UPDATE = "com.hntq.destop.widget.ACTION_WEATHER_AUTO_UPDATE"
        private const val ACTION_REFRESH = "com.hntq.destop.widget.ACTION_WEATHER_REFRESH"
        private const val UPDATE_INTERVAL_MILLIS = 60_000L
        private const val YYY_API_KEY = "f99f9dff-1679-d527-611c-97d8b06ae383eb736a39"
        private const val AMAP_API_KEY = "045af3b53e0e7f5078be868fbcf6af82"
        // 默认坐标（天心区）
        private const val LAT = 28.035402
        private const val LON = 112.995397
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        startAlarm(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        startAlarm(context)
    }

    override fun onDisabled(context: Context) {
        stopAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_AUTO_UPDATE || intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun startAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeatherWidget::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime(),
            UPDATE_INTERVAL_MILLIS,
            pendingIntent
        )
    }

    private fun stopAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeatherWidget::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.weather_widget)
        // 尝试获取位置
        var lat = LAT
        var lon = LON
        var isGpsUsed = false

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
             context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val providers = locationManager.getProviders(true)
                var bestLocation: android.location.Location? = null

                for (provider in providers) {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                }

                if (bestLocation != null) {
                    lat = bestLocation.latitude
                    lon = bestLocation.longitude
                    isGpsUsed = true
                    android.util.Log.d("WeatherWidget", "Using GPS Location:$lon , $lat")
                }
            } catch (e: Exception) {
                android.util.Log.e("WeatherWidget", "Failed to get location", e)
            }
        } else {
             views.setTextViewText(R.id.weather_location, "需定位权限")
        }

        // 高德地图逆地理编码服务
        val geoRetrofit = Retrofit.Builder()
            .baseUrl(GEO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val geoService = geoRetrofit.create(GeocodingService::class.java)

        // 1. 优先获取位置名称
        // 高德地图位置格式："经度,纬度"
        val locationStr = "$lon,$lat"
        
        if (AMAP_API_KEY == "YOUR_AMAP_KEY_HERE") {
            views.setTextViewText(R.id.weather_location, "需高德Key")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        geoService.getAddress(AMAP_API_KEY, locationStr).enqueue(object : Callback<GeocodingResponse> {
            override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
                val regeocode = response.body()?.regeocode
                val addressComponent = regeocode?.addressComponent
                
                // 处理高德城市字段：可能是字符串或空列表（如果为空/无效则使用省份）
                val cityObj = addressComponent?.city
                val cityStr = if (cityObj is String && cityObj.isNotEmpty()) {
                    cityObj
                } else {
                    addressComponent?.province ?: "长沙市"
                }
                
                val district = addressComponent?.district
                
                // 使用城市进行天气查询，移除“市”
                val cityForQuery = cityStr.replace("市", "")
                
                val displayLocation = if (!district.isNullOrEmpty()) district else cityStr
                views.setTextViewText(R.id.weather_location, if(isGpsUsed) displayLocation else "$displayLocation")
                
                fetchWeather(context, cityForQuery, lat, lon, views, appWidgetManager, appWidgetId)
            }

            override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                android.util.Log.e("WeatherWidget", "Geo failed: ${t.message}")
                // 降级到深圳
                views.setTextViewText(R.id.weather_location, "定位失败")
                fetchWeather(context, "定位失败", lat, lon, views, appWidgetManager, appWidgetId)
            }
        })
    }

    private fun fetchWeather(
        context: Context, 
        city: String, 
        lat: Double,
        lon: Double,
        views: RemoteViews, 
        appWidgetManager: AppWidgetManager, 
        appWidgetId: Int
    ) {
        // Yyy 天气服务
        val yyyRetrofit = Retrofit.Builder()
            .baseUrl(YYY_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val yyyService = yyyRetrofit.create(YyyWeatherService::class.java)


        val location = "$lon,$lat"
        android.util.Log.d("WeatherWidget-params", "fetchWeather params: city=$city, location=$location, apikey=$YYY_API_KEY")
        yyyService.getWeather(
            apikey = YYY_API_KEY,
            location = location,
            city = city
        ).enqueue(object : Callback<YyyWeatherResponse> {
            override fun onResponse(call: Call<YyyWeatherResponse>, response: Response<YyyWeatherResponse>) {
                val body = response.body()
                val result = body?.result
                val realtime = result?.realtime

                android.util.Log.d("WeatherWidget-$city", "YYYY Response: $body")
                
                if (body?.status == "ok" && result != null && realtime != null) {
                    // 温度
                    val tempVal = realtime.temperature ?: 0.0
                    val temp = String.format("%.0f", tempVal) // 18.69 -> 19
                    views.setTextViewText(R.id.weather_temp, temp)
                    
                    // 天气类型和图标
                    val skycon = realtime.skycon ?: "CLEAR_DAY"
                    val (desc, iconRes, bgRes) = getWeatherResources(skycon)
                    views.setTextViewText(R.id.weather_desc, desc)
                    views.setImageViewResource(R.id.weather_icon, iconRes)
                    views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
                    
                    // 湿度
                    val humidityVal = ((realtime.humidity ?: 0.0) * 100).coerceIn(0.0, 100.0)
                    val humidityStr = String.format("湿度 %.0f%%", humidityVal)
                    views.setTextViewText(R.id.weather_high_low, humidityStr)
                    
                    // 空气质量
                    val aqiVal = realtime.airQuality?.aqi?.chn ?: 0
                    val aqiDesc = realtime.airQuality?.description?.chn ?: ""
                    val aqiStr = if (aqiDesc.isNotEmpty()) "$aqiDesc $aqiVal" else "$aqiVal"
                    views.setTextViewText(R.id.weather_aqi, aqiStr)
                } else {
                    views.setTextViewText(R.id.weather_desc, body?.status ?: "API Err")
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            override fun onFailure(call: Call<YyyWeatherResponse>, t: Throwable) {
                views.setTextViewText(R.id.weather_desc, "Net Err")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        })
    }
    
    private fun getWeatherResources(skycon: String): Triple<String, Int, Int> {
        // 基于彩云 API 的 Skycon 映射（假设与 YYY 类似，YYY 似乎是对其的封装/模仿）
        // CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT, CLOUDY, LIGHT_HAZE, ...
        val s = skycon.uppercase(Locale.ROOT)
        return when {
            s.contains("CLEAR") -> Triple("晴", R.drawable.ic_weather_sun, R.drawable.bg_weather_sunny)
            s.contains("PARTLY_CLOUDY") -> Triple("多云", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            s == "CLOUDY" -> Triple("阴", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            s.contains("RAIN") -> Triple("雨", R.drawable.ic_rain, R.drawable.bg_weather_rain)
            s.contains("SNOW") -> Triple("雪", R.drawable.ic_rain, R.drawable.bg_weather_rain) // 需要雪的图标？
            s.contains("WIND") -> Triple("大风", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            s.contains("FOG") || s.contains("HAZE") -> Triple("雾霾", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            else -> Triple("多云", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
        }
    }
}
