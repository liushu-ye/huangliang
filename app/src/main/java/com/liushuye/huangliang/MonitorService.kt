package com.liushuye.huangliang

import android.Manifest
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val overlay by lazy { ReminderOverlay(this) }

    /** 当前判定为前台的目标包名；null 表示不在任何 App 里（桌面/息屏/别的 App） */
    private var currentPackage: String? = null

    /** 本次使用会话的开始时间，0 表示还没有开始计时 */
    private var sessionStart = 0L

    /** 下一次该提醒的时间戳；Long.MAX_VALUE 表示本轮已经提醒过 */
    @Volatile
    private var nextReminderAt = 0L

    /** 用户点了「好，我退出」但还没真的走，记下来待会追问 */
    @Volatile
    private var pendingExit: MonitoredApp? = null

    /** 追问他「怎么还没退出」的时间点 */
    @Volatile
    private var nagAt = 0L

    /** 上一次查询使用事件的时间点，用它来推进查询窗口，避免漏事件 */
    private var lastEventQuery = 0L

    /** 最近一次观测到的前台 App，跨息屏保留，避免息屏后判定卡在「未知」 */
    private var lastResumedPackage: String? = null

    /** 用于判断常驻通知内容要不要更新 */
    private var lastMonitoredCount = -1

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground()

        scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                    // 单次轮询出错不中断整个监控循环
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setContentTitle("黄粱一梦正在运行")
            .setContentText("正在监控你设置的 App")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        // Android 14+ 必须在 startForeground 时给出已声明的前台服务类型，
        // 否则会抛 MissingForegroundServiceTypeException 直接崩溃
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_ONGOING, notification)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val monitored = loadMonitored()
        updateOngoingNotification(monitored.size)

        val foreground = detectForegroundPackage(now)

        // 前台换人了：结束上一轮会话
        if (foreground != currentPackage) {
            currentPackage = foreground
            sessionStart = 0L
            nextReminderAt = 0L
            overlay.dismiss()
        }

        checkPendingExit(now, foreground)

        val target = foreground?.let { pkg -> monitored.firstOrNull { it.packageName == pkg } }
        if (target == null) {
            sessionStart = 0L
            nextReminderAt = 0L
            return
        }

        // 刚进入目标 App，本次会话从这一刻开始重新计时
        if (sessionStart == 0L) {
            sessionStart = now
            nextReminderAt = now + target.minutes * 60_000L
            return
        }

        if (now >= nextReminderAt) {
            nextReminderAt = Long.MAX_VALUE
            remind(target)
        }
    }

    /**
     * 「好，我退出」之后的跟进：
     * 人走了就收工；还在原地待着就到点追问一句「你怎么还没退出」。
     */
    private fun checkPendingExit(now: Long, foreground: String?) {
        val pending = pendingExit ?: return

        if (foreground != pending.packageName) {
            clearPendingExit()
            return
        }

        if (now >= nagAt) {
            nagAt = now + NAG_REPEAT_MS
            overlay.showNag(
                label = pending.label,
                onSnooze = { minutes ->
                    clearPendingExit()
                    nextReminderAt = System.currentTimeMillis() + minutes * 60_000L
                },
                onLeave = { requestExit(pending) }
            )
        }
    }

    /**
     * 找出当前真正在前台的包名。
     *
     * 关键点：查询窗口必须从「上次检查的时间点」往后推进，而不能每次都只看最近几秒。
     * 否则目标 App 打开超过几秒后就再也查不到事件，会话计时会被反复中断。
     * 查询不到新事件时沿用上一次的结论，让计时继续往下走。
     */
    private fun detectForegroundPackage(now: Long): String? {
        val from = if (lastEventQuery == 0L) now - FIRST_LOOKBACK_MS else lastEventQuery
        lastEventQuery = now

        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usage.queryEvents(from, now)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg: String? = event.packageName
            if (pkg == null || pkg == packageName) continue
            lastResumedPackage = pkg
        }

        // 息屏时不算「在使用 App」，但保留 lastResumedPackage 以便亮屏后立刻恢复判断
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive) return null

        return lastResumedPackage
    }

    private fun remind(target: MonitoredApp) {
        sendNotification(target)
        // 没给悬浮窗权限时 canDraw() 为 false，show() 自己会跳过，只留通知
        overlay.showReminder(
            label = target.label,
            minutes = target.minutes,
            onSnooze = { minutes ->
                nextReminderAt = System.currentTimeMillis() + minutes * 60_000L
            },
            onLeave = { requestExit(target) }
        )
    }

    /**
     * 用户表达「我要退出」。
     *
     * Android 没有「关掉别的 App」的公开 API，能做到的是通过无障碍服务把用户送回桌面。
     * 服务没开也不强求 —— 会把这次退出挂起，NAG_DELAY_MS 之后如果目标 App 还在前台，
     * 就弹「你怎么还没退出」继续催。
     */
    private fun requestExit(target: MonitoredApp) {
        pendingExit = target
        nagAt = System.currentTimeMillis() + NAG_DELAY_MS
        ExitHelper.goHome()
    }

    private fun clearPendingExit() {
        pendingExit = null
        nagAt = 0L
    }

    private fun sendNotification(target: MonitoredApp) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${target.label} 已经 ${target.minutes} 分钟")
            .setContentText("起来动一动，休息一下吧")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_REMINDER, notification)
    }

    /** 让常驻通知显示监控数量，方便确认服务还活着 */
    private fun updateOngoingNotification(count: Int) {
        if (count == lastMonitoredCount) return
        lastMonitoredCount = count

        val text = if (count == 0) "还没有设置要监控的 App" else "正在监控 $count 个 App"
        val notification = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setContentTitle("黄粱一梦正在运行")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_ONGOING, notification)
    }

    private fun loadMonitored(): List<MonitoredApp> {
        val raw = prefs.getStringSet("monitored", emptySet()) ?: emptySet()
        return raw.mapNotNull {
            val p = it.split("|")
            if (p.size != 3) null
            else MonitoredApp(p[0], p[1], p[2].toIntOrNull() ?: 15)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "后台监控", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "使用提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    // 被系统回收后尽量拉起来，避免监控悄悄失效
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        overlay.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_REMINDER = "break_reminder"
        private const val NOTIFICATION_ID_ONGOING = 1001
        private const val NOTIFICATION_ID_REMINDER = 1002
        private const val POLL_INTERVAL_MS = 1000L

        /** 首次查询的回看窗口：服务可能是用户已经打开目标 App 之后才启动的 */
        private const val FIRST_LOOKBACK_MS = 10 * 60_000L

        /** 点了「好，我退出」之后多久开始追问 */
        private const val NAG_DELAY_MS = 20_000L

        /** 追问的间隔 */
        private const val NAG_REPEAT_MS = 45_000L
    }
}
