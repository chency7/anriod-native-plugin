package com.hntq.destop.widget.schedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.hntq.destop.widget.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_PREV_DAY = "com.hntq.destop.widget.ACTION_SCHEDULE_PREV_DAY"
        private const val ACTION_NEXT_DAY = "com.hntq.destop.widget.ACTION_SCHEDULE_NEXT_DAY"
        private const val ACTION_REFRESH = "com.hntq.destop.widget.ACTION_SCHEDULE_REFRESH"

        private const val PREFS_NAME = "schedule_widget_prefs"
        private const val KEY_EPOCH_DAY_PREFIX = "epoch_day_"

        private val headerFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.CHINA)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun getEpochDay(context: Context, appWidgetId: Int): Long? {
            val key = "$KEY_EPOCH_DAY_PREFIX$appWidgetId"
            val p = prefs(context)
            return if (p.contains(key)) p.getLong(key, 0L) else null
        }

        private fun setEpochDay(context: Context, appWidgetId: Int, epochDay: Long) {
            prefs(context).edit().putLong("$KEY_EPOCH_DAY_PREFIX$appWidgetId", epochDay).apply()
        }

        private fun removeEpochDay(context: Context, appWidgetId: Int) {
            prefs(context).edit().remove("$KEY_EPOCH_DAY_PREFIX$appWidgetId").apply()
        }

        private fun ensureEpochDay(context: Context, appWidgetId: Int): Long {
            val existing = getEpochDay(context, appWidgetId)
            if (existing != null) return existing
            val today = LocalDate.now().toEpochDay()
            setEpochDay(context, appWidgetId, today)
            return today
        }

        private fun updateOne(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val epochDay = ensureEpochDay(context, appWidgetId)
            val date = LocalDate.ofEpochDay(epochDay)

            val views = RemoteViews(context.packageName, R.layout.schedule_widget)
            views.setTextViewText(R.id.schedule_date, headerFormatter.format(date))

            val prevIntent = Intent(context, ScheduleWidget::class.java).apply {
                action = ACTION_PREV_DAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.schedule_prev, prevPendingIntent)

            val nextIntent = Intent(context, ScheduleWidget::class.java).apply {
                action = ACTION_NEXT_DAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.schedule_next, nextPendingIntent)

            val refreshIntent = Intent(context, ScheduleWidget::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 3,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.schedule_header, refreshPendingIntent)

            val svcIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.schedule_list, svcIntent)
            views.setEmptyView(R.id.schedule_list, R.id.schedule_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.schedule_list)
        }

        private fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ScheduleWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            ids?.forEach { updateOne(context, appWidgetManager, it) }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateOne(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { removeEpochDay(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        if (action != ACTION_PREV_DAY && action != ACTION_NEXT_DAY && action != ACTION_REFRESH) return

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            updateAll(context)
            return
        }

        when (action) {
            ACTION_PREV_DAY -> {
                val epochDay = ensureEpochDay(context, appWidgetId) - 1
                setEpochDay(context, appWidgetId, epochDay)
            }
            ACTION_NEXT_DAY -> {
                val epochDay = ensureEpochDay(context, appWidgetId) + 1
                setEpochDay(context, appWidgetId, epochDay)
            }
        }

        updateOne(context, appWidgetManager, appWidgetId)
    }
}

