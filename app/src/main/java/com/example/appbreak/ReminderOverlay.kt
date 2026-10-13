package com.example.appbreak

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 提醒悬浮窗：盖在目标 App 上面，必须手动关掉。
 *
 * 需要「显示在其他应用上层」权限；没授权时 [canDraw] 返回 false，
 * 调用方直接跳过，退回普通通知提醒，不会崩。
 */
class ReminderOverlay(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var current: View? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /**
     * @param onSnooze 点「再玩 N 分钟」时回调，参数是顺延的分钟数
     */
    fun show(label: String, minutes: Int, onSnooze: (Int) -> Unit) {
        if (!canDraw()) return
        // WindowManager 只能在主线程操作
        handler.post {
            removeCurrent()
            val view = build(label, minutes, onSnooze)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(48)
            }
            try {
                windowManager.addView(view, params)
                current = view
            } catch (e: Exception) {
                // 权限被撤销或窗口被拒绝时静默失败
                current = null
            }
        }
    }

    fun dismiss() {
        handler.post { removeCurrent() }
    }

    private fun removeCurrent() {
        current?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 已经被移除了
            }
        }
        current = null
    }

    private fun build(label: String, minutes: Int, onSnooze: (Int) -> Unit): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#FFF7F3E6"))
            }
        }

        val title = TextView(context).apply {
            text = "该休息一下了"
            textSize = 22f
            setTextColor(Color.parseColor("#FF1B1B1B"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val body = TextView(context).apply {
            text = "「$label」已经连续用了 $minutes 分钟。"
            textSize = 15f
            setTextColor(Color.parseColor("#FF44504A"))
            setPadding(0, dp(10), 0, dp(16))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val snooze = Button(context).apply {
            text = "再玩 5 分钟"
            setOnClickListener {
                dismiss()
                onSnooze(5)
            }
        }

        val leave = Button(context).apply {
            text = "好，我退出"
            setOnClickListener { dismiss() }
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        row.addView(snooze, buttonParams)
        row.addView(leave, buttonParams)

        card.addView(title)
        card.addView(body)
        card.addView(row)

        return FrameLayout(context).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
