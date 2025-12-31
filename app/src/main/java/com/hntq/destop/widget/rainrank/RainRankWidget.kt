package com.hntq.destop.widget.rainrank

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.hntq.destop.widget.R

class RainRankWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_CYCLE_WINDOW = "com.hntq.destop.widget.ACTION_RAIN_RANK_CYCLE_WINDOW"
        private const val PREFS_NAME = "rain_rank_widget_prefs"
        private const val KEY_WINDOW_INDEX_PREFIX = "window_index_"

        private val windows = intArrayOf(1, 3, 6, 12, 24)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun getWindowIndex(context: Context, appWidgetId: Int): Int {
            val key = "$KEY_WINDOW_INDEX_PREFIX$appWidgetId"
            return prefs(context).getInt(key, 0).coerceIn(0, windows.size - 1)
        }

        private fun setWindowIndex(context: Context, appWidgetId: Int, idx: Int) {
            prefs(context).edit().putInt("$KEY_WINDOW_INDEX_PREFIX$appWidgetId", idx).apply()
        }

        private fun removeWindowIndex(context: Context, appWidgetId: Int) {
            prefs(context).edit().remove("$KEY_WINDOW_INDEX_PREFIX$appWidgetId").apply()
        }

        private fun updateOne(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.rain_rank_widget)

            val windowIndex = getWindowIndex(context, appWidgetId)
            val hours = windows[windowIndex]
            views.setTextViewText(R.id.rain_rank_window, "近${hours}小时")

            val cycleIntent = Intent(context, RainRankWidget::class.java).apply {
                action = ACTION_CYCLE_WINDOW
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val cyclePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.rain_rank_header, cyclePendingIntent)

            val svcIntent = Intent(context, RainRankWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.rain_rank_list, svcIntent)
            views.setEmptyView(R.id.rain_rank_list, R.id.rain_rank_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.rain_rank_list)
        }

        private fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RainRankWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName) ?: return
            ids.forEach { updateOne(context, appWidgetManager, it) }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { removeWindowIndex(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_CYCLE_WINDOW) return

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            updateAll(context)
            return
        }

        val current = getWindowIndex(context, appWidgetId)
        val next = (current + 1) % windows.size
        setWindowIndex(context, appWidgetId, next)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateOne(context, appWidgetManager, appWidgetId)
    }
}
