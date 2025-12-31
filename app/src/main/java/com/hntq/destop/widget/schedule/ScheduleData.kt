package com.hntq.destop.widget.schedule

import java.time.LocalDate
import kotlin.math.absoluteValue

internal data class ScheduleItem(
    val name: String,
    val post: String
)

internal object ScheduleData {

    private val names = listOf(
        "张清", "李然", "王溪", "赵宁", "周澄", "吴昕",
        "郑恺", "孙悦", "钱澜", "冯安", "陈墨", "褚琳"
    )

    private val posts = listOf(
        "总值班", "指挥调度", "综合协调", "信息报送", "巡查处置", "应急联络",
        "设备保障", "后勤保障", "夜间值守", "机动备勤", "外联对接", "现场支持"
    )

    fun buildForDate(date: LocalDate): List<ScheduleItem> {
        val offset = (date.toEpochDay() % names.size).toInt().absoluteValue
        val rotated = names.drop(offset) + names.take(offset)
        return posts.mapIndexed { idx, post ->
            val name = rotated[idx % rotated.size]
            ScheduleItem(name = name, post = post)
        }
    }
}

