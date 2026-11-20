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
        assertEquals(37, record(date, 120, usage("a", "A", 45)).usedPercent)
        // 正好用满
        assertEquals(100, record(date, 120, usage("a", "A", 120)).usedPercent)
        // 超出一倍
        assertEquals(250, record(date, 120, usage("a", "A", 300)).usedPercent)
        // 参考时长为 0 时不做除法
        assertEquals(0, record(date, 0, usage("a", "A", 10)).usedPercent)
    }

    @Test
    fun `多个 App 的时长与延长次数会汇总`() {
        val date = LocalDate.of(2026, 11, 20)
        val rec = record(
            date,
            100,
            usage("com.a", "抖音", 40, snooze = 2),
            usage("com.b", "B站", 10, snooze = 1)
        )

        assertEquals(50, rec.usedMinutes)
        assertEquals(3, rec.snoozeCount)
        assertEquals(50, rec.usedPercent)
        // 按使用时长从多到少
        assertEquals(listOf("抖音", "B站"), rec.appsByUsage().map { it.label })
    }

    @Test
    fun `单个 App 的占比用同一个参考时长做分母`() {
        val date = LocalDate.of(2026, 11, 20)
        val douyin = usage("com.a", "抖音", 40, snooze = 2)
        val bili = usage("com.b", "B站", 10, snooze = 0)
        val rec = record(date, 100, douyin, bili)

        assertEquals(40, rec.percentOf(douyin))
        assertEquals(10, rec.percentOf(bili))
        // 各 App 占比之和应当等于总占比
        assertEquals(rec.usedPercent, rec.percentOf(douyin) + rec.percentOf(bili))
    }

    @Test
    fun `分类名解析对未知值回退到其他`() {
        assertEquals(AppCategory.SHORT_VIDEO, AppCategory.fromName("SHORT_VIDEO"))
        assertEquals(AppCategory.OTHER, AppCategory.fromName("SOMETHING_NEW"))
        assertEquals(AppCategory.OTHER, AppCategory.fromName(null))
    }

    private fun usage(pkg: String, label: String, minutes: Int, snooze: Int = 0) =
        AppUsage(pkg, label, minutes * 60_000L, snooze)

    private fun record(date: LocalDate, reference: Int, vararg apps: AppUsage) =
        DailyRecord(date, reference, apps.associateBy { it.packageName })
}
