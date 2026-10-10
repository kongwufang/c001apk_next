package com.example.c001apk.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.min

/**
 * 仿官方的「电池形刻度条」，照官方 Compose 组件 `BatterySlider` 重画
 * （反编译记录 `_rev/jadx_bs/.../BatterySliderComposeKt.java`，尺寸表固化在 `_rev/SUBTAB_CARDS_SPEC.md`）。
 *
 * 形状：圆角矩形电池体 + 右侧小凸起端子；内部按 [progress] 填一段**水平渐变**（起点 50% 透明、
 * 终点不透明），末端一个白色手柄；再叠 4 个等分刻度与时间标签（官方量程固定 2~17 小时，
 * 标签固定 2h / 7h / 12h / 17h）。
 *
 * 填充色随进度插值，跟官方同一套色标：低电量红 → 中段橙 → 高电量绿（相邻档位间线性插值）。
 * 电池体的「未填充」部分取卡片同色，所以看上去是空的，只有描边可见 —— 跟官方一致。
 */
class BatterySliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0..1。官方算法：`(平均亮屏小时 - 2) / 15` */
    var progress: Float = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v != field) {
                field = v
                invalidate()
            }
        }

    /**
     * 按「平均亮屏多少小时」直接设，量程用官方那套 2~17h —— 调用方不必自己知道这个区间，
     * 免得量程在两边各写一份、哪天改了只改一处。
     */
    fun setHours(hours: Float) {
        progress = (hours - MIN_HOURS) / (MAX_HOURS - MIN_HOURS)
    }

    private val density = resources.displayMetrics.density

    private fun dp(v: Float) = v * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(10f)
    }
    private val rect = RectF()
    private val path = Path()

    /** 电池体底色：卡片同色（官方取 contentBackgroundColor），未填充部分要与卡片融为一体 */
    private val bodyColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer)

    /** 描边 / 刻度 / 标签：官方这三处都取 textColorTertiary */
    private val lineColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val corner = dp(CORNER_RADIUS)
        // 官方参数：电池体 171dp、视角宽 174dp → 右侧 2dp 端子 + 1dp 空隙
        val terminalW = dp(2f)
        val bodyW = w - terminalW - dp(1f)
        val bodyH = h
        val terminalH = h * TERMINAL_HEIGHT_RATIO
        val terminalTop = (h - terminalH) / 2f

        // 电池体（底 + 描边）
        path.reset()
        rect.set(0f, 0f, bodyW, bodyH)
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        fillPaint.shader = null
        fillPaint.color = bodyColor
        canvas.drawPath(path, fillPaint)
        strokePaint.color = lineColor
        strokePaint.strokeWidth = dp(1f)
        canvas.drawPath(path, strokePaint)

        // 右侧端子
        val terminalR = min(terminalH * 0.4f, corner)
        rect.set(bodyW, terminalTop, bodyW + terminalW, terminalTop + terminalH)
        path.reset()
        path.addRoundRect(rect, terminalR, terminalR, Path.Direction.CW)
        fillPaint.color = lineColor
        canvas.drawPath(path, fillPaint)

        // 进度填充
        val inset = dp(1f)
        val innerW = bodyW - inset * 2f
        val innerH = bodyH - inset * 2f
        val fillW = innerW * progress
        if (fillW > 0f) {
            val color = progressColor(progress)
            fillPaint.shader = LinearGradient(
                inset, 0f, bodyW - inset, 0f,
                withAlpha(color, 0.5f), color, Shader.TileMode.CLAMP
            )
            val r = max(corner - inset, 0f)
            rect.set(inset, inset, inset + max(fillW, r * 2f), inset + innerH)
            path.reset()
            path.addRoundRect(rect, r, r, Path.Direction.CW)
            canvas.save()
            canvas.clipRect(inset, inset, inset + fillW, inset + innerH)
            canvas.drawPath(path, fillPaint)
            canvas.restore()
            fillPaint.shader = null

            // 末端白色手柄（官方 5dp 宽、高 16dp、alpha 0.8，太窄时不画）
            val handleW = dp(5f)
            val handleH = min(dp(16f), innerH)
            if (fillW >= dp(4f) * 2f + handleW) {
                fillPaint.color = withAlpha(Color.WHITE, 0.8f)
                rect.set(
                    inset + fillW - handleW - dp(4f),
                    inset + (innerH - handleH) / 2f,
                    inset + fillW - dp(4f),
                    inset + (innerH - handleH) / 2f + handleH
                )
                path.reset()
                path.addRoundRect(rect, dp(2.5f), dp(2.5f), Path.Direction.CW)
                canvas.drawPath(path, fillPaint)
            }
        }

        // 刻度线 + 时间标签：x 按「两端各留 1.5% 边距」在电池体内部等分
        textPaint.color = lineColor
        fillPaint.color = lineColor
        val tickPadding = dp(1f)
        val x0 = inset + tickPadding
        val span = (bodyW - inset * 2f) - tickPadding * 2f
        val tickH = dp(TICK_HEIGHT)
        val tickW = dp(TICK_WIDTH)
        val tickCy = bodyH / 2f + dp(TICK_OFFSET)
        val labelCy = tickCy + tickH / 2f + dp(LABEL_OFFSET) + textPaint.textSize / 2f
        val range = MAX_HOURS - MIN_HOURS
        for (i in TICKS.indices) {
            val ratio = EDGE + (TICKS[i] - MIN_HOURS) / range * (1f - 2f * EDGE)
            val cx = x0 + ratio * span
            rect.set(cx - tickW / 2f, tickCy - tickH / 2f, cx + tickW / 2f, tickCy + tickH / 2f)
            path.reset()
            path.addRoundRect(rect, tickW / 2f, tickW / 2f, Path.Direction.CW)
            canvas.drawPath(path, fillPaint)
            canvas.drawText(LABELS[i], cx, labelCy, textPaint)
        }
    }

    /** 官方色标：0 红 → 0.2 半透明红 → 0.4 半透明橙 → 0.6 橙 → 0.8 半透明绿 → 1 绿 */
    private fun progressColor(p: Float): Int {
        val v = p.coerceIn(0f, 1f)
        var i = 0
        while (i < STOPS.size - 2 && v > STOPS[i + 1]) i++
        val span = STOPS[i + 1] - STOPS[i]
        val t = if (span <= 0f) 0f else (v - STOPS[i]) / span
        return lerpColor(STOP_COLORS[i], STOP_COLORS[i + 1], t)
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int = Color.argb(
        (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * t).toInt(),
        (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt(),
        (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt(),
        (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
    )

    private fun withAlpha(color: Int, factor: Float): Int = Color.argb(
        (Color.alpha(color) * factor).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    companion object {
        /** 官方量程：平均亮屏 2h ~ 17h */
        private const val MIN_HOURS = 2f
        private const val MAX_HOURS = 17f

        /** 首尾刻度往内缩 1.5%，免得刻度线贴着电池边 */
        private const val EDGE = 0.015f

        private const val CORNER_RADIUS = 6f
        private const val TERMINAL_HEIGHT_RATIO = 0.33333334f
        private const val TICK_HEIGHT = 4f
        private const val TICK_WIDTH = 1f
        private const val TICK_OFFSET = -7f
        private const val LABEL_OFFSET = 4f

        /** 官方刻度值（2、6.95、12.05、17）与取整后的标签 */
        private val TICKS = floatArrayOf(2f, 6.95f, 12.05f, 17f)
        private val LABELS = arrayOf("2h", "7h", "12h", "17h")

        private val STOPS = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        private val STOP_COLORS = intArrayOf(
            0xFFF44336.toInt(), 0x80F44336.toInt(),
            0x80FF9800.toInt(), 0xFFFF9800.toInt(),
            0x804CAF50.toInt(), 0xFF4CAF50.toInt()
        )
    }
}
