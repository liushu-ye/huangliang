package com.liushuye.huangliang

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/** 反馈 QQ 群 */
private const val QQ_GROUP = "535391647"

/** 常见娱乐 App，带上默认分类，省得用户自己一个个选 */
private data class CommonApp(val label: String, val pkg: String, val category: AppCategory)

private val COMMON_APPS = listOf(
    CommonApp("哔哩哔哩", "tv.danmaku.bili", AppCategory.SHORT_VIDEO),
    CommonApp("哔哩哔哩国际版", "com.bilibili.app.in", AppCategory.SHORT_VIDEO),
    CommonApp("抖音", "com.ss.android.ugc.aweme", AppCategory.SHORT_VIDEO),
    CommonApp("抖音极速版", "com.ss.android.ugc.aweme.lite", AppCategory.SHORT_VIDEO),
    CommonApp("快手", "com.smile.gifmaker", AppCategory.SHORT_VIDEO),
    CommonApp("快手极速版", "com.kuaishou.nebula", AppCategory.SHORT_VIDEO),
    CommonApp("腾讯视频", "com.tencent.qqlive", AppCategory.SHORT_VIDEO),
    CommonApp("爱奇艺", "com.qiyi.video", AppCategory.SHORT_VIDEO),
    CommonApp("优酷", "com.youku.phone", AppCategory.SHORT_VIDEO),
    CommonApp("芒果TV", "com.hunantv.imgo.activity", AppCategory.SHORT_VIDEO),
    CommonApp("西瓜视频", "com.ss.android.article.video", AppCategory.SHORT_VIDEO),
    CommonApp("番茄小说", "com.dragon.read", AppCategory.READING),
    CommonApp("七猫免费小说", "com.kmxs.reader", AppCategory.READING),
    CommonApp("微信读书", "com.tencent.weread", AppCategory.READING),
    CommonApp("起点读书", "com.qidian.QDReader", AppCategory.READING),
    CommonApp("晋江小说阅读", "com.jjwxc.reader", AppCategory.READING),
    CommonApp("掌阅", "com.zhangyue.read", AppCategory.READING),
    CommonApp("今日头条", "com.ss.android.article.news", AppCategory.READING),
    CommonApp("知乎", "com.zhihu.android", AppCategory.READING),
    CommonApp("小红书", "com.xingin.xhs", AppCategory.SOCIAL),
    CommonApp("微博", "com.sina.weibo", AppCategory.SOCIAL),
    CommonApp("百度贴吧", "com.baidu.tieba", AppCategory.SOCIAL),
    CommonApp("王者荣耀", "com.tencent.tmgp.sgame", AppCategory.GAME),
    CommonApp("和平精英", "com.tencent.tmgp.pubgmhd", AppCategory.GAME),
    CommonApp("原神", "com.miHoYo.Yuanshen", AppCategory.GAME),
    CommonApp("崩坏：星穹铁道", "com.miHoYo.hkrpg", AppCategory.GAME),
    CommonApp("蛋仔派对", "com.netease.party", AppCategory.GAME),
    CommonApp("第五人格", "com.netease.dwrg", AppCategory.GAME),
    CommonApp("网易云音乐", "com.netease.cloudmusic", AppCategory.OTHER),
    CommonApp("QQ音乐", "com.tencent.qqmusic", AppCategory.OTHER),
    CommonApp("汽水音乐", "com.luna.music", AppCategory.OTHER),
    CommonApp("淘宝", "com.taobao.taobao", AppCategory.OTHER),
    CommonApp("拼多多", "com.xunmeng.pinduoduo", AppCategory.OTHER),
    CommonApp("京东", "com.jingdong.app.mall", AppCategory.OTHER),
    CommonApp("闲鱼", "com.taobao.idlefish", AppCategory.OTHER)
)

private val COMMON_CATEGORY: Map<String, AppCategory> =
    COMMON_APPS.associate { it.pkg to it.category }

/** 正在添加或编辑的目标 */
private data class ConfigTarget(
    val label: String,
    val packageName: String,
    val minutes: Int,
    val category: AppCategory,
    val isNew: Boolean
)

class MainActivity : ComponentActivity() {

    private val usageGranted = mutableStateOf(false)
    private val overlayGranted = mutableStateOf(false)
    private val accessibilityGranted = mutableStateOf(false)

    /** 每次回前台加一，用来触发界面重新读统计数据 */
    private val refreshTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels()
        refreshPermissions()

        setContent {
            MaterialTheme {
                AppRoot(
                    permission = usageGranted.value,
                    overlay = overlayGranted.value,
                    accessibility = accessibilityGranted.value,
                    refreshKey = refreshTick.value
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshTick.value++
    }

    private fun refreshPermissions() {
        usageGranted.value = hasUsagePermission()
        overlayGranted.value = Settings.canDrawOverlays(this)
        accessibilityGranted.value = ExitHelper.isEnabled(this)
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            // unsafeCheckOpNoThrow 是 API 29 才有的，Android 8/9 上必须走旧接口
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** 复制群号，Android 13+ 系统自己会弹提示，就不用我们再弹一次 */
    private fun copyGroupNumber() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("QQ群", QQ_GROUP))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "群号已复制：$QQ_GROUP", Toast.LENGTH_SHORT).show()
        }
    }

    /** 试着直接拉起 QQ 的群名片，没装 QQ 就提示手动搜索 */
    private fun openQqGroup() {
        val uri = Uri.parse(
            "mqqapi://card/show_pslcard?src_type=internal&version=1" +
                "&uin=$QQ_GROUP&card_type=group&source=qrcode"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "没找到 QQ，请手动搜索群号 $QQ_GROUP", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun AppRoot(
        permission: Boolean,
        overlay: Boolean,
        accessibility: Boolean,
        refreshKey: Int
    ) {
        var showHistory by remember { mutableStateOf(false) }

        if (showHistory) {
            HistoryScreen(onBack = { showHistory = false })
        } else {
            HomeScreen(
                permission = permission,
                overlay = overlay,
                accessibility = accessibility,
                refreshKey = refreshKey,
                onOpenHistory = { showHistory = true }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen(
        permission: Boolean,
        overlay: Boolean,
        accessibility: Boolean,
        refreshKey: Int,
        onOpenHistory: () -> Unit
    ) {
        val context = LocalContext.current
        var monitored by remember(refreshKey) { mutableStateOf(AppStore.loadMonitored(context)) }
        var reference by remember(refreshKey) { mutableStateOf(AppStore.loadReference(context)) }
        var todayRecord by remember(refreshKey) {
            mutableStateOf(AppStore.recordFor(context, LocalDate.now()))
        }

        var allApps by remember { mutableStateOf(loadApps()) }
        var showPicker by remember { mutableStateOf(false) }
        var showFeedback by remember { mutableStateOf(false) }
        var showReferenceDialog by remember { mutableStateOf(false) }
        var configuring by remember { mutableStateOf<ConfigTarget?>(null) }

        // 标语每次打开 App 随机一句，同一次使用内保持不变
        val slogan = remember { Slogans.random() }

        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        // Android 13+ 通知属于运行时权限，不申请的话提醒根本弹不出来
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        LaunchedEffect(permission, monitored) {
            val serviceIntent = Intent(this@MainActivity, MonitorService::class.java)
            if (permission && monitored.isNotEmpty()) {
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
        }

        val today = LocalDate.now()
        val referenceMinutes = reference.minutesFor(today)
        val usedMinutes = todayRecord?.usedMinutes ?: 0
        val snoozeCount = todayRecord?.snoozeCount ?: 0
        val percent = if (referenceMinutes > 0) usedMinutes * 100 / referenceMinutes else 0

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("黄粱一梦") },
                    actions = {
                        IconButton(onClick = onOpenHistory) {
                            Icon(
                                painter = painterResource(R.drawable.ic_history),
                                contentDescription = "历史记录"
                            )
                        }
                        IconButton(onClick = { showFeedback = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_feedback),
                                contentDescription = "反馈"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        slogan,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    TodayCard(
                        usedMinutes = usedMinutes,
                        referenceMinutes = referenceMinutes,
                        percent = percent,
                        snoozeCount = snoozeCount,
                        onEditReference = { showReferenceDialog = true }
                    )
                }

                if (!permission) {
                    item {
                        PermissionCard(
                            title = "① 需要使用情况访问权限",
                            body = "系统需要知道当前前台正在运行哪个 App，才能判断一次使用从什么时候开始。",
                            actionLabel = "打开权限设置",
                            onAction = { openUsageAccessSettings() }
                        )
                    }
                }

                if (!overlay) {
                    item {
                        PermissionCard(
                            title = "② 建议开启悬浮窗权限",
                            body = "时间到了会直接弹一个大提醒框盖在屏幕上，比通知难忽略得多。不开启也能用，只有通知。",
                            actionLabel = "打开悬浮窗设置",
                            onAction = { openOverlaySettings() }
                        )
                    }
                }

                if (!accessibility) {
                    item {
                        PermissionCard(
                            title = "③ 可选：无障碍权限",
                            body = "开启后点「好，我退出」会直接把你送回桌面。不开启也能用，" +
                                "只是得自己手动退 —— 我们会在一段时间后追问你「怎么还没退出」。",
                            actionLabel = "打开无障碍设置",
                            onAction = { openAccessibilitySettings() }
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            allApps = loadApps()
                            showPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("添加要监控的 App")
                    }
                }

                if (monitored.isEmpty()) {
                    item { Text("还没有监控任何 App") }
                } else {
                    items(monitored, key = { it.packageName }) { app ->
                        val usage = todayRecord?.apps?.get(app.packageName)
                        MonitoredRow(
                            app = app,
                            usedMinutes = usage?.usedMinutes ?: 0,
                            percent = usage?.let { percentOf(it.usedMillis, referenceMinutes) } ?: 0,
                            snoozeCount = usage?.snoozeCount ?: 0,
                            onEdit = {
                                configuring = ConfigTarget(
                                    label = app.label,
                                    packageName = app.packageName,
                                    minutes = app.minutes,
                                    category = app.category,
                                    isNew = false
                                )
                            },
                            onDelete = {
                                monitored = monitored.filterNot { it.packageName == app.packageName }
                                AppStore.saveMonitored(context, monitored)
                            }
                        )
                    }
                }
            }

            if (showPicker) {
                AppPicker(
                    allApps = allApps,
                    onDismiss = { showPicker = false },
                    onPick = { label, pkg, category ->
                        showPicker = false
                        configuring = ConfigTarget(
                            label = label,
                            packageName = pkg,
                            minutes = 15,
                            category = category,
                            isNew = true
                        )
                    }
                )
            }

            configuring?.let { target ->
                AppConfigDialog(
                    target = target,
                    onDismiss = { configuring = null },
                    onSave = { minutes, category ->
                        val item = MonitoredApp(
                            label = target.label,
                            packageName = target.packageName,
                            minutes = minutes,
                            category = category
                        )
                        monitored = monitored
                            .filterNot { it.packageName == item.packageName }
                            .plus(item)
                            .sortedBy { it.label }
                        AppStore.saveMonitored(context, monitored)
                        configuring = null
                    }
                )
            }

            if (showReferenceDialog) {
                ReferenceDialog(
                    current = reference,
                    onDismiss = { showReferenceDialog = false },
                    onSave = { updated ->
                        reference = updated
                        AppStore.saveReference(context, updated)
                        // 今天的记录快照也跟着改，日历里今天的占比才不会还是旧值
                        AppStore.refreshTodayReference(context)
                        showReferenceDialog = false
                        todayRecord = AppStore.recordFor(context, LocalDate.now())
                    }
                )
            }

            if (showFeedback) {
                FeedbackDialog(
                    onDismiss = { showFeedback = false },
                    onCopy = {
                        copyGroupNumber()
                        showFeedback = false
                    },
                    onOpenQq = { openQqGroup() }
                )
            }
        }
    }

    // ---------------- 今日概览 ----------------

    @Composable
    private fun TodayCard(
        usedMinutes: Int,
        referenceMinutes: Int,
        percent: Int,
        snoozeCount: Int,
        onEditReference: () -> Unit
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("今日娱乐", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onEditReference) { Text("参考时长") }
                }

                Spacer(Modifier.height(4.dp))

                MiniProgress(
                    fraction = if (referenceMinutes > 0) usedMinutes.toFloat() / referenceMinutes else 0f,
                    color = StatsPalette.accent(percent)
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$usedMinutes / $referenceMinutes 分钟",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatsPalette.text(percent)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "今天已延长 $snoozeCount 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---------------- 监控项 ----------------

    @Composable
    private fun MonitoredRow(
        app: MonitoredApp,
        usedMinutes: Int,
        percent: Int,
        snoozeCount: Int,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(top = 8.dp, bottom = 10.dp)) {
                Row(
                    Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${app.category.label} · 每次 ${app.minutes} 分钟提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onEdit) { Text("编辑") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    buildAnnotatedString {
                        append("今日 $usedMinutes 分钟 · 占参考 ")
                        withStyle(SpanStyle(color = StatsPalette.text(percent))) {
                            append("$percent%")
                        }
                        append(" · 延长 $snoozeCount 次")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 64.dp, end = 16.dp)
                )
            }
        }
    }

    // ---------------- 弹窗 ----------------

    @Composable
    private fun AppConfigDialog(
        target: ConfigTarget,
        onDismiss: () -> Unit,
        onSave: (Int, AppCategory) -> Unit
    ) {
        var minutes by remember { mutableStateOf(target.minutes) }
        var category by remember { mutableStateOf(target.category) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(target.packageName, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(target.label, maxLines = 1)
                }
            },
            text = {
                Column {
                    Text("每次打开后提醒：$minutes 分钟")
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = it.toInt().coerceIn(1, 120) },
                        valueRange = 1f..120f
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("分类", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    CategoryChips(selected = category, onSelect = { category = it })

                    if (!target.isNew) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "改完立刻生效：如果正在用这个 App，本轮会按新的时长重算。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(minutes, category) }) {
                    Text(if (target.isNew) "添加" else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }

    @Composable
    private fun CategoryChips(selected: AppCategory, onSelect: (AppCategory) -> Unit) {
        val rows = listOf(
            listOf(AppCategory.SHORT_VIDEO, AppCategory.READING, AppCategory.SOCIAL),
            listOf(AppCategory.GAME, AppCategory.OTHER)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        FilterChip(
                            selected = item == selected,
                            onClick = { onSelect(item) },
                            label = { Text(item.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 3) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    @Composable
    private fun ReferenceDialog(
        current: DailyReference,
        onDismiss: () -> Unit,
        onSave: (DailyReference) -> Unit
    ) {
        var separate by remember { mutableStateOf(current.separateWeekday) }
        var daily by remember { mutableStateOf(current.dailyMinutes) }
        var weekday by remember { mutableStateOf(current.weekdayMinutes) }
        var weekend by remember { mutableStateOf(current.weekendMinutes) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("每日娱乐参考时长") },
            text = {
                Column {
                    Text(
                        "只是一个参照：用来算今天的娱乐时长占多少比例。" +
                            "不会限制你，也不会有任何拦截或惩罚。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !separate,
                            onClick = { separate = false },
                            label = { Text("每天一样") }
                        )
                        FilterChip(
                            selected = separate,
                            onClick = { separate = true },
                            label = { Text("工作日/休息日") }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (!separate) {
                        MinutesSlider("每天", daily) { daily = it }
                    } else {
                        MinutesSlider("工作日", weekday) { weekday = it }
                        MinutesSlider("休息日", weekend) { weekend = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(DailyReference(separate, daily, weekday, weekend))
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }

    @Composable
    private fun MinutesSlider(label: String, minutes: Int, onChange: (Int) -> Unit) {
        Column {
            Text("$label：$minutes 分钟", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = minutes.toFloat(),
                onValueChange = {
                    onChange(
                        it.toInt().coerceIn(
                            DailyReference.MIN_MINUTES,
                            DailyReference.MAX_MINUTES
                        )
                    )
                },
                valueRange = DailyReference.MIN_MINUTES.toFloat()..
                    DailyReference.MAX_MINUTES.toFloat()
            )
        }
    }

    @Composable
    private fun PermissionCard(
        title: String,
        body: String,
        actionLabel: String,
        onAction: () -> Unit
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }

    @Composable
    private fun FeedbackDialog(
        onDismiss: () -> Unit,
        onCopy: () -> Unit,
        onOpenQq: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_feedback),
                    contentDescription = null
                )
            },
            title = { Text("反馈 / 许愿") },
            text = {
                Column {
                    Text("想要的功能、遇到的问题、觉得哪里可以做得更好，都欢迎来说一声。")
                    Spacer(Modifier.height(12.dp))
                    Text("QQ 搜索 $QQ_GROUP 加入群聊", style = MaterialTheme.typography.titleMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = onCopy) { Text("复制群号") }
            },
            dismissButton = {
                TextButton(onClick = onOpenQq) { Text("打开 QQ") }
            }
        )
    }

    @Composable
    private fun AppPicker(
        allApps: List<Pair<String, String>>,
        onDismiss: () -> Unit,
        onPick: (String, String, AppCategory) -> Unit
    ) {
        var query by remember { mutableStateOf("") }
        var active by remember { mutableStateOf<AppCategory?>(null) }
        val installedCommon = remember { installedCommonApps() }

        val filtered = remember(allApps, query, active) {
            allApps.filter { (label, pkg) ->
                val matchQuery = query.isBlank() ||
                    label.contains(query, ignoreCase = true) ||
                    pkg.contains(query, ignoreCase = true)
                val category = COMMON_CATEGORY[pkg] ?: AppCategory.OTHER
                matchQuery && (active == null || category == active)
            }
        }
        val sectionTitle = active?.let { "${it.label}（${filtered.size}）" }
            ?: "全部应用（${filtered.size}）"

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择要监控的 App") },
            text = {
                Column {
                    // 按分类筛选：先从菜单选一个类，再在里面挑 App
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = active == null,
                            onClick = { active = null },
                            label = { Text("全部") }
                        )
                        AppCategory.entries.forEach { category ->
                            FilterChip(
                                selected = active == category,
                                onClick = { active = category },
                                label = { Text(category.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("按名字搜索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    if (installedCommon.isNotEmpty() && query.isBlank() && active == null) {
                        Text("常用娱乐 App", style = MaterialTheme.typography.titleSmall)
                        LazyColumn(Modifier.heightIn(max = 130.dp)) {
                            items(installedCommon) { (label, pkg) ->
                                AppPickRow(label = label, pkg = pkg) {
                                    onPick(label, pkg, COMMON_CATEGORY[pkg] ?: AppCategory.OTHER)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(sectionTitle, style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 210.dp)) {
                        items(filtered) { (label, pkg) ->
                            AppPickRow(label = label, pkg = pkg) {
                                // 在某个分类下挑的，就用那个分类；已知的仍以已知分类为准
                                onPick(
                                    label,
                                    pkg,
                                    COMMON_CATEGORY[pkg] ?: active ?: AppCategory.OTHER
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "没有匹配的应用。不常见的 App 都归在「其他」里，也可以切到「全部」直接搜。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }

    @Composable
    private fun AppPickRow(label: String, pkg: String, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = pkg, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    (COMMON_CATEGORY[pkg] ?: AppCategory.OTHER).label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---------------- 数据读取 ----------------

    /** 手机上真正装了的那部分常见娱乐 App */
    private fun installedCommonApps(): List<Pair<String, String>> {
        return COMMON_APPS.mapNotNull { item ->
            val info = try {
                packageManager.getApplicationInfo(item.pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } ?: return@mapNotNull null

            val realLabel = info.loadLabel(packageManager).toString().ifBlank { item.label }
            realLabel to item.pkg
        }
    }

    private fun loadApps(): List<Pair<String, String>> {
        val collator = Collator.getInstance(Locale.CHINA)
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.loadLabel(packageManager).toString() to it.packageName }
            .sortedWith { a, b -> collator.compare(a.first, b.first) }
            .toList()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "monitor",
                "后台监控",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "break_reminder",
                "使用提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}
