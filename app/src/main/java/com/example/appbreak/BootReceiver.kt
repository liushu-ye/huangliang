package com.example.appbreak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 开机或应用更新后，如果用户已经配置了要监控的 App，就把监控服务重新拉起来。
 * 注意：部分厂商 ROM 会拦截开机自启，这里失败不做处理，避免开机就崩。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val monitored = prefs.getStringSet("monitored", emptySet()) ?: emptySet()
        if (monitored.isEmpty()) return

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java)
            )
        } catch (e: Exception) {
            // ROM 限制后台启动前台服务时静默失败
        }
    }
}
