package com.liushuye.huangliang

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 监控对象的分类 */
enum class AppCategory(val label: String) {
    SHORT_VIDEO("短视频"),
    READING("文字"),
    SOCIAL("社交"),
    GAME("游戏"),
    OTHER("其他");

    companion object {
        fun fromName(name: String?): AppCategory =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class MonitoredApp(
    val label: String,
    val packageName: String,
    val minutes: Int,
    val category: AppCategory = AppCategory.OTHER
)

/**
 * 某一天的统计记录。
 *
 * 注意 [referenceMinutes] 只是「占比的分母」——用来告诉你今天娱乐时间占参考时长的多少，
 * 不做任何限制：不拦截、不额外提醒、到点也不会禁用 App。
 */
data class DailyRecord(
    val date: LocalDate,
    /** 当天被监控 App 的累计使用时长 */
    val usedMillis: Long,
    /** 当天设置的娱乐参考时长（分钟），存快照是因为这个值以后可能会调整 */
    val referenceMinutes: Int,
    /** 当天用了多少次「再玩 5 分钟」 */
    val snoozeCount: Int
) {
    val usedMinutes: Int get() = (usedMillis / 60_000L).toInt()

    /** 已用时长占参考时长的百分比，纯参考 */
    val usedPercent: Int
        get() = if (referenceMinutes <= 0) 0
        else ((usedMillis.toDouble() / (referenceMinutes * 60_000L)) * 100).toInt()

    companion object {
        fun empty(date: LocalDate, referenceMinutes: Int) =
            DailyRecord(date, 0L, referenceMinutes, 0)
    }
}

/**
 * 每日娱乐参考时长。可以统一一个值，也可以工作日/休息日分开。
 * 只是用来算占比，不是限额。
 */
data class DailyReference(
    val separateWeekday: Boolean = false,
    val dailyMinutes: Int = 120,
    val weekdayMinutes: Int = 120,
    val weekendMinutes: Int = 180
) {
    fun minutesFor(date: LocalDate): Int = when {
        !separateWeekday -> dailyMinutes
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY ->
            weekendMinutes
        else -> weekdayMinutes
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 480
    }
}

/** 星期几的中文单字，周一开头 */
val WEEKDAY_CHARS = arrayOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 把一个月排成 7 列的行（周一开头），本月之外的位置是 null。
 * 抽成纯函数是为了能单测 —— 日历错位是最容易出又最难一眼看出的 bug。
 */
fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (leading + days + 6) / 7
    return (0 until rows).map { r ->
        (0..6).map { c ->
            val day = r * 7 + c - leading + 1
            if (day in 1..days) month.atDay(day) else null
        }
    }
}

fun LocalDate.weekdayChar(): String = WEEKDAY_CHARS[dayOfWeek.value - 1]

fun LocalDate.chineseDate(): String = "${monthValue}月${dayOfMonth}日 周${weekdayChar()}"
