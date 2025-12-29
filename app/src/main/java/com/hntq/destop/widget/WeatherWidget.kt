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
        private const val BASE_URL = "https://api.open-meteo.com/"
        private const val ACTION_AUTO_UPDATE = "com.hntq.destop.widget.ACTION_WEATHER_AUTO_UPDATE"
        private const val ACTION_REFRESH = "com.hntq.destop.widget.ACTION_WEATHER_REFRESH"
        private const val UPDATE_INTERVAL_MILLIS = 60_000L
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
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(WeatherService::class.java)
        val call = service.getWeather(31.23, 121.47)
        call.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                val current = response.body()?.current
                val temp = current?.temperature_2m
                val rain = current?.precipitation
                val tempText = if (temp != null) String.format(Locale.getDefault(), "%.0f°C", temp) else "--°C"
                val rainText = if (rain != null) String.format(Locale.getDefault(), "降雨 %.1f mm", rain) else "降雨 -- mm"
                views.setTextViewText(R.id.weather_temp, tempText)
                views.setTextViewText(R.id.weather_rain, rainText)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                views.setTextViewText(R.id.weather_temp, "--°C")
                views.setTextViewText(R.id.weather_rain, "降雨 -- mm")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        })
    }
}

