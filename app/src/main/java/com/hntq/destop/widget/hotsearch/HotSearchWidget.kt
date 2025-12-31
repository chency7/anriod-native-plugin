package com.hntq.destop.widget.hotsearch

import com.hntq.destop.widget.R
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HotSearchWidget : AppWidgetProvider() {

    companion object {
        private const val BASE_URL = "https://v2.xxapi.cn/"
        private const val ACTION_AUTO_UPDATE = "com.hntq.destop.widget.ACTION_HOT_AUTO_UPDATE"
        private const val ACTION_REFRESH = "com.hntq.destop.widget.ACTION_HOT_REFRESH"
        private const val PREFS_NAME = "hot_search_widget_prefs"
        private const val KEY_CACHE_JSON = "cache_json"
        private const val KEY_CACHE_AT = "cache_at"
        private const val KEY_CURSOR = "cursor"
        private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
        private const val UPDATE_INTERVAL_MILLIS = 60 * 1000L
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        startAlarm(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, forceRefresh = false)
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
            val componentName = ComponentName(context, HotSearchWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val forceRefresh = intent.action == ACTION_REFRESH
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, forceRefresh)
            }
        }
    }

    private fun startAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HotSearchWidget::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
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
        val intent = Intent(context, HotSearchWidget::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        forceRefresh: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.hot_search_widget)
        setupRefreshClick(context, appWidgetId, views)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_CACHE_JSON, null)
        val cachedAt = prefs.getLong(KEY_CACHE_AT, 0L)
        val now = System.currentTimeMillis()

        if (!forceRefresh && cachedJson != null && now - cachedAt <= CACHE_TTL_MILLIS) {
            val items = parseItems(cachedJson)
            if (items.isNotEmpty()) {
                renderNextPage(context, appWidgetManager, appWidgetId, views, items)
                return
            }
        }

        views.setTextViewText(R.id.hot_item_1, "正在获取热搜...")
        views.setTextViewText(R.id.hot_item_2, "")
        views.setTextViewText(R.id.hot_item_3, "")
        appWidgetManager.updateAppWidget(appWidgetId, views)
        fetchHotList(context, appWidgetManager, appWidgetId, views)
    }

    private fun setupRefreshClick(context: Context, appWidgetId: Int, views: RemoteViews) {
        val intent = Intent(context, HotSearchWidget::class.java).apply { action = ACTION_REFRESH }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.hot_header, pendingIntent)
    }

    private fun setupOpenUrlClick(context: Context, views: RemoteViews, viewId: Int, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val requestCode = (viewId * 1000) + url.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }

    private fun fetchHotList(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(HotSearchService::class.java)
        service.getWeiboHot().enqueue(object : Callback<HotSearchResponse> {
            override fun onResponse(call: Call<HotSearchResponse>, response: Response<HotSearchResponse>) {
                val body = response.body()
                val items = if (response.isSuccessful && body != null && body.code == 200) body.data else emptyList()
                if (items.isEmpty()) {
                    views.setTextViewText(R.id.hot_item_1, "加载失败")
                    views.setTextViewText(R.id.hot_item_2, "")
                    views.setTextViewText(R.id.hot_item_3, "")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return
                }

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_CACHE_JSON, Gson().toJson(items))
                    .putLong(KEY_CACHE_AT, System.currentTimeMillis())
                    .putInt(KEY_CURSOR, 0)
                    .apply()

                renderNextPage(context, appWidgetManager, appWidgetId, views, items)
            }

            override fun onFailure(call: Call<HotSearchResponse>, t: Throwable) {
                views.setTextViewText(R.id.hot_item_1, "加载失败: ${t.message}")
                views.setTextViewText(R.id.hot_item_2, "")
                views.setTextViewText(R.id.hot_item_3, "")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                android.util.Log.e("HotSearchWidget", "Request failed", t)
            }
        })
    }

    private fun parseItems(json: String): List<HotSearchItem> {
        return try {
            val type = object : TypeToken<List<HotSearchItem>>() {}.type
            Gson().fromJson<List<HotSearchItem>>(json, type) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun renderNextPage(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        items: List<HotSearchItem>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cursor = prefs.getInt(KEY_CURSOR, 0)

        val pageItems = buildList {
            var i = 0
            while (i < 3 && items.isNotEmpty()) {
                val idx = (cursor + i) % items.size
                add(items[idx])
                i += 1
            }
        }

        fun formatLine(item: HotSearchItem): String {
            val hotText = item.hot?.trim()?.takeIf { it.isNotEmpty() }?.let { "  $it" } ?: ""
            return "${item.index}. ${item.title}$hotText"
        }

        val line1 = pageItems.getOrNull(0)?.let(::formatLine) ?: ""
        val line2 = pageItems.getOrNull(1)?.let(::formatLine) ?: ""
        val line3 = pageItems.getOrNull(2)?.let(::formatLine) ?: ""

        views.setTextViewText(R.id.hot_item_1, line1)
        views.setTextViewText(R.id.hot_item_2, line2)
        views.setTextViewText(R.id.hot_item_3, line3)

        pageItems.getOrNull(0)?.let { setupOpenUrlClick(context, views, R.id.hot_item_1, it.url) }
        pageItems.getOrNull(1)?.let { setupOpenUrlClick(context, views, R.id.hot_item_2, it.url) }
        pageItems.getOrNull(2)?.let { setupOpenUrlClick(context, views, R.id.hot_item_3, it.url) }

        appWidgetManager.updateAppWidget(appWidgetId, views)

        val nextCursor = if (items.isEmpty()) 0 else (cursor + 3) % items.size
        prefs.edit().putInt(KEY_CURSOR, nextCursor).apply()
    }
}

