package com.liushuye.huangliang

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * 本地存储。全部放在同一个 SharedPreferences 文件里，后台服务和界面共用。
 *
 * 每日记录存成一段 JSON：{"2026-11-20":{"usedMs":123456,"ref":120,"snooze":2}, ...}
 * 只保留最近 [MAX_DAILY_ENTRIES] 天，日期键是 yyyy-MM-dd，字典序即时间序。
 */
object AppStore {

    private const val PREFS_NAME = "settings"
    private const val KEY_MONITORED = "monitored"
    private const val KEY_DAILY = "daily_records"

    /** 大约两年，够日历翻了 */
    private const val MAX_DAILY_ENTRIES = 800

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------- 监控列表 ----------------

    fun loadMonitored(context: Context): List<MonitoredApp> {
        val raw = prefs(context).getStringSet(KEY_MONITORED, emptySet()) ?: emptySet()
        return raw.mapNotNull { parseMonitored(it) }.sortedBy { it.label }
    }

    fun saveMonitored(context: Context, list: List<MonitoredApp>) {
        prefs(context).edit()
            .putStringSet(KEY_MONITORED, list.map { formatMonitored(it) }.toSet())
            .apply()
    }

    /** 格式 label|package|minutes|CATEGORY；老数据只有前三段，按「其他」处理 */
    private fun formatMonitored(app: MonitoredApp): String =
        listOf(app.label, app.packageName, app.minutes.toString(), app.category.name)
            .joinToString("|")

    private fun parseMonitored(raw: String): MonitoredApp? {
        val p = raw.split("|")
        if (p.size < 3) return null
        return MonitoredApp(
            label = p[0],
            packageName = p[1],
            minutes = p[2].toIntOrNull() ?: 15,
            category = if (p.size >= 4) AppCategory.fromName(p[3]) else AppCategory.OTHER
        )
    }

    // ---------------- 娱乐参考时长 ----------------

    fun loadReference(context: Context): DailyReference {
        val p = prefs(context)
        return DailyReference(
            separateWeekday = p.getBoolean("ref_separate", false),
            dailyMinutes = p.getInt("ref_daily", 120),
            weekdayMinutes = p.getInt("ref_weekday", 120),
            weekendMinutes = p.getInt("ref_weekend", 180)
        )
    }

    fun saveReference(context: Context, reference: DailyReference) {
        prefs(context).edit()
            .putBoolean("ref_separate", reference.separateWeekday)
            .putInt("ref_daily", reference.dailyMinutes)
            .putInt("ref_weekday", reference.weekdayMinutes)
            .putInt("ref_weekend", reference.weekendMinutes)
            .apply()
    }

    // ---------------- 每日记录 ----------------

    fun loadDailyRecords(context: Context): Map<LocalDate, DailyRecord> {
        val raw = prefs(context).getString(KEY_DAILY, null) ?: return emptyMap()
        val obj = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return emptyMap()
        }

        val result = LinkedHashMap<LocalDate, DailyRecord>()
        for (key in obj.keys()) {
            val date = try {
                LocalDate.parse(key)
            } catch (e: Exception) {
                continue
            }
            val item = obj.optJSONObject(key) ?: continue
            result[date] = DailyRecord(
                date = date,
                usedMillis = item.optLong("usedMs", 0L),
                referenceMinutes = item.optInt("ref", 0),
                snoozeCount = item.optInt("snooze", 0)
            )
        }
        return result
    }

    fun recordFor(context: Context, date: LocalDate): DailyRecord? =
        loadDailyRecords(context)[date]

    /** 累加今天的使用时长（毫秒），顺带把今天的参考时长刷新成当前设置 */
    fun addUsage(context: Context, millis: Long) {
        if (millis <= 0) return
        mutateToday(context) { obj, ref ->
            obj.put("usedMs", obj.optLong("usedMs", 0L) + millis)
            obj.put("ref", ref)
        }
    }

    /** 今天「再玩 N 分钟」用了一次 */
    fun addSnooze(context: Context) {
        mutateToday(context) { obj, ref ->
            obj.put("snooze", obj.optInt("snooze", 0) + 1)
            if (!obj.has("ref")) obj.put("ref", ref)
        }
    }

    /**
     * 用户改了参考时长：把今天这条记录的快照同步过来，
     * 免得日历里今天的占比还按旧值算。
     */
    fun refreshTodayReference(context: Context) {
        val today = LocalDate.now().toString()
        val root = readRoot(context)
        if (!root.has(today)) return
        mutateToday(context) { obj, ref -> obj.put("ref", ref) }
    }

    private fun readRoot(context: Context): JSONObject = try {
        prefs(context).getString(KEY_DAILY, null)?.let { JSONObject(it) } ?: JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    /**
     * 读-改-写每日记录。后台服务和界面都会走到这里（同一进程、不同线程），
     * 加锁避免其中一次更新被覆盖掉。
     */
    @Synchronized
    private fun mutateToday(
        context: Context,
        block: (JSONObject, Int) -> Unit
    ) {
        val today = LocalDate.now()
        val reference = loadReference(context).minutesFor(today)
        val p = prefs(context)

        val root = try {
            p.getString(KEY_DAILY, null)?.let { JSONObject(it) } ?: JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }

        val key = today.toString()
        val entry = root.optJSONObject(key) ?: JSONObject().apply {
            put("usedMs", 0L)
            put("ref", reference)
            put("snooze", 0)
        }

        block(entry, reference)
        root.put(key, entry)

        trim(root)
        p.edit().putString(KEY_DAILY, root.toString()).apply()
    }

    private fun trim(root: JSONObject) {
        val keys = root.keys().asSequence().toList()
        if (keys.size <= MAX_DAILY_ENTRIES) return
        keys.sorted().take(keys.size - MAX_DAILY_ENTRIES).forEach { root.remove(it) }
    }
}
