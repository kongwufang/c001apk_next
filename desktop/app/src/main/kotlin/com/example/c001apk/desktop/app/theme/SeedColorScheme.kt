package com.example.c001apk.desktop.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 从 **seed 色** 生成一套完整的 Material 3 配色。
 *
 * ## 为什么不用 `dynamicLightColorScheme`
 * Android 的 `androidx.compose.material3.dynamicLightColorScheme(seed)` 依赖
 * `material-color-utilities`（HCT 色彩空间 + TonalPalette）。**Compose Multiplatform
 * 的 material3 没有这个 API** —— 实测编译报 `Unresolved reference`。
 * 这里用 HSL 明度阶自行推导，等价于 Material Theme Builder 的思路：
 * 由 seed 取色相/饱和度，再按 M3 各角色的**标准明度（tone）**取值。
 *
 * ## 与 Android 的差距（诚实说明）
 * Android 走 HCT（感知均匀），本实现走 HSL。对绝大多数 seed：
 * * 主色族（primary / onPrimary / primaryContainer）**观感接近**；
 * * 中性色族（surface / outline 等）因 HSL 在中低饱和度下的色相偏移，
 *   与 Android 端会有**可见的细微差别**（通常表现为中性色略偏色相）。
 *
 * 如需与 Android 端**逐位一致**，需要移植 `material-color-utilities` 的 HCT 实现
 * （或引入 `com.materialkolor:material-kolor` 依赖，其内部即该库的 Kotlin 移植）。
 * 这是已知的、有意接受的近似。
 *
 * ## 角色 → 明度映射（取自 Material 3 的设计规范）
 * 浅色：primary 40 / onPrimary 100 / primaryContainer 90 / onPrimaryContainer 10
 * 深色：primary 80 / onPrimary 20 / primaryContainer 30 / onPrimaryContainer 90
 * surface 浅 98 / 深 6；surfaceContainer 浅 94 / 深 12；outline 浅 50 / 深 60
 *
 * 注意：桌面端**不能**用 `android.graphics.Color`（那是 Android 类，JVM 里不存在），
 * 故此处自带 HSL⇄RGB 转换（见 [rgbToHsl] / [hslToRgb]）。
 */
object SeedColorScheme {

    /** 生成配色。[dark] 为真时走深色映射。 */
    fun generate(seed: Color, dark: Boolean): ColorScheme {
        val hsl = rgbToHsl(seed.red, seed.green, seed.blue)
        val hue = hsl[0]
        // 主色族饱和度的下限：seed 若很灰，主色也要保留一点彩度，否则与 onPrimary 对比不足
        val chroma = hsl[1].coerceAtLeast(0.18f)
        // 中性色族的饱和度：M3 的中性色带极弱色相（约 seed 饱和度的 4%~8%）
        val neutral = (hsl[1] * 0.12f).coerceAtMost(0.10f)
        val neutralVariant = (hsl[1] * 0.25f).coerceAtMost(0.18f)

        fun primary(tone: Float) = hslColor(hue, chroma, tone)
        fun tone(t: Float) = hslColor(hue, neutral, t)
        fun variant(t: Float) = hslColor(hue, neutralVariant, t)

        return if (dark) {
            darkColorScheme(
                primary = primary(80f),
                onPrimary = primary(20f),
                primaryContainer = primary(30f),
                onPrimaryContainer = primary(90f),
                inversePrimary = primary(40f),

                secondary = variant(80f),
                onSecondary = variant(20f),
                secondaryContainer = variant(30f),
                onSecondaryContainer = variant(90f),

                tertiary = primary(80f),
                onTertiary = primary(20f),
                tertiaryContainer = primary(30f),
                onTertiaryContainer = primary(90f),

                background = tone(6f),
                onBackground = tone(90f),
                surface = tone(6f),
                onSurface = tone(90f),
                surfaceVariant = variant(30f),
                onSurfaceVariant = variant(80f),
                surfaceTint = primary(80f),
                inverseSurface = tone(90f),
                inverseOnSurface = tone(20f),

                surfaceContainerLowest = tone(4f),
                surfaceContainerLow = tone(10f),
                surfaceContainer = tone(12f),
                surfaceContainerHigh = tone(17f),
                surfaceContainerHighest = tone(22f),

                outline = variant(60f),
                outlineVariant = variant(30f),

                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),

                scrim = Color(0xFF000000),
            )
        } else {
            lightColorScheme(
                primary = primary(40f),
                onPrimary = Color.White,
                primaryContainer = primary(90f),
                onPrimaryContainer = primary(10f),
                inversePrimary = primary(80f),

                secondary = variant(40f),
                onSecondary = Color.White,
                secondaryContainer = variant(90f),
                onSecondaryContainer = variant(10f),

                tertiary = primary(40f),
                onTertiary = Color.White,
                tertiaryContainer = primary(90f),
                onTertiaryContainer = primary(10f),

                background = tone(98f),
                onBackground = tone(10f),
                surface = tone(98f),
                onSurface = tone(10f),
                surfaceVariant = variant(90f),
                onSurfaceVariant = variant(30f),
                surfaceTint = primary(40f),
                inverseSurface = tone(20f),
                inverseOnSurface = tone(95f),

                surfaceContainerLowest = Color.White,
                surfaceContainerLow = tone(96f),
                surfaceContainer = tone(94f),
                surfaceContainerHigh = tone(92f),
                surfaceContainerHighest = tone(90f),

                outline = variant(50f),
                outlineVariant = variant(80f),

                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),

                scrim = Color(0xFF000000),
            )
        }
    }

    /** HSL → Color。`tone` 为 M3 的明度值（0..100），直接当作 HSL 的 lightness。 */
    private fun hslColor(hue: Float, sat: Float, tone: Float): Color =
        hslToRgb(hue, sat.coerceIn(0f, 1f), (tone / 100f).coerceIn(0f, 1f))

    // ── HSL ⇄ RGB（自包含，不依赖 android.graphics）──

    /** 返回 [hue(0..360), saturation(0..1), lightness(0..1)] */
    private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return floatArrayOf(0f, 0f, l)

        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + if (g < b) 6f else 0f)
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return floatArrayOf(h, s, l)
    }

    private fun hslToRgb(hue: Float, s: Float, l: Float): Color {
        if (s == 0f) return Color(l, l, l)

        val h = ((hue % 360f) + 360f) % 360f / 360f
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        fun hue2rgb(t0: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        return Color(hue2rgb(h + 1f / 3f), hue2rgb(h), hue2rgb(h - 1f / 3f))
    }
}
