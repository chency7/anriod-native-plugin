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
        // 注意：更新逻辑已移至 onReceive 以支持异步保活（goAsync）
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
        try {
            val yyyRetrofit = Retrofit.Builder()
            .baseUrl(YYY_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            val yyyService = yyyRetrofit.create(YyyWeatherService::class.java)
            val location = "$lon,$lat"
    
            // 使用网络请求获取天气数据（仅实时）
            yyyService.getWeather(
                apikey = YYY_API_KEY,
                location = location,
                city = city,
                type = "realtime"
            ).enqueue(object : Callback<YyyWeatherResponse> {
                override fun onResponse(call: Call<YyyWeatherResponse>, response: Response<YyyWeatherResponse>) {
                    try {
                        val body = response.body()
                        val result = body?.result
                        updateWidgetWithData(context, views, appWidgetManager, appWidgetId, result, locationName)
                    } catch (e: Exception) {
                        updateWidgetWithData(context, views, appWidgetManager, appWidgetId, null, locationName)
                    } finally {
                        onComplete()
                    }
                }
    
                override fun onFailure(call: Call<YyyWeatherResponse>, t: Throwable) {
                    // 网络请求失败，使用完全模拟的数据
                    updateWidgetWithData(context, views, appWidgetManager, appWidgetId, null, locationName)
                    onComplete()
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("HourlyWidget", "Fetch weather error", e)
            updateWidgetWithData(context, views, appWidgetManager, appWidgetId, null, locationName)
            onComplete()
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
        result: YyyResult?,
        locationName: String
    ) {
        try {
            val realtime = result?.realtime
            // 忽略 API 返回的小时数据，强制使用 Mock

            // 1. 更新头部信息
            // 温度
            val tempVal = realtime?.temperature ?: 18.0
            views.setTextViewText(R.id.hourly_temp, String.format("%.0f", tempVal))
            
            // 天气状况
            val skycon = realtime?.skycon ?: "CLEAR_DAY"
            val (desc, _, _) = getWeatherResources(skycon)
            views.setTextViewText(R.id.hourly_weather_desc, desc)
            
            // 空气质量
            val aqiDesc = realtime?.airQuality?.description?.chn ?: ""
            val aqiVal = realtime?.airQuality?.aqi?.chn ?: 0
            // 显示格式：优 45
            val aqiStr = if (aqiDesc.isNotEmpty()) "$aqiDesc $aqiVal" else if (aqiVal > 0) "$aqiVal" else "--" // 默认模拟值
            views.setTextViewText(R.id.hourly_aqi, aqiStr)

            // 保存到缓存
            if (result != null) {
                WeatherCache.saveCache(
                    context,
                    tempVal,
                    skycon,
                    aqiDesc,
                    aqiVal,
                    locationName
                )
            }
            
            // 2. 准备列表数据并更新 Holder（全部使用模拟数据）
            val newData = ArrayList<HourlyForecastDataHolder.HourlyItem>()
            generateMockHourlyData(newData, tempVal, skycon)
            
            // 更新单例数据
            HourlyForecastDataHolder.hourlyData = newData

            views.setViewVisibility(R.id.hourly_chart_view, android.view.View.VISIBLE)

            try {
                // 获取 Widget 当前尺寸
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                
                // 转换为像素，并设置合理的最小值和最大值
                val density = context.resources.displayMetrics.density
                
                // Header 高度估算：
                // 基础高度：36sp(temp) + 12sp(desc) + margins ≈ 50-60dp
                // 为了兼容大字体，预留稍多一点的空间 (60dp)，并根据总高度动态调整
                // 如果总高度很小（<110dp），压缩 Header 占比，优先保证图表至少有 50dp
                val headerHeightDp = if (minHeightDp < 110) 40 else 60
                
                // 计算图表可用高度
                // 如果获取不到 minHeightDp（例如某些 Launcher 返回 0），默认给 100dp
                val chartHeightDp = if (minHeightDp > 0) {
                    if (minHeightDp > headerHeightDp) minHeightDp - headerHeightDp else 50
                } else {
                    100
                }
                
                // 确保高度至少能容纳上下边距 (约 44dp) 和 图表区域 (至少 20dp) -> 64dp
                // 这里的 44dp 是 ChartGenerator 内部的 padding (top 12 + bottom 32)
                val minReqHeight = (50 * density).toInt() // 降低最小高度要求，适应极小模式
                val reqHeight = (chartHeightDp * density).toInt().coerceAtLeast(minReqHeight).coerceAtMost(1200)
                
                val reqWidth = if (minWidthDp > 0) (minWidthDp * density).toInt().coerceIn(100, 2000) else 800
                val _reqWidth = reqWidth+250

                val chartBitmap = HourlyChartGenerator.generateChart(
                    context,
                    if (newData.isNotEmpty()) newData else HourlyForecastDataHolder.hourlyData,
                    _reqWidth,
                    reqHeight
                )
                views.setImageViewBitmap(R.id.hourly_chart_view, chartBitmap)
                
                // 设置点击事件（点击整个 Widget 触发刷新）
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
                
            } catch (e: Exception) {
                android.util.Log.e("HourlyWidget", "Chart error", e)
                views.setViewVisibility(R.id.hourly_chart_view, android.view.View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            android.util.Log.e("HourlyWidget", "Update UI error", e)
            views.setTextViewText(R.id.hourly_location, "渲染错误")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
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
