package com.liushuye.huangliang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历排布和占比计算的单测。
 * 这些是纯函数，没有 Android 依赖，跑在 JVM 上就能验证。
 */
class StatsLogicTest {

    @Test
    fun `2026年11月1日是周日，第一行前六格为空`() {
        val weeks = monthGrid(YearMonth.of(2026, 11))
        assertEquals(6, weeks.size)
        repeat(6) { assertNull(weeks[0][it]) }
        assertEquals(LocalDate.of(2026, 11, 1), weeks[0][6])
    }

    @Test
    fun `2024年2月1日是周四，落在第4列`() {
        val weeks = monthGrid(YearMonth.of(2024, 2))
        assertEquals(LocalDate.of(2024, 2, 1), weeks[0][3])
        assertEquals(29, weeks.flatten().filterNotNull().size)
    }

    @Test
    fun `2026年11月20日周五落在第5列`() {
        val target = LocalDate.of(2026, 11, 20)
        val row = monthGrid(YearMonth.of(2026, 11)).first { week -> target in week }
        assertEquals(target, row[4])
    }

    @Test
    fun `任意月份每行都是7格，日期都在当月且首尾正确`() {
        var month = YearMonth.of(2020, 1)
        val last = YearMonth.of(2030, 12)
        while (month <= last) {
            val weeks = monthGrid(month)
            weeks.forEach { assertEquals(7, it.size) }

            val dates = weeks.flatten().filterNotNull()
            assertEquals(month.lengthOfMonth(), dates.size)
            dates.forEach { assertEquals(month, YearMonth.from(it)) }
            assertEquals(month.atDay(1), dates.first())
            assertEquals(month.atEndOfMonth(), dates.last())

            month = month.plusMonths(1)
        }
    }

    @Test
    fun `每月1号所在列等于星期几减一`() {
        var month = YearMonth.of(2023, 1)
        val last = YearMonth.of(2027, 12)
        while (month <= last) {
            val first = month.atDay(1)
            val row = monthGrid(month).first()
            assertEquals("$month 的 1 号位置不对", first, row[first.dayOfWeek.value - 1])
            month = month.plusMonths(1)
        }
    }

    @Test
    fun `工作日与休息日分开取参考时长`() {
        val ref = DailyReference(
            separateWeekday = true,
            dailyMinutes = 120,
            weekdayMinutes = 90,
            weekendMinutes = 240
        )
        assertEquals(90, ref.minutesFor(LocalDate.of(2026, 11, 20)))   // 周五
        assertEquals(240, ref.minutesFor(LocalDate.of(2026, 11, 21)))  // 周六
        assertEquals(240, ref.minutesFor(LocalDate.of(2026, 11, 22)))  // 周日
        assertEquals(90, ref.minutesFor(LocalDate.of(2026, 11, 23)))   // 周一
    }

    @Test
    fun `统一模式下每天取同一个值`() {
        val ref = DailyReference(separateWeekday = false, dailyMinutes = 150)
        assertEquals(150, ref.minutesFor(LocalDate.of(2026, 11, 21)))
        assertEquals(150, ref.minutesFor(LocalDate.of(2026, 11, 24)))
    }

    @Test
    fun `占比按毫秒换算并向下取整`() {
        val date = LocalDate.of(2026, 11, 20)
        // 45 分钟 / 120 分钟 = 37.5% -> 37
        assertEquals(37, DailyRecord(date, 45 * 60_000L, 120, 0).usedPercent)
        // 正好用满
        assertEquals(100, DailyRecord(date, 120 * 60_000L, 120, 0).usedPercent)
        // 超出一倍
        assertEquals(250, DailyRecord(date, 300 * 60_000L, 120, 0).usedPercent)
        // 参考时长为 0 时不做除法
        assertEquals(0, DailyRecord(date, 10_000L, 0, 0).usedPercent)
    }

    @Test
    fun `分类名解析对未知值回退到其他`() {
        assertEquals(AppCategory.SHORT_VIDEO, AppCategory.fromName("SHORT_VIDEO"))
        assertEquals(AppCategory.OTHER, AppCategory.fromName("SOMETHING_NEW"))
        assertEquals(AppCategory.OTHER, AppCategory.fromName(null))
    }
}
