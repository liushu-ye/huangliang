package com.liushuye.huangliang

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 占比配色：不到 60% 绿、60~99% 橙、满 100% 及以上红。
 * 只是视觉参考，不代表任何「超限」含义。
 */
object StatsPalette {
    val Good = Color(0xFF2E6B32)
    val GoodBg = Color(0xFFE3F2E1)
    val Warn = Color(0xFF9A5B00)
    val WarnBg = Color(0xFFFFF0D6)
    val Over = Color(0xFFB3261E)
    val OverBg = Color(0xFFFBDCDC)

    fun accent(percent: Int): Color = when {
        percent < 60 -> Color(0xFF4CAF50)
        percent < 100 -> Color(0xFFFFA000)
        else -> Color(0xFFE53935)
    }

    fun text(percent: Int): Color = when {
        percent < 60 -> Good
        percent < 100 -> Warn
        else -> Over
    }

    fun background(percent: Int): Color = when {
        percent < 60 -> GoodBg
        percent < 100 -> WarnBg
        else -> OverBg
    }
}

/** 自己画的细进度条，避免依赖 Material3 里会变签名的 LinearProgressIndicator */
@Composable
fun MiniProgress(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp
) {
    val safe = fraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(color.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(safe)
                .fillMaxHeight()
                .clip(shape)
                .background(color)
        )
    }
}
