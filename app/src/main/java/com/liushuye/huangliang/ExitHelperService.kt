package com.liushuye.huangliang

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * 一个只用来「回到桌面」的无障碍服务。
 *
 * 为什么需要它：Android 不允许一个 App 直接结束另一个 App 的进程，
 * 没有公开 API 能做到「点一下就把抖音关掉」。但无障碍服务可以执行
 * GLOBAL_ACTION_HOME，把用户直接送回桌面 —— 对用户来说效果就是「退出了当前应用」。
 *
 * 它不读取任何窗口内容（canRetrieveWindowContent=false），只调用这一个全局动作。
 */
class ExitHelperService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ExitHelperService? = null
            private set
    }
}

object ExitHelper {

    /** 用户有没有在系统设置里打开这个无障碍服务 */
    fun isEnabled(context: Context): Boolean {
        if (ExitHelperService.instance != null) return true

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expected = ComponentName(context, ExitHelperService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        }
        return false
    }

    /**
     * 回到桌面。
     * @return false 表示服务没连上（没开权限或还没绑定），调用方需要走别的兜底
     */
    fun goHome(): Boolean {
        val service = ExitHelperService.instance ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
