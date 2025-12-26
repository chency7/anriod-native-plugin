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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HitokotoWidget : AppWidgetProvider() {

    companion object {
        private const val BASE_URL = "https://v1.hitokoto.cn/"
        private const val ACTION_AUTO_UPDATE = "com.hntq.destop.widget.ACTION_AUTO_UPDATE"
        private const val ACTION_REFRESH = "com.hntq.destop.widget.ACTION_REFRESH"
        private const val PREFS_NAME = "hitokoto_widget_prefs"
        private const val KEY_TEXT = "text"
        private const val KEY_FROM = "from"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 启动定时轮播服务
        startAlarm(context)

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_AUTO_UPDATE || intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HitokotoWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // 遍历更新所有小组件
            for (appWidgetId in appWidgetIds) {
                 // 注意：这里我们直接发起网络请求，这在主线程可能会有轻微卡顿，
                 // 但 Retrofit 的 enqueue 是异步的，所以是安全的。
                 // 只是不要在主线程做耗时操作。
                 // 为了用户体验，先设置一个 Loading 状态（可选，如果轮播太快则不需要）
                 // val views = RemoteViews(context.packageName, R.layout.hitokoto_widget)
                 // views.setTextViewText(R.id.hitokoto_text, "刷新中...")
                 // appWidgetManager.updateAppWidget(appWidgetId, views)
                 
                 // 获取新的 RemoteViews 用于更新
                 val views = RemoteViews(context.packageName, R.layout.hitokoto_widget)
                 // 重新绑定点击事件，防止丢失
                 setupClickIntent(context, appWidgetId, views)
                 
                 fetchHitokoto(context, appWidgetManager, appWidgetId, views)
            }
        }
    }

    private fun startAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HitokotoWidget::class.java).apply {
            action = ACTION_AUTO_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 setRepeating (在省电模式下可能不准) 或 setExact
        // 为了演示效果，这里使用 setRepeating，间隔 10 秒
        // 注意：Android 5.1 之后最小间隔是 60秒，如果需要更快，需要用 Handler 或 WorkManager
        // 但对于桌面组件，太快会耗电。这里设置 60 秒轮播比较合理。
        // 如果用户强烈要求“轮播”，我们可以尝试设为 10 秒，但在高版本安卓上会被系统强制拉长到 1 分钟。
        
        // 为了演示效果，我们先设定 60 秒（1分钟）
        val interval = 60000L 
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime(),
            interval,
            pendingIntent
        )
    }

    private fun stopAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HitokotoWidget::class.java).apply {
            action = ACTION_AUTO_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.hitokoto_widget)
        setupClickIntent(context, appWidgetId, views)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedText = prefs.getString(KEY_TEXT, null)
        val cachedFrom = prefs.getString(KEY_FROM, null)
        if (cachedText != null) {
            views.setTextViewText(R.id.hitokoto_text, cachedText)
            if (cachedFrom != null) {
                views.setTextViewText(R.id.hitokoto_from, cachedFrom)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        // 初始加载一次
        fetchHitokoto(context, appWidgetManager, appWidgetId, views)
    }

    private fun setupClickIntent(context: Context, appWidgetId: Int, views: RemoteViews) {
        val intent = Intent(context, HitokotoWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.hitokoto_text, pendingIntent)
        views.setOnClickPendingIntent(R.id.hitokoto_from, pendingIntent)
    }

    private fun fetchHitokoto(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(HitokotoService::class.java)
        service.getHitokoto().enqueue(object : Callback<HitokotoResponse> {
            override fun onResponse(
                call: Call<HitokotoResponse>,
                response: Response<HitokotoResponse>
            ) {
                if (!response.isSuccessful) {
                    showFailure(context, appWidgetManager, appWidgetId, views, "HTTP ${response.code()}")
                    return
                }
                val hitokoto = response.body()
                val text = hitokoto?.hitokoto?.trim().orEmpty()
                if (text.isEmpty()) {
                    showFailure(context, appWidgetManager, appWidgetId, views, "空内容")
                    return
                }
                val fromValue = hitokoto?.from?.trim().takeIf { !it.isNullOrEmpty() } ?: "未知"
                val from = "— $fromValue"

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TEXT, text)
                    .putString(KEY_FROM, from)
                    .apply()

                views.setTextViewText(R.id.hitokoto_text, text)
                views.setTextViewText(R.id.hitokoto_from, from)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            override fun onFailure(call: Call<HitokotoResponse>, t: Throwable) {
                val msg = t.message ?: "未知错误"
                showFailure(context, appWidgetManager, appWidgetId, views, "${t.javaClass.simpleName}: $msg")
                android.util.Log.e("HitokotoWidget", "Request failed", t)
            }
        })
    }

    private fun showFailure(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        reason: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedText = prefs.getString(KEY_TEXT, null)
        val cachedFrom = prefs.getString(KEY_FROM, null)

        if (cachedText != null) {
            views.setTextViewText(R.id.hitokoto_text, cachedText)
            if (cachedFrom != null) {
                views.setTextViewText(R.id.hitokoto_from, cachedFrom)
            }
        } else {
            views.setTextViewText(R.id.hitokoto_text, "加载失败")
            views.setTextViewText(R.id.hitokoto_from, reason)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
