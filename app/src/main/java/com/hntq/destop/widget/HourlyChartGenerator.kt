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
        
        dataList.forEachIndexed { index, item ->
            entries.add(Entry(index.toFloat(), item.temp.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Temperature").apply {
            color = Color.WHITE
            lineWidth = 2.5f // 稍微加粗
            
            // 圆点配置
            setDrawCircles(true)
            setCircleColor(Color.WHITE)
            circleRadius = 3f
            setDrawCircleHole(false)

            // 数值配置
            setDrawValues(true)
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}°"
                }
            }

            mode = LineDataSet.Mode.CUBIC_BEZIER // 平滑曲线
            cubicIntensity = 0.2f
            setDrawHighlightIndicators(false) // 关闭点击高亮线
            
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
            // top 增加到 80f 以容纳数值
            setViewPortOffsets(0f, 80f, 0f, 120f) 
            
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
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
                textSize = 11f
                granularity = 1f
                yOffset = 10f // 文字向下偏移
                
                // 添加垂直虚线（当前时间）
                addLimitLine(LimitLine(0f).apply {
                    lineColor = Color.parseColor("#80FFFFFF")
                    lineWidth = 1f
                    enableDashedLine(10f, 10f, 0f)
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
                
                // 添加水平虚线（当前温度基准）
                val currentTemp = entries.firstOrNull()?.y ?: 0f
                addLimitLine(LimitLine(currentTemp).apply {
                    lineColor = Color.parseColor("#80FFFFFF")
                    lineWidth = 1f
                    enableDashedLine(10f, 10f, 0f)
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
        val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        dataList.forEachIndexed { index, item ->
            // 获取当前点在屏幕上的坐标
            val point = floatArrayOf(index.toFloat(), item.temp.toFloat())
            transformer.pointValuesToPixel(point)
            val x = point[0]
            val y = point[1]

            // 1. 绘制第一个点的特殊样式（黄色光晕圆点）
            if (index == 0) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.strokeWidth = 3f
                canvas.drawCircle(x, y, 10f, paint) // 外圈

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FFCC00") // 黄色实心
                canvas.drawCircle(x, y, 6f, paint)
            }

            // 2. 绘制底部天气图标
            // 图标位置：X轴文字上方。
            // 我们可以利用 chart.viewPortHandler.contentBottom() 获取图表底部边界
            // 但因为 viewPortOffsets 设置了底部留白，我们可以直接在底部区域绘制
            val iconResId = getWeatherIconRes(item.skycon)
            val drawable = ContextCompat.getDrawable(context, iconResId)
            drawable?.let {
                val iconSize = 40
                // X轴文字大概在 height - 20 左右，图标放在文字上方 15dp 处
                val iconY = chart.height - 65f 
                
                val left = (x - iconSize / 2).toInt()
                val top = (iconY - iconSize / 2).toInt()
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
