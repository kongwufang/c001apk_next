package com.example.c001apk.desktop.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.c001apk.desktop.app.theme.ThemeState
import com.example.c001apk.desktop.app.ui.detail.DetailScreen
import com.example.c001apk.desktop.app.ui.home.HomeScreen
import com.example.c001apk.desktop.app.ui.motion.m3Slide
import com.example.c001apk.desktop.app.ui.mine.MineScreen
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.Session

/**
 * 主外壳 —— 1:1 对齐 Android 端 **横屏** 布局
 * `app/src/main/res/layout-land/activity_main.xml`：
 *
 * ```xml
 * <ConstraintLayout>
 *     <ViewPager2          id=viewPager
 *                          width=0dp height=match_parent
 *                          constraint:start_toEndOf=@id/bottomNav
 *                          constraint:top/bottom/end=parent />
 *     <NavigationRailView  id=bottomNav
 *                          width=wrap_content height=match_parent
 *                          constraint:start/top/bottom=parent
 *                          app:menu=@menu/nav_menu
 *                          app:menuGravity="top"
 *                          app:labelVisibilityMode="selected" />
 * </ConstraintLayout>
 * ```
 *
 * 关键点（都来自 Android 侧的真实定义）：
 * * 导航栏在**左**、宽度 `wrap_content`、高度撑满；内容区占剩余宽度（`0dp` = 约束撑满）
 * * `menuGravity="top"` → 导航项**靠上**排列（不是垂直居中）
 * * `labelVisibilityMode="selected"` → **只有选中项显示文字**
 * * 导航项只有 **2 个**（见 `res/menu/nav_menu.xml`）：
 *   `navigation_home`（首页 `tab_home`）与 `navigation_mine`（我的 `tab_mine`）
 * * 图标是**双态 selector**：选中 = `*_baseline`（实心），未选中 = `*_outline`（描边）
 *   Compose 里无 selector，故按选中状态切换 filled / outlined 图标等价实现
 *
 * ★ 关于「滚动收起导航栏」：**横屏不做**。
 *   `MainActivity` 只对 `BottomNavigationView`（竖屏）挂 `HideBottomViewOnScrollBehavior`，
 *   横屏的 `NavigationRailView` 被 `if (this is BottomNavigationView)` 守卫挡住，
 *   `showNavigationView()/hideNavigationView()` 在横屏是**空操作** → Rail 常驻。
 *   所以桌面端**不要**实现滚动隐藏。 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainShell(
    client: Client,
    session: Session,
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
) {
    // 导航项顺序与 nav_menu.xml 一致：首页 → 我的
    var index by remember { mutableStateOf(0) }
    val items = remember { NavDestination.entries }
    // 详情页：非空表示当前覆盖在 tab 之上（对应 Android 端另起 FeedActivity，整窗转场）
    var openFeedId by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize()) {
        // ── 左侧 NavigationRailView ──
        NavigationRail(
            // menuGravity="top"：靠上排列
            header = { Spacer(Modifier.height(12.dp)) },
        ) {
            items.forEachIndexed { i, dest ->
                NavigationRailItem(
                    selected = index == i,
                    onClick = { index = i },
                    // labelVisibilityMode="selected"：仅选中项显示文字
                    alwaysShowLabel = false,
                    icon = {
                        Icon(
                            imageVector = if (index == i) dest.iconFilled else dest.iconOutlined,
                            contentDescription = dest.label,
                        )
                    },
                    label = { Text(dest.label) },
                )
            }
        }

        // 横屏布局里没有分隔线（靠 NavigationRail 自带表面色区分），
        // 但 M3E 的 rail 与内容同色时边界不明显，这里用 outlineVariant 细线，
        // 与 fragment_home.xml 里 AppBar 底部那根 1px colorSurfaceVariant 同源。
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Box(Modifier.fillMaxSize()) {
            // 详情页覆盖在 tab 之上。
            // Android 端详情是**另起一个 Activity**，默认档转场是整窗水平推挤
            // （exp_slide_emph_300_*：300ms + emphasized_decelerate，两层各 100% 视口宽）。
            AnimatedContent(
                targetState = openFeedId,
                transitionSpec = { m3Slide(forward = targetState != null) },
                label = "detailPager",
            ) { fid ->
                if (fid != null) {
                    DetailScreen(client = client, feedId = fid, onBack = { openFeedId = null })
                } else {
                    // ViewPager2 等价。
                    // 注：Android 端 tab 切换是 ViewPager2 的 smooth scroll，
                    // 并非 window 级 Animation；这里用同一套 M3E 规格做等价观感。
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = { m3Slide(forward = targetState > initialState) },
                        label = "viewPager",
                    ) { i ->
                        when (items[i]) {
                            NavDestination.Home -> HomeScreen(
                                client = client,
                                theme = theme,
                                onThemeChange = onThemeChange,
                                onOpenFeed = { openFeedId = it },
                            )
                            NavDestination.Mine -> MineScreen(client = client, session = session)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 导航目的地 —— 对齐 `res/menu/nav_menu.xml`（只有两项）。
 *
 * 图标对应关系（`ic_home_selector` / `ic_mine_selector`）：
 * ```
 * ic_home_selector:  state_checked → ic_home_baseline    否则 → ic_home_outline
 * ic_mine_selector:  state_checked → ic_mine_baseline    否则 → ic_mine_outline
 * ```
 * Android 端是自有矢量图；这里先用 Material 官方 filled/outlined 等价替代，
 * 待把 `ic_home_baseline/outline`、`ic_mine_baseline/outline` 的 path 数据移植过来后替换。
 */
private enum class NavDestination(
    val label: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
) {
    Home("首页", Icons.Filled.Home, Icons.Outlined.Home),
    Mine("我的", Icons.Filled.Person, Icons.Outlined.Person),
}
