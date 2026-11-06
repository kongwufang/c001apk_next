package com.example.c001apk.util

import android.content.Context
import androidx.annotation.StyleRes
import com.example.c001apk.R
import com.google.android.material.color.DynamicColors
import rikka.core.util.ResourceUtils

object ThemeUtils {

    @StyleRes
    fun getNightThemeStyleRes(context: Context): Int {
        return if (PrefManager.blackDarkTheme && ResourceUtils.isNightMode(context.resources.configuration))
            R.style.ThemeOverlay_Black else R.style.ThemeOverlay
    }

    val isSystemAccent
        get() = DynamicColors.isDynamicColorAvailable() && PrefManager.followSystemAccent

    private val colorThemeMap = mapOf(
        "MATERIAL_DEFAULT" to R.style.ThemeOverlay_MaterialDefault,
        "MATERIAL_SAKURA" to R.style.ThemeOverlay_MaterialSakura,
        "MATERIAL_RED" to R.style.ThemeOverlay_MaterialRed,
        "MATERIAL_PINK" to R.style.ThemeOverlay_MaterialPink,
        "MATERIAL_PURPLE" to R.style.ThemeOverlay_MaterialPurple,
        "MATERIAL_DEEP_PURPLE" to R.style.ThemeOverlay_MaterialDeepPurple,
        "MATERIAL_INDIGO" to R.style.ThemeOverlay_MaterialIndigo,
        "MATERIAL_BLUE" to R.style.ThemeOverlay_MaterialBlue,
        "MATERIAL_LIGHT_BLUE" to R.style.ThemeOverlay_MaterialLightBlue,
        "MATERIAL_CYAN" to R.style.ThemeOverlay_MaterialCyan,
        "MATERIAL_TEAL" to R.style.ThemeOverlay_MaterialTeal,
        "MATERIAL_GREEN" to R.style.ThemeOverlay_MaterialGreen,
        "MATERIAL_LIGHT_GREEN" to R.style.ThemeOverlay_MaterialLightGreen,
        "MATERIAL_LIME" to R.style.ThemeOverlay_MaterialLime,
        "MATERIAL_YELLOW" to R.style.ThemeOverlay_MaterialYellow,
        "MATERIAL_AMBER" to R.style.ThemeOverlay_MaterialAmber,
        "MATERIAL_ORANGE" to R.style.ThemeOverlay_MaterialOrange,
        "MATERIAL_DEEP_ORANGE" to R.style.ThemeOverlay_MaterialDeepOrange,
        "MATERIAL_BROWN" to R.style.ThemeOverlay_MaterialBrown,
        "MATERIAL_BLUE_GREY" to R.style.ThemeOverlay_MaterialBlueGrey
    )

    val colorTheme get() = if (isSystemAccent) "SYSTEM" else PrefManager.themeColor
    val colorThemeStyleRes: Int
        @StyleRes get() = colorThemeMap[colorTheme] ?: R.style.ThemeOverlay_MaterialDefault

    /**
     * ★ 2026-11-06：把「选了哪套配色」变成 DynamicColors 的**内容种子**。
     *
     * 背景：rikka 的 materialthemebuilder 1.3.3 生成的 20 套 ThemeOverlay 是**旧版 M3 角色集**，
     * 只有 primary / surface / surfaceVariant 那一批 ✗ **没有** colorSurfaceContainer* ✗
     * ⇒ 选了红色，按钮文字都变红 ✓ 但**卡片底色不变** ✗（卡片取 colorSurfaceContainer）
     * ⇒ 见手机实测（2026-11-06 用户反馈）
     *
     * 做法：先落原来的强调色 overlay ✓ 再取它算出来的 colorPrimary 当种子 ✓
     * 交给 DynamicColors 生成**全套角色**（含 5 个 surfaceContainer 档）✓
     * ⇒ 强调色仍是用户选的那套 ✓ 容器色也有了 ✓
     *
     * ⚠️ 只在「不跟随系统主题色」时走这条路 ✓
     *    跟随系统时由基础主题 Theme.Material3.DynamicColors.* 自己算 ✓ 不用管
     */
    fun contentSeedOf(context: Context): Int {
        val probe = context.resources.newTheme()
        probe.applyStyle(colorThemeStyleRes, true)
        val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimary)
        val ta = probe.obtainStyledAttributes(attrs)
        val c = try {
            ta.getColor(0, 0)
        } finally {
            ta.recycle()
        }
        return c
    }
}
