package com.example.c001apk.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Html
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.example.c001apk.view.CenteredImageSpan
import com.example.c001apk.view.MyURLSpan
import com.google.android.material.color.MaterialColors
import io.noties.markwon.core.spans.CodeBlockSpan
import java.util.regex.Pattern

object SpannableStringBuilderUtil {

    fun setText(
        mContext: Context,
        text: String,
        size: Float,
        imgList: List<String>?,
        /**
         * 正文**可用宽度**（px）。`> 0` 时 markdown 表格由 [MdTableSpan] 接管 ✓
         * （按内容分配列宽；`<= 0` 保持原样交给 Markwon 的 TablePlugin ✓）
         */
        availWidth: Int = 0,
        // 注意：新参数一律加在这两个之前。showMoreReply 必须留在最后，
        // FeedReplyAdapter 是用尾随 lambda 调它的（尾随 lambda 只会绑到最后一个参数上）。
        linkAsChip: Boolean = false,
        showMoreReply: (() -> Unit)? = null
    ): SpannableStringBuilder {
        // 代码块复制按钮的占位符可能在文本被二次渲染时残留，先剔除
        val src = text.replace(PLACEHOLDER, "")
        val mess: Spanned =
            if (MarkdownUtils.isMarkdown(src))
                MarkdownUtils.parse(mContext, src, availWidth)
            else
                Html.fromHtml(
                    src.replace("\n", "<br/>"),
                    Html.FROM_HTML_MODE_COMPACT
                )
        val builder = SpannableStringBuilder(mess)
        val urls = builder.getSpans(
            0, mess.length,
            URLSpan::class.java
        )
        urls.forEach {
            val url = MarkdownUtils.cleanUrl(it.url)
            val start = builder.getSpanStart(it)
            var end = builder.getSpanEnd(it)
            // 服务端会把 "…(url)" 整段识别成链接（尾随标点被吞），
            // 显示文本等于 URL 时按清洗后的长度收缩 span，避免标点被染成链接
            if (builder.subSequence(start, end).toString() == it.url)
                end = start + url.length
            val flags = builder.getSpanFlags(it)
            builder.setSpan(MyURLSpan(mContext, url, imgList, showMoreReply), start, end, flags)
            builder.removeSpan(it)
        }
        // 私信气泡里的「查看链接」要一眼能认出来（见 layout 里的 app:linkAsChip）
        if (linkAsChip) addLinkChips(mContext, builder)
        if (PrefManager.showEmoji) {
            val pattern = Pattern.compile("\\[[^\\]]+\\]")
            val matcher = pattern.matcher(builder)
            while (matcher.find()) {
                val group = matcher.group()
                EmojiUtils.emojiMap[group]?.let {
                    mContext.getDrawable(it)?.let { emoji ->
                        if (group in listOf("[楼主]", "[层主]", "[置顶]"))
                            emoji.setBounds(0, 0, (size * 2).toInt(), size.toInt())
                        else
                            emoji.setBounds(0, 0, (size * 1.3).toInt(), (size * 1.3).toInt())
                        val imageSpan = CenteredImageSpan(emoji, (size * 1.3).toInt(), group)
                        builder.setSpan(
                            imageSpan,
                            matcher.start(),
                            matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                }
            }
        }
        addCodeCopyButtons(mContext, builder)
        return builder
    }

    // ---------------- 代码块复制按钮 ----------------

    /**
     * 在每个 markdown 代码块内部最前插入一行「复制」按钮（右对齐），
     * 按钮显示在代码块背景内的右上角，点击把代码内容写入剪贴板。
     */
    private fun addCodeCopyButtons(
        context: Context,
        builder: SpannableStringBuilder
    ) {
        val blocks = builder.getSpans(
            0, builder.length,
            CodeBlockSpan::class.java
        )
        if (blocks.isEmpty()) return
        blocks.map { builder.getSpanStart(it) to builder.getSpanEnd(it) }
            .filter { it.first >= 0 && it.second > it.first }
            .sortedByDescending { it.first } // 从后往前插入，避免下标偏移
            .forEach { (start, end) ->
                val code = builder.subSequence(start, end).toString().trim('\n')
                // 跳过代码块前导换行，把按钮行放进 CodeBlockSpan 内部（背景内）
                var bodyStart = start
                while (bodyStart < end && builder[bodyStart] == '\n') bodyStart++
                builder.insert(bodyStart, PLACEHOLDER + "\n")
                builder.setSpan(
                    CopyCodeButtonSpan(),
                    bodyStart, bodyStart + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager ?: return
                            cm.setPrimaryClip(ClipData.newPlainText("code", code))
                            Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            // 保持原样式，不做任何装饰
                        }
                    },
                    bodyStart, bodyStart + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // 按钮行整行右对齐 → 显示在右上角
                // 注：Layout.Alignment.ALIGN_RIGHT 是隐藏 API，LTR 下右对齐用 ALIGN_OPPOSITE
                builder.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                    bodyStart, bodyStart + 2,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
    }

    private const val PLACEHOLDER = "\uFFFC"

    /** 绘制成一个小圆角「复制」按钮，颜色跟随正文颜色 */
    private class CopyCodeButtonSpan : ReplacementSpan() {

        private val label = "复制"

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val p = Paint(paint)
            p.textSize = paint.textSize * 0.72f
            return (p.measureText(label) + p.textSize).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val p = Paint(paint)
            p.textSize = paint.textSize * 0.72f
            val width = p.measureText(label) + p.textSize
            val height = p.textSize * 1.7f
            val centerY = (top + bottom) / 2f
            val rect = RectF(x, centerY - height / 2f, x + width, centerY + height / 2f)
            val radius = height / 2f
            val textColor = paint.color

            p.style = Paint.Style.FILL
            p.color = ColorUtils.setAlphaComponent(textColor, 30)
            canvas.drawRoundRect(rect, radius, radius, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            p.color = ColorUtils.setAlphaComponent(textColor, 120)
            canvas.drawRoundRect(rect, radius, radius, p)

            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = ColorUtils.setAlphaComponent(textColor, 190)
            canvas.drawText(
                label,
                x + width / 2f,
                centerY - (p.descent() + p.ascent()) / 2f,
                p
            )
        }
    }

    // ---------------- 「查看链接」胶囊按钮 ----------------

    /** 服务端把纯文本消息里的链接下发成 `<a class="feed-link-url">查看链接</a>` */
    private const val LINK_CHIP_LABEL = "查看链接"

    /**
     * 给正文里的「查看链接」套一层胶囊底色。
     * 这个锚点是服务端生成的可点元素，但降级成纯文本后跟正文一模一样，
     * 完全看不出它能点，所以这里把它画成一颗按钮（点击仍由同区间的 MyURLSpan 负责）。
     */
    private fun addLinkChips(context: Context, builder: SpannableStringBuilder) {
        builder.getSpans(0, builder.length, MyURLSpan::class.java)
            .forEach { span ->
                val start = builder.getSpanStart(span)
                val end = builder.getSpanEnd(span)
                if (start < 0 || end <= start) return@forEach
                if (builder.subSequence(start, end).toString() != LINK_CHIP_LABEL) return@forEach
                builder.setSpan(
                    LinkChipSpan(context),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
    }

    /** 画成一颗主题色胶囊，颜色取主题 colorPrimary，深浅色主题都跟着走 */
    private class LinkChipSpan(private val context: Context) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val label = text.subSequence(start, end).toString()
            return (paint.measureText(label) + dp(9f) * 2).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val label = text.subSequence(start, end).toString()
            val p = Paint(paint)
            val accent = MaterialColors.getColor(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                p.color
            )
            val padding = dp(9f)
            val width = p.measureText(label) + padding * 2
            val height = p.textSize * 1.55f
            val centerY = (top + bottom) / 2f
            val rect = RectF(x, centerY - height / 2f, x + width, centerY + height / 2f)
            val radius = height / 2f

            p.style = Paint.Style.FILL
            p.color = ColorUtils.setAlphaComponent(accent, 38)
            canvas.drawRoundRect(rect, radius, radius, p)

            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1f)
            p.color = ColorUtils.setAlphaComponent(accent, 130)
            canvas.drawRoundRect(rect, radius, radius, p)

            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.LEFT
            p.color = accent
            canvas.drawText(label, x + padding, centerY - (p.descent() + p.ascent()) / 2f, p)
        }

        private fun dp(v: Float) = v * context.resources.displayMetrics.density
    }

}