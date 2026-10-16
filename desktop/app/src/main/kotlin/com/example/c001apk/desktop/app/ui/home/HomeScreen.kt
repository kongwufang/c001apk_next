package com.example.c001apk.desktop.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.app.theme.AppTheme
import com.example.c001apk.desktop.app.theme.ThemeState
import com.example.c001apk.desktop.core.model.CheckCount
import com.example.c001apk.desktop.core.net.Client
import com.example.c001apk.desktop.core.net.executeSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 首页 —— 对齐 Android 端 `res/layout/fragment_home.xml`：
 *
 * ```xml
 * <CoordinatorLayout edgeToEdge=true>
 *   <AppBarLayout id=appBar background=@null>
 *     <ConstraintLayout>
 *       <TabLayout   id=tabLayout  width=0dp
 *                    style=Widget.Material3.TabLayout.OnSurface.Trans
 *                    tabGravity=fill tabMode=auto tabMinWidth=0dp
 *                    tabTextAppearance=@style/TabText              <!-- themes.xml: 16sp -->
 *                    constraint:end_toStartOf=@id/search />
 *       <ImageView   id=search  marginH=10dp
 *                    background=?actionBarItemBackground src=@drawable/ic_search />
 *       <FrameLayout id=message padding=10dp constraint:end=parent>
 *         <ImageView 24x24dp src=@drawable/ic_message_outline />
 *         <TextView  id=messageBadge  height=15dp minWidth=15dp textSize=9sp
 *                    background=@drawable/badge_red gravity=top|end
 *                    text = count>99 ? "99+" : count
 *                    visible = count>0 />
 *       </FrameLayout>
 *     </ConstraintLayout>
 *     <View height=1px background=?attr/colorSurfaceVariant />   <!-- AppBar 底部分隔线 -->
 *   </AppBarLayout>
 *   <ViewPager2 id=viewPager
 *               behavior=@string/appbar_scrolling_view_behavior />  <!-- 随 AppBar 滚动 -->
 * </CoordinatorLayout>
 * ```
 *
 * 顶栏底色用 `colorSurfaceContainer`（不是 surface）—— 见 `themes.xml` 的注释：
 * 刻意与页面底拉开一档，转场时才能看出内容在移动。
 */
@Composable
fun HomeScreen(
    client: Client,
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFeed: (String) -> Unit = {},
) {
    var tabs by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf(0) }
    var unread by remember { mutableStateOf<Int?>(null) }
    var badgeCount by remember { mutableStateOf(0) }

    // 拉 tab 配置与未读数（对应 HomeFragment 里的 tabList / UnreadCounter）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = client.api.checkCount().executeSync()
                resp.data
            }.getOrNull()
        }.let { c: CheckCount? -> badgeCount = c?.badge ?: 0 }

        withContext(Dispatchers.IO) {
            runCatching {
                val resp = client.api.loadConfig(key = "home_tab", refresh = 0).executeSync()
                resp.data?.toString()
            }.getOrNull()
        }.let { /* tab 配置解析留待接 cardui/uiskin 时补 */ }
    }

    // 暂时用固定 tab（真实 tab 列表来自服务端配置，见 HomeFragment 的 tabList）
    if (tabs.isEmpty()) tabs = DEFAULT_TABS
    unread = badgeCount

    Column(Modifier.fillMaxSize()) {
        HomeTopBar(
            tabs = tabs,
            selected = selected,
            onSelect = { selected = it },
            badge = unread,
        )

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(280)) { w -> dir * w / 8 } + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(tween(280)) { w -> -dir * w / 8 } + fadeOut(tween(160))
                        )
                },
                label = "homeTabPager",
            ) { i ->
                HomeTabContent(
                    client = client,
                    tab = tabs.getOrElse(i) { "头条" },
                    onOpenFeed = onOpenFeed,
                )
            }
        }
    }
}

/**
 * 单个 tab。
 *
 * 尺寸全部来自真机 UI 树实测（853×640dp 横屏平板，MEmu / Android 9）：
 * ```
 * 每个 tab            56dp 宽 × 48dp 高（量到 84×72px @240dpi）
 * tab 文字            16sp（= @style/TabText，themes.xml 里定义）
 * 下划线指示器         约 3dp 高（Android TabLayout 默认）
 * tab 容器左侧留白     让第一个 tab 的 x 落在 159px≈106dp（= rail 96dp + 10dp）
 * ```
 * 注意**不是**平分宽度 —— Android 用 `tabMode=auto` 放在横向滚动容器里。
 */
@Composable
private fun AppTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(56.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            fontSize = 16.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        // 选中下划线
        Box(
            Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent
                ),
        )
    }
}

/** 顶栏高度 —— 实测真机 appBar 为 73dp */
private val APP_BAR_HEIGHT = 73.dp

/**
 * 顶栏。结构与 `fragment_home.xml` 的 `AppBarLayout` 等价：
 * 左 TabLayout（横向滚动）+ 右 search + 右 message（带角标），底部 1px 分隔线。
 */
@Composable
private fun HomeTopBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    badge: Int?,
) {
    val d = AppTheme.dimens
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                // 实测：真机 appBar 高 73dp（1280×960@240dpi 下量到 y=0..110px）
                .height(APP_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── TabLayout：**横向可滚动** ──
            // 实测（真机 UI 树，853×640dp 横屏）：
            //   HorizontalScrollView#tabLayout  x=96dp 宽 669dp 高 48dp
            //   每个 tab 56dp 宽 × 48dp 高
            //   7 个 tab 共 392dp < 669dp，但 Android 用的是 tabMode=auto + 横向滚动容器，
            //   所以**不能被 weight 平均摊开** —— 那样每个 tab 会变成 ~95dp，与真机不符。
            LazyRow(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(tabs.size) { i ->
                    AppTab(
                        title = tabs[i],
                        selected = i == selected,
                        onClick = { onSelect(i) },
                    )
                }
            }

            // search：marginH=10dp，背景 ?actionBarItemBackground（M3 图标按钮）
            Spacer(Modifier.width(d.appBarIconMarginH))
            AppBarIconButton(onClick = { /* 搜索页：待实现 */ }) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(d.appBarIconSize),
                )
            }
            Spacer(Modifier.width(d.appBarIconMarginH))

            // message：padding=10dp，右上角未读角标
            AppBarIconButton(
                onClick = { /* 消息中心：待实现 */ },
                contentPadding = d.appBarIconPadding,
            ) {
                BadgedIcon(count = badge ?: 0) {
                    Icon(
                        Icons.Outlined.Mail,
                        contentDescription = "消息中心",
                        modifier = Modifier.size(d.appBarIconSize),
                    )
                }
            }
        }

        // AppBar 底部 1px 分隔线，颜色 ?attr/colorSurfaceVariant
        Box(
            Modifier
                .fillMaxWidth()
                .height(d.appBarDividerThickness)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun AppBarIconButton(
    onClick: () -> Unit,
    contentPadding: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 未读角标 —— 对齐 `fragment_home.xml` 的 `messageBadge`：
 * 高 15dp、minWidth 15dp、paddingH 4dp、textSize 9sp、背景 `@drawable/badge_red`、
 * gravity `top|end`；`count > 99` 显示 `"99+"`；`count == 0` 时 `visibility=gone`。
 */
@Composable
private fun BadgedIcon(count: Int, content: @Composable () -> Unit) {
    val d = AppTheme.dimens
    Box {
        content()
        if (count > 0) {
            val text = if (count > 99) "99+" else count.toString()
            val appear by animateFloatAsState(1f, tween(160), label = "badge")
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { alpha = appear }
                    .height(d.badgeHeight)
                    // minWidth 15dp；数字变长时自然撑开
                    .widthIn(min = d.badgeMinWidth)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = d.badgeTextSizeSp.sp,
                    // 角标高 15dp、字 9sp：用等值行高把文字垂直居中，不额外给 lineHeight 传 dp
                    lineHeight = d.badgeTextSizeSp.sp,
                    modifier = Modifier.padding(horizontal = d.badgePaddingH),
                )
            }
        }
    }
}

/** 单个 tab 的内容：动态列表（卡片/动态渲染在下阶段细化） */
@Composable
private fun HomeTabContent(
    client: Client,
    tab: String,
    onOpenFeed: (String) -> Unit,
) {
    FeedList(client = client, tab = tab, onOpenFeed = onOpenFeed)
}

/**
 * 首页分类 tab 的默认值 —— 对齐 Android 端 `HomeViewModel.defaultList` + `HomeFragment`。
 *
 * 真实列表与承载 Fragment（顺序即此）：
 * ```
 * pos 0  关注  → HomeFeedFragment("follow")
 * pos 1  应用  → AppListFragment()
 * pos 2  头条  → HomeFeedFragment("feed")     ★ 默认选中
 * pos 3  热榜  → HomeFeedFragment("rank")
 * pos 4  话题  → HomeTopicFragment("topic")
 * pos 5  数码  → HomeTopicFragment("product")
 * pos 6  酷图  → HomeFeedFragment("coolPic")
 * ```
 * `HomeFragment` 在 `TabLayoutMediator` 建好后执行
 * `setCurrentItem(enableList.indexOf("头条"), false)` —— 所以**默认落在「头条」**（第 2 项），
 * 而不是第 0 项。用户可在设置里增删 tab，故真实列表是动态的。
 */
private val DEFAULT_TABS = listOf("关注", "应用", "头条", "热榜", "话题", "数码", "酷图")

/** 默认选中的 tab（对应 Android 端 `enableList.indexOf("头条")`） */
private const val DEFAULT_TAB = "头条"
