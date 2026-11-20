package com.liushuye.huangliang

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 进页面读一次就够，历史是只读的
    val records = remember { AppStore.loadDailyRecords(context) }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MonthSwitcher(
                month = month,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) }
            )

            MonthGrid(
                month = month,
                records = records,
                selected = selected,
                onSelect = { selected = it }
            )

            DayDetail(record = records[selected], date = selected)

            MonthSummary(month = month, records = records)

            Text(
                "颜色只表示当天娱乐时长占参考时长的比例，不做任何限制。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev) { Text("‹ 上月") }
        Text(
            "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = onNext) { Text("下月 ›") }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    records: Map<LocalDate, DailyRecord>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val weeks = monthGrid(month)

    Column {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_CHARS.forEach { c ->
                Text(
                    c,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                record = records[date],
                                isSelected = date == selected,
                                isToday = date == today,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    record: DailyRecord?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val used = record?.usedMillis ?: 0L
    val percent = record?.usedPercent ?: 0

    val bg = if (used > 0L) StatsPalette.background(percent)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val fg = if (used > 0L) StatsPalette.text(percent)
    else MaterialTheme.colorScheme.onSurfaceVariant

    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bg)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 13.sp,
            color = fg,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
        if (used > 0L) {
            Text("$percent%", fontSize = 8.sp, color = fg.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun DayDetail(record: DailyRecord?, date: LocalDate) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(date.chineseDate(), style = MaterialTheme.typography.titleMedium)

            if (record == null || record.usedMillis <= 0L) {
                Text(
                    "这天没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val percent = record.usedPercent
                MiniProgress(
                    fraction = if (record.referenceMinutes > 0)
                        record.usedMillis.toFloat() / (record.referenceMinutes * 60_000f)
                    else 0f,
                    color = StatsPalette.accent(percent)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("已用 ${record.usedMinutes} 分钟", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "参考 ${record.referenceMinutes} 分钟 · $percent%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatsPalette.text(percent)
                    )
                }
                Text(
                    "延长使用 ${record.snoozeCount} 次",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(month: YearMonth, records: Map<LocalDate, DailyRecord>) {
    val monthRecords = records.values.filter { YearMonth.from(it.date) == month }
    val totalMillis = monthRecords.sumOf { it.usedMillis }
    val activeDays = monthRecords.count { it.usedMillis > 0L }
    val overDays = monthRecords.count { it.usedPercent >= 100 }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("本月汇总", style = MaterialTheme.typography.titleMedium)
            SummaryRow("累计娱乐时长", formatDuration(totalMillis))
            SummaryRow("有记录天数", "$activeDays 天")
            SummaryRow(
                "日均",
                if (activeDays > 0) "${(totalMillis / 60_000L / activeDays)} 分钟" else "—"
            )
            SummaryRow("占比 100% 以上", "$overDays 天")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分钟"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分钟"
    }
}
