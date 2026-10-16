package com.example.c001apk.desktop.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.c001apk.desktop.core.model.LoginInfo
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.Session
import com.example.c001apk.desktop.core.net.executeSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我的」页 —— 对齐 `res/menu/nav_menu.xml` 的 `navigation_mine`（`MineFragment`）。
 *
 * 参考基准（见 `.codebuddy/handover-memory/reference_ui_baseline_desktop.md`）：
 * 用户认可 `daimiaopeng/coolapk-desktop` 的「我的」界面 ——
 * 页面浅灰底 + 白色卡片、卡片圆角 12px、卡间距 8px、
 * 头像 60dp、统计数字 22px / 标签 12px、功能宫格 4 列 8 格。
 *
 * ⚠️ 当前是**最小可用版**（顶部资料 + 数据行），宫格与卡片列表待下阶段按上述规格细化。
 */
@Composable
fun MineScreen(client: Client, session: Session) {
    var info by remember { mutableStateOf<LoginInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = client.api.checkLoginInfo().executeSync()
                if ((resp.status ?: 0) != 0) throw IllegalStateException(resp.message ?: "登录信息有误")
                resp.data
            }
        }.onSuccess { info = it }
            .onFailure { error = "${it::class.simpleName}: ${it.message}" }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏（对应 Android 端用 surfaceContainer 与页面底拉开一档）
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        val d = info?.data
        if (d != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    d.username ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row {
                    StatItem("等级", d.level ?: "-")
                    Spacer(Modifier.width(24.dp))
                    StatItem("UID", d.uid ?: "-")
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    error ?: "正在获取账号信息…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 数据项。数值规格来自参考基准：**统计数字 22px / 标签 12px**
 * （`reference_ui_baseline_desktop.md`）。
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
