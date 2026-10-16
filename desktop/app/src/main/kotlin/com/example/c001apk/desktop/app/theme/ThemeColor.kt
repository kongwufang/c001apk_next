package com.example.c001apk.desktop.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 配色方案 —— 对齐 Android 端 `app/build.gradle.kts` 里的 `materialThemeBuilder` 配置。
 *
 * Android 端用 rikka 的 `materialthemebuilder` 插件，从 **seed 色** 生成整套 M3 令牌：
 * ```
 * materialThemeBuilder {
 *     themes {
 *         for ((name, color) in listOf(
 *             "Default" to "6750A4",   // Material 3 标准基线色
 *             "Red" to "F44336", … )) {
 *             create("Material$name") {
 *                 lightThemeFormat = "ThemeOverlay.Light.%s"
 *                 darkThemeFormat  = "ThemeOverlay.Dark.%s"
 *                 primaryColor = "#$color"
 *             }
 *         }
 *     }
 * }
 * ```
 * Compose 的 [dynamicLightColorScheme] / [dynamicDarkColorScheme] 吃的是**同一个 seed 色**，
 * 生成的令牌体系与 Android 端一致 —— 所以配色这块可以做到精确对齐。
 *
 * 用户在 Android 端还能选「跟随系统取色」（`ThemeUtils.isSystemAccent`），
 * 那在桌面端没有等价物，故桌面端枚举里不提供 `SYSTEM` 项（见 [ThemeColor.entries] 说明）。
 */
enum class ThemeColor(
    /** 与 Android 端 `Material<Name>` 对应 */
    val androidName: String,
    /** seed 色，取自 Android 端 build.gradle.kts */
    val seed: Long,
    /** 显示名（设置界面用） */
    val label: String,
) {
    Default("Default", 0xFF6750A4, "默认"),
    Red("Red", 0xFFF44336, "红"),
    Pink("Pink", 0xFFE91E63, "粉"),
    Purple("Purple", 0xFF9C27B0, "紫"),
    DeepPurple("DeepPurple", 0xFF673AB7, "深紫"),
    Indigo("Indigo", 0xFF3F51B5, "靛蓝"),
    Blue("Blue", 0xFF2196F3, "蓝"),
    LightBlue("LightBlue", 0xFF03A9F4, "浅蓝"),
    Cyan("Cyan", 0xFF00BCD4, "青"),
    Teal("Teal", 0xFF009688, "蓝绿"),
    Green("Green", 0xFF4FAF50, "绿"),
    LightGreen("LightGreen", 0xFF8BC3A4, "浅绿"),
    Lime("Lime", 0xFFCDDC39, "柠檬"),
    Yellow("Yellow", 0xFFFFEB3B, "黄"),
    Amber("Amber", 0xFFFFC107, "琥珀"),
    Orange("Orange", 0xFFFF9800, "橙"),
    DeepOrange("DeepOrange", 0xFFFF5722, "深橙"),
    Brown("Brown", 0xFF795548, "棕"),
    BlueGrey("BlueGrey", 0xFF607D8F, "蓝灰"),
    Sakura("Sakura", 0xFFFF9CA8, "樱"),
    ;

    val seedColor: Color get() = Color(seed)

    /** 生成该 seed 下的完整 M3 配色 */
    fun scheme(dark: Boolean): ColorScheme =
        if (dark) dynamicDarkColorScheme(seedColor) else dynamicLightColorScheme(seedColor)
}

/** 主题偏好（对齐 Android 端 PrefManager 的相关项） */
data class ThemeState(
    val color: ThemeColor = ThemeColor.Default,
    /**
     * 是否深色。Android 端有 `ThemeOverlay_Black`（纯黑深色）与跟随系统两档，
     * 桌面端先做「跟随系统可切换 + 手动覆盖」。
     */
    val dark: Boolean = false,
    /** 纯黑深色（Android 端 `PrefManager.blackDarkTheme` 的等价物） */
    val blackDark: Boolean = false,
)
