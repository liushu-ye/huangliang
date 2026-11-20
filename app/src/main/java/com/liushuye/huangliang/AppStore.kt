package com.liushuye.huangliang

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * 本地存储。全部放在同一个 SharedPreferences 文件里，后台服务和界面共用。
 *
 * 每日记录存成一段 JSON，按天再按 App 分开：
 * ```
 * {"2026-11-20":{"ref":120,
 *   "apps":{"com.ss.android.ugc.aweme":{"label":"抖音","usedMs":600000,"snooze":2}}}}
 * ```
 * 日期键是 yyyy-MM-dd，字典序即时间序，只保留最近 [MAX_DAILY_ENTRIES] 天。
 *
 * 老版本只存了当天总数（usedMs/snooze 在顶层），读取时会迁移成一条
 * packageName = [LEGACY_KEY] 的记录，不丢历史。
 */
object AppStore {

    private const val PREFS_NAME = "settings"
    private const val KEY_MONITORED = "monitored"
    private const val KEY_DAILY = "daily_records"

    /** 大约两年，够日历翻了 */
    private const val MAX_DAILY_ENTRIES = 800

    /** 老数据的兜底 key，不会与任何真实包名冲突 */
    private const val LEGACY_KEY = "__legacy__"

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

    /** 格式 label|package|minutes|CATEGORY；更老的数据只有前三段，按「其他」处理 */
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
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return emptyMap()
        }

        val result = LinkedHashMap<LocalDate, DailyRecord>()
        for (key in root.keys()) {
            val date = try {
                LocalDate.parse(key)
            } catch (e: Exception) {
                continue
            }
            val day = root.optJSONObject(key) ?: continue
            result[date] = DailyRecord(
                date = date,
                referenceMinutes = day.optInt("ref", 0),
                apps = readApps(day)
            )
        }
        return result
    }

    fun recordFor(context: Context, date: LocalDate): DailyRecord? =
        loadDailyRecords(context)[date]

    private fun readApps(day: JSONObject): Map<String, AppUsage> {
        val appsObj = day.optJSONObject("apps")
        if (appsObj != null) {
            val map = LinkedHashMap<String, AppUsage>()
            for (pkg in appsObj.keys()) {
                val item = appsObj.optJSONObject(pkg) ?: continue
                map[pkg] = AppUsage(
                    packageName = pkg,
                    label = item.optString("label").ifBlank { pkg },
                    usedMillis = item.optLong("usedMs", 0L),
                    snoozeCount = item.optInt("snooze", 0)
                )
            }
            return map
        }

        // 老格式：只有当天总数，没有分应用
        val used = day.optLong("usedMs", 0L)
        val snooze = day.optInt("snooze", 0)
        if (used <= 0L && snooze <= 0) return emptyMap()
        return mapOf(
            LEGACY_KEY to AppUsage(LEGACY_KEY, "未区分应用", used, snooze)
        )
    }

    /** 累加今天某个 App 的使用时长（毫秒），顺带把参考时长快照刷新成当前设置 */
    fun addAppUsage(context: Context, packageName: String, label: String, millis: Long) {
        if (millis <= 0) return
        mutateToday(context) { day, reference ->
            day.put("ref", reference)
            val apps = ensureApps(day)
            val entry = apps.optJSONObject(packageName) ?: JSONObject()
            entry.put("label", label)
            entry.put("usedMs", entry.optLong("usedMs", 0L) + millis)
            if (!entry.has("snooze")) entry.put("snooze", 0)
            apps.put(packageName, entry)
            day.put("apps", apps)
        }
    }

    /** 今天这个 App 又用了一次「再玩 N 分钟」 */
    fun addAppSnooze(context: Context, packageName: String, label: String) {
        mutateToday(context) { day, reference ->
            if (!day.has("ref")) day.put("ref", reference)
            val apps = ensureApps(day)
            val entry = apps.optJSONObject(packageName) ?: JSONObject()
            entry.put("label", label)
            if (!entry.has("usedMs")) entry.put("usedMs", 0L)
            entry.put("snooze", entry.optInt("snooze", 0) + 1)
            apps.put(packageName, entry)
            day.put("apps", apps)
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
        mutateToday(context) { day, reference -> day.put("ref", reference) }
    }

    /** 取当天的 apps 对象；如果还是老格式，就地迁移成新格式 */
    private fun ensureApps(day: JSONObject): JSONObject {
        day.optJSONObject("apps")?.let { return it }

        val apps = JSONObject()
        val used = day.optLong("usedMs", 0L)
        val snooze = day.optInt("snooze", 0)
        if (used > 0L || snooze > 0) {
            apps.put(
                LEGACY_KEY,
                JSONObject().apply {
                    put("label", "未区分应用")
                    put("usedMs", used)
                    put("snooze", snooze)
                }
            )
        }
        day.remove("usedMs")
        day.remove("snooze")
        day.put("apps", apps)
        return apps
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
        val root = readRoot(context)

        val key = today.toString()
        val day = root.optJSONObject(key) ?: JSONObject().apply {
            put("ref", reference)
            put("apps", JSONObject())
        }

        block(day, reference)
        root.put(key, day)

        trim(root)
        p.edit().putString(KEY_DAILY, root.toString()).apply()
    }

    private fun trim(root: JSONObject) {
        val keys = root.keys().asSequence().toList()
        if (keys.size <= MAX_DAILY_ENTRIES) return
        keys.sorted().take(keys.size - MAX_DAILY_ENTRIES).forEach { root.remove(it) }
    }
}
