package com.example.c001apk.desktop.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.app.ui.common.AppCard
import com.example.c001apk.desktop.app.ui.common.UserAvatar
import com.example.c001apk.desktop.core.model.LoginInfo
// UserProfile 是 ApiService 的**嵌套类**（定义在 interface 内部），
// 所以要按 ApiService.UserProfile 导入 —— 直接 import ...net.UserProfile 会 Unresolved。
import com.example.c001apk.desktop.core.net.ApiService.UserProfile
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.Session
import com.example.c001apk.desktop.core.net.executeSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我的」页 —— 对齐 Android 端 `ui/message/MineFragment` + `res/layout/fragment_mine.xml`。
 *
 * ## 实测规格（来自布局与 `item_mine_menu.xml`）
 * 用户卡（`profileLayout`，上下 padding 各 10dp）：
 * ```
 * avatar       60×60dp，圆形（circleImageStyle cornerSize=50%），marginStart=20dp
 * verifyBadge  14×14dp，padding 2dp，压在 avatar 右下角
 * name         0dp 宽，marginStart=14dp，18sp bold，ellipsize=end
 * level        marginStart=14dp marginTop=6dp，12sp bold，?android:textColorSecondary
 * exp          10sp，?android:textColorSecondary
 * progress     marginStart=14dp marginTop=6dp，trackThickness=5dp
 *              trackCornerRadius=20dp，indicatorTrackGapSize=0，trackStopIndicatorSize=0
 * 分割线        1px，?attr/colorSurfaceVariant
 * ```
 * 功能宫格（8 项，2 行 × 4 列，每项 `layout_weight=1`）：
 * ```
 * 离线缓存 #4caf50   我的关注 #00b8cf   我的收藏 #2196f3   我的赞 #ee61d6
 * 我的回复 #ff9800   我的常去 #a56bff   浏览历史 #00b8cf   我的装备 #2196f3
 * ```
 * 统计数字 22px / 标签 12px 来自用户认可的参考基准
 * （`.codebuddy/handover-memory/reference_ui_baseline_desktop.md`）。
 *
 * ## 登录态
 * `MineMenuAdapter` 里只给「离线缓存 / 我的装备 / 浏览历史」无条件挂点击，
 * 其余 5 项**仅在已登录时**可点。这里照做（未登录时置灰）。
 */
@Composable
fun MineScreen(client: Client, session: Session) {
    var login by remember { mutableStateOf<LoginInfo?>(null) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val info = client.api.checkLoginInfo().executeSync()
                if ((info.status ?: 0) != 0) throw IllegalStateException(info.message ?: "登录信息有误")
                val li = info.data
                // 已登录才有必要拉资料
                val pf = li?.uid?.takeIf { it.isNotBlank() }?.let { uid ->
                    runCatching {
                        val r2 = client.api.getUserProfile(uid).executeSync()
                        if ((r2.status ?: 0) == 0) r2.data else null
                    }.getOrNull()
                }
                li to pf
            }
        }
        r.onSuccess { pair ->
            login = pair.first
            profile = pair.second
            loading = false
        }.onFailure {
            error = "${it::class.simpleName}: ${it.message}"
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏（Android 端是 MaterialToolbar，title=@string/tab_mine）
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("我的", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        // 分割线 1px ?attr/colorSurfaceVariant
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ProfileCard(login = login, profile = profile)
                Spacer(Modifier.height(8.dp))
                MenuGrid(isLogin = login?.token != null)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 用户卡 —— 对齐 `fragment_mine.xml` 的 `profileLayout`（含未登录态 `clickToLogin`） */
@Composable
private fun ProfileCard(login: LoginInfo?, profile: UserProfile?) {
    val loggedIn = login?.token != null
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(20.dp))
            // avatar 60dp 圆形
            val avatar = profile?.userAvatar?.takeIf { it.isNotBlank() }
            if (loggedIn && avatar != null) {
                UserAvatar(avatar, size = 60.dp)
            } else {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (profile?.username ?: login?.username)?.firstOrNull()?.toString() ?: "?",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))

            if (!loggedIn) {
                // 未登录：给登录入口（对应 clickToLogin）
                Column(Modifier.weight(1f)) {
                    Text("未登录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "登录后可查看关注 / 收藏 / 消息",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                LoginButton()
                Spacer(Modifier.width(20.dp))
            } else {
                val name = profile?.username ?: login?.username ?: "—"
                val level = profile?.level ?: login?.level
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!level.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Lv$level",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
            }
        }

        // 数据行：粉丝 / 关注 / 动态
        if (loggedIn && profile != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(start = 20.dp)) {
                StatItem("粉丝", profile.fans)
                Spacer(Modifier.width(28.dp))
                StatItem("关注", profile.follow)
                Spacer(Modifier.width(28.dp))
                StatItem("动态", profile.feed)
            }
        }
    }
}

/** 统计项：数字 22sp bold / 标签 12sp（参考基准规格） */
@Composable
private fun StatItem(label: String, value: Int?) {
    Column {
        Text(value?.toString() ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 宫格项（对齐 `item_mine_menu.xml` 的 id 顺序 / 图标色 / 文案） */
private data class MenuEntry(
    val label: String,
    val tint: Color,
    /** 是否只在已登录时可点 —— `MineMenuAdapter` 里只有 3 项无条件挂点击 */
    val requiresLogin: Boolean,
)

private val MENU_ENTRIES = listOf(
    MenuEntry("离线缓存", Color(0xFF4CAF50), false),
    MenuEntry("我的关注", Color(0xFF00B8CF), true),
    MenuEntry("我的收藏", Color(0xFF2196F3), true),
    MenuEntry("我的赞", Color(0xFFEE61D6), true),
    MenuEntry("我的回复", Color(0xFFFF9800), true),
    MenuEntry("我的常去", Color(0xFFA56BFF), true),
    MenuEntry("浏览历史", Color(0xFF00B8CF), false),
    MenuEntry("我的装备", Color(0xFF2196F3), false),
)

/** 功能宫格：2 行 × 4 列 */
@Composable
private fun MenuGrid(isLogin: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        MENU_ENTRIES.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { e ->
                    MenuCell(e, enabled = !e.requiresLogin || isLogin, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 单个宫格：卡片 + 图标位 + 12sp 标签。
 *
 * Android 端是矢量图标（`ic_archive` / `ic_star` …），桌面端还没移植那些 path 数据，
 * 故先用 **tint 圆角块 + 文案首字** 代替 —— 尺寸与位置与规格一致，等移植图标再换。
 */
@Composable
private fun MenuCell(e: MenuEntry, enabled: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else 0.4f
    AppCard(modifier.then(Modifier.clickable(enabled = enabled) { /* 目标页待实现 */ })) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(e.tint.copy(alpha = 0.16f * alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    e.label.first().toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = e.tint.copy(alpha = alpha),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                e.label,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}

/** 未登录时的登录按钮（对应 `clickToLogin`） */
@Composable
private fun LoginButton() {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { /* 登录流程：待实现 */ }
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text("点击登录", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}
