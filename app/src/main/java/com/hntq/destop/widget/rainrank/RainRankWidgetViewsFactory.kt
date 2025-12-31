package com.hntq.destop.widget.rainrank

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hntq.destop.widget.R
import java.util.Locale

internal class RainRankWidgetViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int =
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    private val prefs = context.getSharedPreferences("rain_rank_widget_prefs", Context.MODE_PRIVATE)
    private var items: List<RainRankItem> = emptyList()

    override fun onCreate() {
        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            items = emptyList()
            return
        }

        val idxKey = "window_index_$appWidgetId"
        val windowIndex = prefs.getInt(idxKey, 0).coerceIn(0, 4)
        val hours = intArrayOf(1, 3, 6, 12, 24)[windowIndex]
        items = mockItems(hours)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.rain_rank_widget_row)

        val views = RemoteViews(context.packageName, R.layout.rain_rank_widget_row)
        val rankNo = position + 1
        views.setTextViewText(R.id.rain_rank_row_no, rankNo.toString())
        views.setTextViewText(R.id.rain_rank_row_station, item.station)
        views.setTextViewText(R.id.rain_rank_row_value, String.format(Locale.CHINA, "%.1fmm", item.mm))

        val primaryColor = 0xFFFFFFFF.toInt()
        val secondaryColor = 0xFFEAEAEA.toInt()
        val highlightColor = 0xFFFFD36E.toInt()
        val valuePrimary = 0xFFDDE7FF.toInt()

        val nameColor = when (rankNo) {
            1 -> highlightColor
            2, 3 -> primaryColor
            else -> secondaryColor
        }
        views.setTextColor(R.id.rain_rank_row_no, nameColor)
        views.setTextColor(R.id.rain_rank_row_station, nameColor)
        views.setTextColor(R.id.rain_rank_row_value, if (rankNo <= 3) valuePrimary else secondaryColor)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private data class RainRankItem(
        val station: String,
        val mm: Double
    )

    private fun mockItems(hours: Int): List<RainRankItem> {
        val stations = listOf(
            "长沙·马坡岭",
            "岳阳·君山",
            "常德·桃源",
            "怀化·沅陵",
            "永州·道县",
            "湘西·凤凰",
            "邵阳·新邵",
            "益阳·桃江",
            "衡阳·祁东",
            "郴州·资兴",
            "娄底·涟源",
            "株洲·醴陵",
            "湘潭·韶山",
            "岳阳·平江",
            "张家界·武陵源",
            "长沙·望城",
            "永州·宁远",
            "郴州·宜章",
            "怀化·通道",
            "常德·澧县",
            "湘西·吉首",
            "邵阳·城步",
            "永州·江华",
            "郴州·桂东",
            "怀化·靖州"
        )

        val base = when (hours) {
            1 -> 22.0
            3 -> 48.0
            6 -> 76.0
            12 -> 135.0
            else -> 220.0
        }
        val step = base / (stations.size + 4)
        return stations.mapIndexed { index, station ->
            val noise = ((index % 7) - 3) * 0.15
            val value = (base - index * step + noise).coerceAtLeast(0.0)
            RainRankItem(station, value)
        }.sortedByDescending { it.mm }
    }
}

