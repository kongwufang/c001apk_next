package com.example.c001apk.desktop.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用主题 —— 对齐 Android 端 `app/src/main/res/values/themes.xml` 的主题链：
 *
 * ```
 * Platform.Theme.C001apk → Theme.C001apk.M3E        （M3E 形态层）
 * 页面底        = ?attr/colorSurface
 * 顶栏 / 卡片   = ?attr/colorSurfaceContainer        ← 刻意拉开一档
 * windowBackground = ?attr/colorSurface（必须不透明）
 * ```
 *
 * themes.xml 里对「为什么顶栏用 surfaceContainer」有明确注释：
 * > 目的：页面里出现可见的色块边界，转场滑动/缩放时才能看出内容在移动
 * > （rikkahub 同款角色分配：页面底 = surface，顶栏 / 卡片 = surfaceContainer）
 *
 * 所以这里**不能**把顶栏和页面底设成同一个颜色 —— 那会丢掉转场的视觉锚点。
 */
@Composable
fun C001apkTheme(state: ThemeState = ThemeState(), content: @Composable () -> Unit) {
    val base = state.color.scheme(dark = state.dark)

    // 纯黑深色（对应 Android 端 ThemeOverlay_Black）：
    // 把容器色族压到纯黑，只保留文字/图标的对比度。
    val scheme = if (state.dark && state.blackDark) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF121212),
            surfaceContainerHighest = Color(0xFF1A1A1A),
        )
    } else {
        base
    }

    CompositionLocalProvider(
        LocalAppDimens provides AppDimens(),
    ) {
        // 注意：**不要**在这里改 LocalDensity 去"放大"UI。
        // 曾经试过把密度固定成真机的 240dpi（density ×1.5）来让视觉尺寸接近手机，
        // 结果整个界面明显过大 —— 用户反馈"显示布局有点大了，调回原来的"。
        //
        // 正确做法是：布局尺寸本身按真机实测的 dp 值写（导航栏 96dp、
        // 顶栏 73dp、tab 56×48dp …），用系统默认密度渲染即可。
        // 桌面窗口本来就比手机宽（1280×800dp vs 853×640dp），显示更多内容是合理的。
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * 尺寸令牌。数值来自 Android 端资源（`dimens.xml` / layouts / `themes.xml`）。
 * 只收录已在 Android 端**实际查到**的值，其余留待按需补充（见注释来源）。
 */
data class AppDimens(
    // ── 来自 themes.xml ──
    /** `res/values/themes.xml` → `iconMiniScrollCardRadius`：cornerSize 18dp */
    val iconMiniScrollCardRadius: Dp = 18.dp,
    /** `res/values/themes.xml` → `searchTopicRadius`：cornerSize 12dp */
    val searchTopicRadius: Dp = 12.dp,
    /** `res/values/themes.xml` → `textScrollImgRadius`：上两角 12dp */
    val textScrollImgRadius: Dp = 12.dp,
    /** `res/values/themes.xml` → `roundImageStyle`：cornerSize 8dp */
    val roundImageRadius: Dp = 8.dp,
    /** `res/values/themes.xml` → `chatImageStyle`：cornerSize 14dp（私信气泡） */
    val chatImageRadius: Dp = 14.dp,
    /** `res/values/themes.xml` → `CustomShapeAppearanceBottomSheetDialog`：上两角 16dp */
    val bottomSheetTopRadius: Dp = 16.dp,

    // ── 来自 fragment_home.xml（横屏首页顶栏）──
    /** `fragment_home.xml` → search ImageView：marginStart/End = 10dp */
    val appBarIconMarginH: Dp = 10.dp,
    /** `fragment_home.xml` → message FrameLayout：padding 10dp */
    val appBarIconPadding: Dp = 10.dp,
    /** `fragment_home.xml` → 消息图标 24×24dp */
    val appBarIconSize: Dp = 24.dp,
    /** `fragment_home.xml` → messageBadge：高 15dp、minWidth 15dp、paddingH 4dp */
    val badgeHeight: Dp = 15.dp,
    val badgeMinWidth: Dp = 15.dp,
    val badgePaddingH: Dp = 4.dp,
    /** `fragment_home.xml` → AppBar 底部 1px 分隔线（用 dp 近似） */
    val appBarDividerThickness: Dp = 1.dp,

    // ── 字号（来自 themes.xml / 布局）──
    /** `themes.xml` → `TabText`：16sp（另有 `TabText15` = 15sp） */
    val tabTextSizeSp: Int = 16,
    /** `fragment_home.xml` → messageBadge：9sp */
    val badgeTextSizeSp: Int = 9,
)

val LocalAppDimens = staticCompositionLocalOf { AppDimens() }

/** 便捷访问：`AppTheme.dimens.tabTextSizeSp` */
object AppTheme {
    val dimens: AppDimens
        @Composable get() = LocalAppDimens.current
}
