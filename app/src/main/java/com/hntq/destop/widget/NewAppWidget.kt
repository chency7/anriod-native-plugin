package com.hntq.destop.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/**
 * Implementation of App Widget functionality.
 */
class NewAppWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            android.util.Log.d("HNTQ_WIDGET", "Updating widget ID: $appWidgetId")
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // 1. 准备原有的文本内容
    val widgetText = context.getString(R.string.appwidget_text)

    // 2. 构建 RemoteViews 对象
    val views = RemoteViews(context.packageName, R.layout.new_app_widget)

    // 3. 注意：这里我们移除了设置时间的代码，因为在 xml 中使用了 TextClock，
    // 系统会自动接管时间的显示和每秒刷新。

    // 4. 设置原有说明文本
    views.setTextViewText(R.id.appwidget_text, widgetText)
    // 5. 设置作者署名
    views.setTextViewText(R.id.appwidget_author, "Author: chency")

    // 6. 通知 AppWidgetManager 更新组件
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
