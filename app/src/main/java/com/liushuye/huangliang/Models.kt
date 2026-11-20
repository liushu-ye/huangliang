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
 * 某一天、某个 App 的使用情况。
 *
 * label 也存一份：以后用户把这个 App 从监控里删了，历史记录还得能显示名字。
 */
data class AppUsage(
    val packageName: String,
    val label: String,
    val usedMillis: Long,
    val snoozeCount: Int
) {
    val usedMinutes: Int get() = (usedMillis / 60_000L).toInt()
}

/** 毫秒换算成占参考时长的百分比 */
fun percentOf(millis: Long, referenceMinutes: Int): Int =
    if (referenceMinutes <= 0) 0
    else ((millis.toDouble() / (referenceMinutes * 60_000L)) * 100).toInt()

/**
 * 某一天的统计记录。
 *
 * 注意 [referenceMinutes] 只是「占比的分母」——用来告诉你今天娱乐时间占参考时长的多少，
 * 不做任何限制：不拦截、不额外提醒、到点也不会禁用 App。
 */
data class DailyRecord(
    val date: LocalDate,
    /** 当天设置的娱乐参考时长（分钟），存快照是因为这个值以后可能会调整 */
    val referenceMinutes: Int,
    /** 按包名索引的当天各 App 使用情况 */
    val apps: Map<String, AppUsage>
) {
    val usedMillis: Long get() = apps.values.sumOf { it.usedMillis }
    val usedMinutes: Int get() = (usedMillis / 60_000L).toInt()
    val snoozeCount: Int get() = apps.values.sumOf { it.snoozeCount }

    /** 当天总时长占参考时长的百分比，纯参考 */
    val usedPercent: Int get() = percentOf(usedMillis, referenceMinutes)

    fun percentOf(app: AppUsage): Int = percentOf(app.usedMillis, referenceMinutes)

    /** 按使用时长从多到少排，日历里就按这个顺序列 */
    fun appsByUsage(): List<AppUsage> = apps.values.sortedByDescending { it.usedMillis }

    companion object {
        fun empty(date: LocalDate, referenceMinutes: Int) =
            DailyRecord(date, referenceMinutes, emptyMap())
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

fun LocalDate.weekdayChar(): String = WEEKDAY_CHARS[dayOfWeek.value - 1]

fun LocalDate.chineseDate(): String = "${monthValue}月${dayOfMonth}日 周${weekdayChar()}"

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
