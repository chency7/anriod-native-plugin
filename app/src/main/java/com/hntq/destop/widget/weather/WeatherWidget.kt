package com.hntq.destop.widget.weather

import com.hntq.destop.widget.R
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
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
        private const val OPEN_METEO_AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"
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
        val intent = Intent(context, WeatherWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        context.sendBroadcast(intent)
    }

    override fun onEnabled(context: Context) {
        startAlarm(context)
    }

    override fun onDisabled(context: Context) {
        stopAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val action = intent.action
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || 
            action == ACTION_AUTO_UPDATE || 
            action == ACTION_REFRESH) {

            if (action == ACTION_REFRESH) {
                android.widget.Toast.makeText(context, "正在更新天气...", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            val pendingResult = goAsync()
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidget::class.java)
            
            val appWidgetIds = if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
                intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: appWidgetManager.getAppWidgetIds(componentName)
            } else {
                appWidgetManager.getAppWidgetIds(componentName)
            }

            if (appWidgetIds == null || appWidgetIds.isEmpty()) {
                pendingResult.finish()
                return
            }

            val counter = java.util.concurrent.atomic.AtomicInteger(appWidgetIds.size)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId) {
                    if (counter.decrementAndGet() == 0) {
                        pendingResult.finish()
                    }
                }
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

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, onComplete: () -> Unit) {
        val views = RemoteViews(context.packageName, R.layout.weather_widget)
        val refreshIntent = Intent(context, WeatherWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
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
            onComplete()
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
                
                fetchWeather(context, cityForQuery, lat, lon, views, appWidgetManager, appWidgetId, onComplete)
            }

            override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                android.util.Log.e("WeatherWidget", "Geo failed: ${t.message}")
                // 降级到深圳
                views.setTextViewText(R.id.weather_location, "定位失败")
                fetchWeather(context, "定位失败", lat, lon, views, appWidgetManager, appWidgetId, onComplete)
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
        appWidgetId: Int,
        onComplete: () -> Unit
    ) {
        fun fetchFromYyy() {
            val yyyRetrofit = Retrofit.Builder()
                .baseUrl(YYY_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val yyyService = yyyRetrofit.create(YyyWeatherService::class.java)

            val location = "$lon,$lat"
            yyyService.getWeather(
                apikey = YYY_API_KEY,
                location = location,
                city = city
            ).enqueue(object : Callback<YyyWeatherResponse> {
                override fun onResponse(call: Call<YyyWeatherResponse>, response: Response<YyyWeatherResponse>) {
                    try {
                        val body = response.body()
                        val result = body?.result
                        val realtime = result?.realtime
                        if (body?.status == "ok" && realtime != null) {
                            val tempVal = realtime.temperature
                            if (tempVal != null) {
                                views.setTextViewText(R.id.weather_temp, String.format("%.0f", tempVal))
                            }

                            val skycon = realtime.skycon ?: "CLEAR_DAY"
                            val (desc, iconRes, bgRes) = getWeatherResources(skycon)
                            views.setTextViewText(R.id.weather_desc, desc)
                            views.setImageViewResource(R.id.weather_icon, iconRes)
                            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

                            val humidityVal = ((realtime.humidity ?: 0.0) * 100).coerceIn(0.0, 100.0)
                            views.setTextViewText(R.id.weather_high_low, String.format("湿度 %.0f%%", humidityVal))

                            val aqiVal = realtime.airQuality?.aqi?.chn
                            val aqiDesc = realtime.airQuality?.description?.chn
                            views.setTextViewText(R.id.weather_aqi, formatAqiText(aqiVal, aqiDesc))
                        } else {
                            views.setTextViewText(R.id.weather_desc, body?.status?.takeIf { it.isNotBlank() } ?: "请求失败")
                        }
                    } finally {
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        onComplete()
                    }
                }

                override fun onFailure(call: Call<YyyWeatherResponse>, t: Throwable) {
                    views.setTextViewText(R.id.weather_desc, "网络错误")
                    views.setTextViewText(R.id.weather_aqi, "--")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    onComplete()
                }
            })
        }

        try {
            val weatherRetrofit = Retrofit.Builder()
                .baseUrl(OPEN_METEO_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val weatherService = weatherRetrofit.create(WeatherService::class.java)

            weatherService.getWeather(latitude = lat, longitude = lon).enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    val body = response.body()
                    val current = body?.current
                    if (!response.isSuccessful || current == null) {
                        fetchFromYyy()
                        return
                    }

                    val tempVal = current.temperature_2m
                    if (tempVal != null) {
                        views.setTextViewText(R.id.weather_temp, String.format("%.0f", tempVal))
                    }

                    val (desc, iconRes, bgRes) = getWeatherResourcesFromCode(current.weather_code)
                    views.setTextViewText(R.id.weather_desc, desc)
                    views.setImageViewResource(R.id.weather_icon, iconRes)
                    views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

                    val humidityVal = current.relative_humidity_2m
                    if (humidityVal != null) {
                        views.setTextViewText(R.id.weather_high_low, String.format("湿度 %.0f%%", humidityVal.coerceIn(0.0, 100.0)))
                    } else {
                        views.setTextViewText(R.id.weather_high_low, "")
                    }

                    val airRetrofit = Retrofit.Builder()
                        .baseUrl(OPEN_METEO_AIR_QUALITY_BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    val airService = airRetrofit.create(AirQualityService::class.java)
                    airService.getAirQuality(latitude = lat, longitude = lon).enqueue(object : Callback<AirQualityResponse> {
                        override fun onResponse(call: Call<AirQualityResponse>, response: Response<AirQualityResponse>) {
                            val aqi = response.body()?.current?.us_aqi
                            val (aqiVal, aqiDesc) = toAqiPair(aqi)
                            views.setTextViewText(R.id.weather_aqi, formatAqiText(aqiVal, aqiDesc))
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                            onComplete()
                        }

                        override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                            views.setTextViewText(R.id.weather_aqi, "--")
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                            onComplete()
                        }
                    })
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    fetchFromYyy()
                }
            })
        } catch (e: Exception) {
            fetchFromYyy()
        }
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

    private fun getWeatherResourcesFromCode(code: Int?): Triple<String, Int, Int> {
        return when (code) {
            0 -> Triple("晴", R.drawable.ic_weather_sun, R.drawable.bg_weather_sunny)
            1, 2, 3 -> Triple("多云", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            45, 48 -> Triple("雾霾", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Triple("雨", R.drawable.ic_rain, R.drawable.bg_weather_rain)
            71, 73, 75, 77, 85, 86 -> Triple("雪", R.drawable.ic_rain, R.drawable.bg_weather_rain)
            95, 96, 99 -> Triple("雷雨", R.drawable.ic_rain, R.drawable.bg_weather_rain)
            else -> Triple("多云", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
        }
    }

    private fun toAqiPair(usAqi: Double?): Pair<Int?, String?> {
        val v = usAqi?.toInt()
        val desc = when {
            v == null -> null
            v <= 50 -> "优"
            v <= 100 -> "良"
            v <= 150 -> "轻度污染"
            v <= 200 -> "中度污染"
            v <= 300 -> "重度污染"
            else -> "严重污染"
        }
        return Pair(v, desc)
    }

    private fun formatAqiText(aqiVal: Int?, aqiDesc: String?): String {
        val v = aqiVal ?: 0
        val d = aqiDesc?.trim().orEmpty()
        return when {
            d.isNotEmpty() && v > 0 -> "$d $v"
            d.isNotEmpty() -> d
            v > 0 -> v.toString()
            else -> "--"
        }
    }
}
