package com.hntq.destop.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Locale
import kotlin.math.min

import android.graphics.drawable.GradientDrawable

object HourlyChartGenerator {

    fun generateChart(
        context: Context,
        fullData: List<HourlyForecastDataHolder.HourlyItem>,
        width: Int,
        height: Int
    ): Bitmap {
        if (fullData.isEmpty()) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        // 1. 创建 LineChart 实例
        val chart = LineChart(context)

        // 2. 准备数据
        val displayCount = min(fullData.size, 8) // 显示前8个数据点
        val dataList = fullData.take(displayCount)
        val entries = ArrayList<Entry>()

        val density = 1f

        
        dataList.forEachIndexed { index, item ->
            entries.add(Entry(index.toFloat(), item.temp.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Temperature").apply {
            color = Color.WHITE
            lineWidth = 2.5f * density // 稍微加粗
            
            // 圆点配置
            setDrawCircles(true)
            setCircleColor(Color.WHITE)
            circleRadius = 3f * density
            setDrawCircleHole(false)

            // 数值配置
            setDrawValues(false)

            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲线
            cubicIntensity = 0.2f
            setDrawHighlightIndicators(true) // 关闭点击高亮线
            
            // 填充配置
            setDrawFilled(true)
            val fillGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#4DFFFFFF"), Color.TRANSPARENT)
            )
            fillDrawable = fillGradient
        }

        val lineData = LineData(dataSet)
        chart.data = lineData

        // 3. 配置图表外观
        chart.apply {
            // 调整边距，底部留出更多空间给图标和文字
            // left, top, right, bottom
            // top: 数值文字(9dp) + 间距 = 12dp -> 改为 20dp 以便数字上移
            // bottom: 时间文字(9dp) + 图标(18dp) + 间距 = 32dp
            val topOffset = 12f * density
            val bottomOffset = 32f * density
            setViewPortOffsets(0f, topOffset, 0f, bottomOffset) 
            
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            // X轴配置
            xAxis.apply {
                isEnabled = true
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                textColor = Color.parseColor("#CCFFFFFF")
                textSize = 10f * density // 10dp
                granularity = 1f
                yOffset = 3f * density // 文字向下偏移
                
                // 添加垂直虚线（当前时间）
                addLimitLine(LimitLine(0f).apply {
                    lineColor = Color.parseColor("#80FFFFFF")
                    lineWidth = 1f * density
                    enableDashedLine(5f * density, 5f * density, 0f)
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                })

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < dataList.size) {
                             if (index == 0) "现在" else dataList[index].time
                        } else ""
                    }
                }
            }

            // Y轴配置
            axisLeft.apply {
                isEnabled = true // 启用以便绘制 LimitLine，但不显示轴线和标签
                setDrawAxisLine(false)
                setDrawLabels(false)
                setDrawGridLines(false)
                
                // 动态设置 Y 轴范围，防止线条贴顶/贴底
                if (entries.isNotEmpty()) {
                    val yMin = entries.minOf { it.y }
                    val yMax = entries.maxOf { it.y }
                    val diff = yMax - yMin
                    val buffer = if (diff < 2f) 2f else diff * 0.2f
                    axisMinimum = yMin - buffer
                    axisMaximum = yMax + buffer
                }

                // 添加水平虚线（当前温度基准）
                val currentTemp = entries.firstOrNull()?.y ?: 0f
                addLimitLine(LimitLine(currentTemp).apply {
                    lineColor = Color.parseColor("#80FFFFFF")
                    lineWidth = 1f * density
                    enableDashedLine(5f * density, 5f * density, 0f)
                })
            }
            axisRight.isEnabled = false
            
            animateX(0)
        }

        // 4. 手动测量和布局
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        chart.measure(widthSpec, heightSpec)
        chart.layout(0, 0, width, height)

        // 5. 绘制到 Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        chart.draw(canvas)

        // 6. 后处理：绘制自定义元素（图标、特殊点）
        drawCustomElements(context, canvas, chart, dataList)

        return bitmap
    }

    private fun drawCustomElements(
        context: Context,
        canvas: Canvas,
        chart: LineChart,
        dataList: List<HourlyForecastDataHolder.HourlyItem>
    ) {
        val density = context.resources.displayMetrics.density
        val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
        }
        
        dataList.forEachIndexed { index, item ->
            // 获取当前点在屏幕上的坐标
            val point = floatArrayOf(index.toFloat(), item.temp.toFloat())
            transformer.pointValuesToPixel(point)
            val x = point[0]
            val y = point[1]

            // 绘制温度数值（向上偏移 12dp）
            val textY = y - 12 * density
            canvas.drawText("${item.temp.toInt()}°", x, textY, textPaint)

            // 1. 绘制第一个点的特殊样式（黄色光晕圆点）
            if (index == 0) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.strokeWidth = 1f * density
                canvas.drawCircle(x, y, 6f * density, paint) // 外圈

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FFCC00") // 黄色实心
                canvas.drawCircle(x, y, 3.5f * density, paint)
            }

            // 2. 绘制底部天气图标
            // 图标位置：X轴文字上方。
            val iconResId = getWeatherIconRes(item.skycon)
            val drawable = ContextCompat.getDrawable(context, iconResId)
            drawable?.let {
                val density = context.resources.displayMetrics.density
                val iconSize = (18 * density).toInt() // 18dp icon size
                // 图标位置：放在底部区域上方
                // bottomOffset 是 32dp。文字大概占 12dp (9sp + padding)。
                // 剩余 20dp 给图标。
                // 图标中心大概在 height - 22dp。
                val iconCenterY = chart.height - (22 * density)
                
                val left = (x - iconSize / 2).toInt()
                val top = (iconCenterY - iconSize / 2).toInt()
                val right = left + iconSize
                val bottom = top + iconSize
                
                it.setBounds(left, top, right, bottom)
                it.draw(canvas)
            }
        }
    }

    private fun getWeatherIconRes(skycon: String): Int {
        val s = skycon.uppercase(Locale.ROOT)
        return when {
            s.contains("CLEAR") -> com.hntq.destop.widget.R.drawable.ic_weather_sun
            s.contains("PARTLY_CLOUDY") -> com.hntq.destop.widget.R.drawable.ic_weather_cloud
            s == "CLOUDY" -> com.hntq.destop.widget.R.drawable.ic_weather_cloud
            s.contains("RAIN") -> com.hntq.destop.widget.R.drawable.ic_rain
            s.contains("SNOW") -> com.hntq.destop.widget.R.drawable.ic_rain // 暂无雪图标复用雨
            s.contains("WIND") -> com.hntq.destop.widget.R.drawable.ic_weather_cloud
            s.contains("FOG") || s.contains("HAZE") -> com.hntq.destop.widget.R.drawable.ic_weather_cloud
            else -> com.hntq.destop.widget.R.drawable.ic_weather_cloud
        }
    }
}
