package com.liushuye.huangliang

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标：PackageManager 能拿到 Drawable，转成 ImageBitmap 给 Compose 用。
 * 列表滚动时同一张图会被反复请求，所以加一层内存缓存。
 */
private object IconCache {
    private const val MAX = 400
    private val map = LinkedHashMap<String, ImageBitmap>()

    @Synchronized
    fun get(pkg: String): ImageBitmap? = map[pkg]

    @Synchronized
    fun put(pkg: String, bitmap: ImageBitmap) {
        if (map.size >= MAX) {
            // 简单粗暴地丢掉最早的一批
            map.keys.take(map.size / 4).forEach { map.remove(it) }
        }
        map[pkg] = bitmap
    }
}

private fun loadIcon(context: Context, pkg: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(pkg)
        .toBitmap(96, 96)
        .asImageBitmap()
} catch (e: Exception) {
    null
}

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val state = remember(packageName) { mutableStateOf(IconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (state.value == null) {
            val loaded = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
            if (loaded != null) {
                IconCache.put(packageName, loaded)
                state.value = loaded
            }
        }
    }

    return state.value
}

@Composable
fun AppIcon(packageName: String, size: Dp = 36.dp) {
    val icon = rememberAppIcon(packageName)
    val shape = RoundedCornerShape(size / 4)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
