package com.example.c001apk.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.example.c001apk.R
import kotlin.math.roundToInt

/** 官方 ProductComposeUI 的五个 14dp 方形徽章；分数转换见 ProductGlobalData.ratingScoreToStar。 */
class ProductRatingBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val star = AppCompatResources.getDrawable(context, R.drawable.ic_search_rating_star)!!
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var stars = 0f

    var score: Float = 0f
        set(value) {
            field = value
            stars = ratingStars(value)
            contentDescription = "${value}分，${stars}星"
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val slot = 14 * resources.displayMetrics.density
        setMeasuredDimension(resolveSize((slot * 5).roundToInt(), widthMeasureSpec),
            resolveSize(slot.roundToInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slot = width / 5f
        val size = minOf(slot, height.toFloat()).roundToInt()
        repeat(5) { index ->
            val save = canvas.save()
            canvas.translate(index * slot, (height - size) / 2f)
            val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            drawBadge(canvas, size, Color.rgb(215, 215, 215), if (dark) 128 else 255)
            val fraction = (stars - index).coerceIn(0f, 1f)
            if (fraction > 0f) {
                canvas.clipRect(0f, 0f, size * fraction, size.toFloat())
                drawBadge(canvas, size, Color.rgb(217, 156, 15), 255)
            }
            canvas.restoreToCount(save)
        }
    }

    /** 官方 ComposeRatingBar：背景为 0.75 个槽位，星形为 0.44 个槽位，圆角 2dp。 */
    private fun drawBadge(canvas: Canvas, size: Int, color: Int, alpha: Int) {
        val side = size * 0.75f
        val start = (size - side) / 2f
        paint.color = color
        paint.alpha = alpha
        val radius = 2 * resources.displayMetrics.density
        canvas.drawRoundRect(start, start, start + side, start + side, radius, radius, paint)
        val starSize = (size * 0.44f).roundToInt()
        val starStart = (size - starSize) / 2
        star.setBounds(starStart, starStart, starStart + starSize, starStart + starSize)
        star.alpha = alpha
        star.draw(canvas)
    }

    companion object {
        /** 逐项照官方 lyc.m15220；评分不是直接除以二。 */
        fun ratingStars(score: Float): Float = when {
            score >= 9.2f -> 5f
            score >= 8.4f -> 4.5f
            score >= 7.6f -> 4f
            score >= 6.8f -> 3.5f
            score >= 6f -> 3f
            score >= 5f -> 2.5f
            score >= 4f -> 2f
            score >= 3f -> 1.5f
            score >= 1f -> 1f
            else -> 0f
        }
    }
}
