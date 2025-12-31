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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HourlyForecastWidget : AppWidgetProvider() {

    companion object {
        private const val YYY_BASE_URL = "https://api.yyy001.com/"
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
        private const val OPEN_METEO_AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"
        private const val GEO_BASE_URL = "https://restapi.amap.com/"
        private const val ACTION_AUTO_UPDATE = "com.hntq.destop.widget.ACTION_HOURLY_AUTO_UPDATE"
        private const val ACTION_REFRESH = "com.hntq.destop.widget.ACTION_HOURLY_REFRESH"
        private const val UPDATE_INTERVAL_MILLIS = 1800000L

        private const val YYY_API_KEY = "f99f9dff-1679-d527-611c-97d8b06ae383eb736a39"
        private const val AMAP_API_KEY = "045af3b53e0e7f5078be868fbcf6af82"

        // 默认位置（天心区）
        private const val LAT = 28.035402
        private const val LON = 112.995397
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        startAlarm(context)
        val intent = Intent(context, HourlyForecastWidget::class.java).apply {
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

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 尺寸变化时触发刷新以重新生成适配大小的图表
        val intent = Intent(context, HourlyForecastWidget::class.java).apply { 
            action = ACTION_REFRESH 
        }
        context.sendBroadcast(intent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val action = intent.action
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || 
            action == ACTION_AUTO_UPDATE || 
            action == ACTION_REFRESH) {

            // 如果是用户点击刷新，给出提示
            if (action == ACTION_REFRESH) {
                android.widget.Toast.makeText(context, "正在更新天气...", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            val pendingResult = goAsync()
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HourlyForecastWidget::class.java)
            
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
        val intent = Intent(context, HourlyForecastWidget::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4001,
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
        val intent = Intent(context, HourlyForecastWidget::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateAppWidget(
        context: Context, 
        appWidgetManager: AppWidgetManager, 
        appWidgetId: Int, 
        onComplete: () -> Unit
    ) {
        val views = RemoteViews(context.packageName, R.layout.hourly_forecast_widget)
        val refreshIntent = Intent(context, HourlyForecastWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.hourly_root, pendingIntent)
        
        // 尝试从缓存恢复旧数据，避免闪烁成默认值
        try {
            val cache = WeatherCache.getCache(context)
            if (cache != null) {
                // 1. 恢复温度
                views.setTextViewText(R.id.hourly_temp, String.format("%.0f", cache.temp))
                
                // 2. 恢复天气描述和图标
                val (desc, _, _) = getWeatherResources(cache.skycon)
                views.setTextViewText(R.id.hourly_weather_desc, desc)
                
                // 3. 恢复 AQI
                val aqiStr = if (cache.aqiDesc.isNotEmpty()) "${cache.aqiDesc} ${cache.aqiVal}" 
                             else if (cache.aqiVal > 0) "${cache.aqiVal}" else "--"
                views.setTextViewText(R.id.hourly_aqi, aqiStr)
                
                // 4. 恢复位置
                views.setTextViewText(R.id.hourly_location, cache.location)
                
                // 5. 恢复图表（如果有单例数据）
                if (HourlyForecastDataHolder.hourlyData.isNotEmpty()) {
                    // 需要重新生成 Bitmap，因为 Bitmap 不能被缓存
                    // 这里简单处理：如果单例有数据就画，没有就算了
                    // 由于单例在进程重启后会丢，所以这里只能尽力而为
                    // 理想情况是将 List 也缓存到文件，但这里先只做 Header 的防闪烁
                }

                views.setViewVisibility(R.id.hourly_warning_icon, android.view.View.VISIBLE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            // 忽略缓存读取错误
        }

        try {
            // 定位逻辑（类似天气组件）
            var lat = LAT
            var lon = LON
            var isGpsUsed = true

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
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HourlyWidget", "Loc error", e)
                }
            }

            // 逆地理编码
            val geoRetrofit = Retrofit.Builder()
                .baseUrl(GEO_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val geoService = geoRetrofit.create(GeocodingService::class.java)
            val locationStr = "$lon,$lat"

            geoService.getAddress(AMAP_API_KEY, locationStr).enqueue(object : Callback<GeocodingResponse> {
                override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
                    try {
                        val regeocode = response.body()?.regeocode
                        val addressComponent = regeocode?.addressComponent
                        val cityObj = addressComponent?.city
                        val cityStr = if (cityObj is String && cityObj.isNotEmpty()) cityObj else (addressComponent?.province ?: "长沙市")
                        val district = addressComponent?.district
                        val cityForQuery = cityStr.replace("市", "")
                        val displayLocation = if (!district.isNullOrEmpty()) district else cityStr
                        
                        views.setTextViewText(R.id.hourly_location, if(isGpsUsed) displayLocation else "$displayLocation")
                        val finalLocation = if(isGpsUsed) displayLocation else "$displayLocation"
                        fetchWeather(context, cityForQuery, lat, lon, views, appWidgetManager, appWidgetId, finalLocation, onComplete)
                    } catch (e: Exception) {
                        views.setTextViewText(R.id.hourly_location, "定位解析错误")
                        fetchWeather(context, "定位解析错误", lat, lon, views, appWidgetManager, appWidgetId, "定位解析错误", onComplete)
                    }
                }

                override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                    views.setTextViewText(R.id.hourly_location, "定位失败")
                    fetchWeather(context, "定位失败", lat, lon, views, appWidgetManager, appWidgetId, "定位失败", onComplete)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("HourlyWidget", "Setup error", e)
            views.setTextViewText(R.id.hourly_location, "组件异常")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            onComplete()
        }
    }

    private fun fetchWeather(
        context: Context, 
        city: String, 
        lat: Double,
        lon: Double,
        views: RemoteViews, 
        appWidgetManager: AppWidgetManager, 
        appWidgetId: Int,
        locationName: String,
        onComplete: () -> Unit
    ) {
        fun fetchFromYyy() {
            try {
                val yyyRetrofit = Retrofit.Builder()
                    .baseUrl(YYY_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val yyyService = yyyRetrofit.create(YyyWeatherService::class.java)
                val location = "$lon,$lat"

                yyyService.getWeather(
                    apikey = YYY_API_KEY,
                    location = location,
                    city = city,
                    type = "realtime"
                ).enqueue(object : Callback<YyyWeatherResponse> {
                    override fun onResponse(call: Call<YyyWeatherResponse>, response: Response<YyyWeatherResponse>) {
                        val realtime = response.body()?.result?.realtime
                        val temp = realtime?.temperature
                        val skycon = realtime?.skycon
                        val aqiVal = realtime?.airQuality?.aqi?.chn
                        val aqiDesc = realtime?.airQuality?.description?.chn
                        val hasRealtime = temp != null || !skycon.isNullOrEmpty() || realtime?.airQuality != null
                        updateWidgetWithData(
                            context,
                            views,
                            appWidgetManager,
                            appWidgetId,
                            temp,
                            skycon,
                            aqiVal,
                            aqiDesc,
                            locationName,
                            hasRealtime
                        )
                        onComplete()
                    }

                    override fun onFailure(call: Call<YyyWeatherResponse>, t: Throwable) {
                        updateWidgetWithData(
                            context,
                            views,
                            appWidgetManager,
                            appWidgetId,
                            null,
                            null,
                            null,
                            null,
                            locationName,
                            false
                        )
                        onComplete()
                    }
                })
            } catch (e: Exception) {
                android.util.Log.e("HourlyWidget", "Fetch weather error", e)
                updateWidgetWithData(
                    context,
                    views,
                    appWidgetManager,
                    appWidgetId,
                    null,
                    null,
                    null,
                    null,
                    locationName,
                    false
                )
                onComplete()
            }
        }

        try {
            val weatherRetrofit = Retrofit.Builder()
                .baseUrl(OPEN_METEO_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val weatherService = weatherRetrofit.create(WeatherService::class.java)
            weatherService.getWeather(latitude = lat, longitude = lon).enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    val current = response.body()?.current
                    if (!response.isSuccessful || current == null) {
                        fetchFromYyy()
                        return
                    }

                    val temp = current.temperature_2m
                    val skycon = codeToSkycon(current.weather_code)
                    val hasRealtime = temp != null || !skycon.isNullOrEmpty()

                    val airRetrofit = Retrofit.Builder()
                        .baseUrl(OPEN_METEO_AIR_QUALITY_BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    val airService = airRetrofit.create(AirQualityService::class.java)
                    airService.getAirQuality(latitude = lat, longitude = lon).enqueue(object : Callback<AirQualityResponse> {
                        override fun onResponse(call: Call<AirQualityResponse>, response: Response<AirQualityResponse>) {
                            val (aqiVal, aqiDesc) = toAqiPair(response.body()?.current?.us_aqi)
                            updateWidgetWithData(
                                context,
                                views,
                                appWidgetManager,
                                appWidgetId,
                                temp,
                                skycon,
                                aqiVal,
                                aqiDesc,
                                locationName,
                                hasRealtime
                            )
                            onComplete()
                        }

                        override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                            updateWidgetWithData(
                                context,
                                views,
                                appWidgetManager,
                                appWidgetId,
                                temp,
                                skycon,
                                null,
                                null,
                                locationName,
                                hasRealtime
                            )
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

    private fun generateMockHourlyData(
        targetList: ArrayList<HourlyForecastDataHolder.HourlyItem>, 
        baseTemp: Double, 
        baseSkycon: String
    ) {
        targetList.clear()
        val now = System.currentTimeMillis()
        
        // 生成24小时的模拟数据
        for (i in 0 until 24) {
            val futureTime = now + i * 3600 * 1000L
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeStr = if (i == 0) "现在" else sdf.format(Date(futureTime))
            
            // 简单的模拟波动
            val fluctuation = when(i % 6) {
                0 -> 0.0
                1 -> -1.0
                2 -> -2.0
                3 -> -1.0
                4 -> 0.0
                5 -> 1.0
                else -> 0.0
            }
            val mockTemp = baseTemp + fluctuation
            
            val mockSkycon = if (i < 3) baseSkycon else when (i % 5) {
                0 -> "CLEAR_DAY"
                1 -> "CLOUDY"
                2 -> "PARTLY_CLOUDY_NIGHT"
                3 -> "RAIN"
                else -> "CLEAR_NIGHT"
            }
            
            targetList.add(HourlyForecastDataHolder.HourlyItem(
                time = timeStr,
                temp = mockTemp,
                skycon = mockSkycon,
                isMock = true
            ))
        }
    }

    private fun updateWidgetWithData(
        context: Context,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        temp: Double?,
        skyconInput: String?,
        aqiValInput: Int?,
        aqiDescInput: String?,
        locationName: String,
        hasRealtime: Boolean
    ) {
        try {
            val cache = WeatherCache.getCache(context)
            val tempVal = temp ?: cache?.temp
            if (tempVal != null) {
                views.setTextViewText(R.id.hourly_temp, String.format("%.0f", tempVal))
            } else {
                views.setTextViewText(R.id.hourly_temp, "--")
            }

            val skycon = skyconInput ?: cache?.skycon
            if (!skycon.isNullOrEmpty()) {
                val (desc, _, _) = getWeatherResources(skycon)
                views.setTextViewText(R.id.hourly_weather_desc, desc)
            } else {
                views.setTextViewText(R.id.hourly_weather_desc, "--")
            }

            val aqiDesc = aqiDescInput?.trim().takeIf { !it.isNullOrEmpty() } ?: cache?.aqiDesc.orEmpty()
            val aqiVal = aqiValInput ?: cache?.aqiVal ?: 0
            val aqiStr = if (aqiDesc.isNotEmpty()) "$aqiDesc $aqiVal" else if (aqiVal > 0) "$aqiVal" else "--"
            views.setTextViewText(R.id.hourly_aqi, aqiStr)

            views.setViewVisibility(R.id.hourly_warning_icon, android.view.View.VISIBLE)

            val displayLocation = if (locationName.contains("失败") || locationName.contains("错误")) {
                cache?.location ?: locationName
            } else {
                locationName
            }
            views.setTextViewText(R.id.hourly_location, displayLocation)

            if (hasRealtime && tempVal != null && !skycon.isNullOrEmpty()) {
                WeatherCache.saveCache(
                    context,
                    tempVal,
                    skycon,
                    aqiDesc,
                    aqiVal,
                    displayLocation
                )
            }

            if (hasRealtime || HourlyForecastDataHolder.hourlyData.isEmpty()) {
                val baseTemp = tempVal ?: 18.0
                val baseSkycon = skycon ?: "CLEAR_DAY"
                val newData = ArrayList<HourlyForecastDataHolder.HourlyItem>()
                generateMockHourlyData(newData, baseTemp, baseSkycon)
                HourlyForecastDataHolder.hourlyData = newData
            }

            if (HourlyForecastDataHolder.hourlyData.isNotEmpty()) {
                views.setViewVisibility(R.id.hourly_chart_view, android.view.View.VISIBLE)
                try {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

                    val density = context.resources.displayMetrics.density
                    val headerHeightDp = if (minHeightDp < 110) 40 else 60
                    val chartHeightDp = if (minHeightDp > 0) {
                        if (minHeightDp > headerHeightDp) minHeightDp - headerHeightDp else 50
                    } else {
                        100
                    }
                    val minReqHeight = (50 * density).toInt()
                    val reqHeight = (chartHeightDp * density).toInt().coerceAtLeast(minReqHeight).coerceAtMost(1200)

                    val reqWidth = if (minWidthDp > 0) (minWidthDp * density).toInt().coerceIn(100, 2000) else 800
                    val _reqWidth = reqWidth + 250

                    val chartBitmap = HourlyChartGenerator.generateChart(
                        context,
                        HourlyForecastDataHolder.hourlyData,
                        _reqWidth,
                        reqHeight
                    )
                    views.setImageViewBitmap(R.id.hourly_chart_view, chartBitmap)
                } catch (e: Exception) {
                    android.util.Log.e("HourlyWidget", "Chart error", e)
                    views.setViewVisibility(R.id.hourly_chart_view, android.view.View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.hourly_chart_view, android.view.View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            android.util.Log.e("HourlyWidget", "Update UI error", e)
            views.setTextViewText(R.id.hourly_location, "渲染错误")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun codeToSkycon(code: Int?): String? {
        return when (code) {
            0 -> "CLEAR_DAY"
            1, 2, 3 -> "PARTLY_CLOUDY_DAY"
            45, 48 -> "LIGHT_HAZE"
            51, 53, 55, 56, 57 -> "RAIN"
            61, 63, 65, 66, 67 -> "RAIN"
            71, 73, 75, 77, 85, 86 -> "SNOW"
            80, 81, 82 -> "RAIN"
            95, 96, 99 -> "RAIN"
            else -> null
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

    private fun getWeatherResources(skycon: String): Triple<String, Int, Int> {
        val s = skycon.uppercase(Locale.ROOT)
        return when {
            s.contains("CLEAR") -> Triple("晴", R.drawable.ic_weather_sun, R.drawable.bg_weather_sunny)
            s.contains("PARTLY_CLOUDY") -> Triple("多云", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            s == "CLOUDY" -> Triple("阴", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            s.contains("RAIN") -> Triple("雨", R.drawable.ic_rain, R.drawable.bg_weather_rain)
            s.contains("SNOW") -> Triple("雪", R.drawable.ic_rain, R.drawable.bg_weather_rain)
            s.contains("WIND") -> Triple("大风", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            s.contains("FOG") || s.contains("HAZE") -> Triple("雾霾", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
            else -> Triple("多云", R.drawable.ic_weather_cloud, R.drawable.bg_weather_cloudy)
        }
    }
}
