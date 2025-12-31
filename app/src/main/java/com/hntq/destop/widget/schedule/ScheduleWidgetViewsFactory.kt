package com.hntq.destop.widget.schedule

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hntq.destop.widget.R
import java.time.LocalDate

internal class ScheduleWidgetViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int =
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    private val prefs = context.getSharedPreferences("schedule_widget_prefs", Context.MODE_PRIVATE)
    private var items: List<ScheduleItem> = emptyList()

    override fun onCreate() {
        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            items = emptyList()
            return
        }

        val key = "epoch_day_$appWidgetId"
        val epochDay = if (prefs.contains(key)) prefs.getLong(key, 0L) else LocalDate.now().toEpochDay()
        val date = LocalDate.ofEpochDay(epochDay)
        items = ScheduleData.buildForDate(date)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.schedule_widget_row)
        val views = RemoteViews(context.packageName, R.layout.schedule_widget_row)
        views.setTextViewText(R.id.schedule_row_name, item.name)
        views.setTextViewText(R.id.schedule_row_post, item.post)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

