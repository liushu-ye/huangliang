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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.Collator
import java.util.Locale

/** 反馈 QQ 群 */
private const val QQ_GROUP = "535391647"

data class MonitoredApp(
    val label: String,
    val packageName: String,
    val minutes: Int
)

/**
 * 常见娱乐 App 的包名。装在手机上的会出现在选择器的「常用」列表里，
 * 省得在几百个应用里翻。
 */
private val COMMON_APPS = listOf(
    "哔哩哔哩" to "tv.danmaku.bili",
    "哔哩哔哩国际版" to "com.bilibili.app.in",
    "抖音" to "com.ss.android.ugc.aweme",
    "抖音极速版" to "com.ss.android.ugc.aweme.lite",
    "快手" to "com.smile.gifmaker",
    "快手极速版" to "com.kuaishou.nebula",
    "小红书" to "com.xingin.xhs",
    "微博" to "com.sina.weibo",
    "番茄小说" to "com.dragon.read",
    "七猫免费小说" to "com.kmxs.reader",
    "微信读书" to "com.tencent.weread",
    "起点读书" to "com.qidian.QDReader",
    "晋江小说阅读" to "com.jjwxc.reader",
    "掌阅" to "com.zhangyue.read",
    "腾讯视频" to "com.tencent.qqlive",
    "爱奇艺" to "com.qiyi.video",
    "优酷" to "com.youku.phone",
    "芒果TV" to "com.hunantv.imgo.activity",
    "西瓜视频" to "com.ss.android.article.video",
    "今日头条" to "com.ss.android.article.news",
    "网易云音乐" to "com.netease.cloudmusic",
    "QQ音乐" to "com.tencent.qqmusic",
    "汽水音乐" to "com.luna.music",
    "王者荣耀" to "com.tencent.tmgp.sgame",
    "和平精英" to "com.tencent.tmgp.pubgmhd",
    "原神" to "com.miHoYo.Yuanshen",
    "崩坏：星穹铁道" to "com.miHoYo.hkrpg",
    "蛋仔派对" to "com.netease.party",
    "第五人格" to "com.netease.dwrg",
    "知乎" to "com.zhihu.android",
    "百度贴吧" to "com.baidu.tieba",
    "淘宝" to "com.taobao.taobao",
    "拼多多" to "com.xunmeng.pinduoduo",
    "京东" to "com.jingdong.app.mall",
    "闲鱼" to "com.taobao.idlefish"
)

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private val usageGranted = mutableStateOf(false)
    private val overlayGranted = mutableStateOf(false)
    private val accessibilityGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels()
        refreshPermissions()

        setContent {
            MaterialTheme {
                AppScreen(
                    permission = usageGranted.value,
                    overlay = overlayGranted.value,
                    accessibility = accessibilityGranted.value
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置页返回，几个权限都重新检查一次
        refreshPermissions()
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppScreen(permission: Boolean, overlay: Boolean, accessibility: Boolean) {
        var allApps by remember { mutableStateOf(loadApps()) }
        var selected by remember { mutableStateOf(loadSelected()) }
        var showPicker by remember { mutableStateOf(false) }
        var showFeedback by remember { mutableStateOf(false) }

        val context = LocalContext.current
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

        LaunchedEffect(permission, selected) {
            val serviceIntent = Intent(this@MainActivity, MonitorService::class.java)
            if (permission && selected.isNotEmpty()) {
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("黄粱一梦") },
                    actions = {
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
                        "不是统计你今天用了多久，而是在每一次沉浸时提醒你。",
                        style = MaterialTheme.typography.bodyLarge
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

                if (selected.isEmpty()) {
                    item { Text("还没有监控任何 App") }
                } else {
                    items(selected, key = { it.packageName }) { app ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                                    Text("每次打开 ${app.minutes} 分钟后提醒")
                                }
                                TextButton(onClick = {
                                    selected = selected.filterNot { it.packageName == app.packageName }
                                    saveSelected(selected)
                                }) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }

            if (showPicker) {
                AppPicker(
                    allApps = allApps,
                    onDismiss = { showPicker = false },
                    onAdd = { label, pkg, minutes ->
                        val item = MonitoredApp(label, pkg, minutes)
                        selected = (selected.filterNot { it.packageName == item.packageName } + item)
                        saveSelected(selected)
                        showPicker = false
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
        onAdd: (String, String, Int) -> Unit
    ) {
        var minutes by remember { mutableStateOf(15) }
        var query by remember { mutableStateOf("") }

        val installedCommon = remember { installedCommonApps() }
        val filtered = remember(allApps, query) {
            if (query.isBlank()) allApps
            else allApps.filter {
                it.first.contains(query, ignoreCase = true) ||
                    it.second.contains(query, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择要监控的 App") },
            text = {
                Column {
                    Text("提醒时间：$minutes 分钟")
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = it.toInt().coerceIn(1, 120) },
                        valueRange = 1f..120f
                    )

                    Spacer(Modifier.height(8.dp))

                    if (installedCommon.isNotEmpty()) {
                        Text("常用娱乐 App（点一下直接添加）", style = MaterialTheme.typography.titleSmall)
                        LazyColumn(Modifier.heightIn(max = 140.dp)) {
                            items(installedCommon) { (label, pkg) ->
                                TextButton(
                                    onClick = { onAdd(label, pkg, minutes) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        "全部应用（${filtered.size}）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("按名字搜索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(filtered) { (label, pkg) ->
                            TextButton(
                                onClick = { onAdd(label, pkg, minutes) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }

    /** 手机上真正装了的那部分常见娱乐 App */
    private fun installedCommonApps(): List<Pair<String, String>> {
        return COMMON_APPS.mapNotNull { (name, pkg) ->
            val info = try {
                packageManager.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } ?: return@mapNotNull null

            val realLabel = info.loadLabel(packageManager).toString().ifBlank { name }
            realLabel to pkg
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

    private fun loadSelected(): List<MonitoredApp> {
        val raw = prefs.getStringSet("monitored", emptySet()) ?: emptySet()
        return raw.mapNotNull {
            val p = it.split("|")
            if (p.size != 3) null
            else MonitoredApp(p[0], p[1], p[2].toIntOrNull() ?: 15)
        }
    }

    private fun saveSelected(list: List<MonitoredApp>) {
        prefs.edit()
            .putStringSet(
                "monitored",
                list.map { "${it.label}|${it.packageName}|${it.minutes}" }.toSet()
            )
            .apply()
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
