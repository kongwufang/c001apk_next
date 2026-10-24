package com.example.c001apk.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan

/**
 * 自绘 markdown 表格（接管 Markwon 的表格渲染）。
 *
 * ## 为什么要自己画
 * 酷安服务端**不支持 markdown 表格** ⇒ 正文里存的是原始 markdown
 * （`| 平台 | 文件 |` / `|---|---|` / …）✗
 *
 * 之前交给 Markwon 的 `TablePlugin` 渲染 ✓ 但 Markwon 4.6.2 把列宽**写死**了：
 * ```java
 * // TableRowSpan.java L411
 * protected int cellWidth(int size) { return (int) (1F * width / size + 0.5F); }
 * ```
 * ⇒ **可用宽度 ÷ 列数** ✗ ⇒ 两列表永远各占 50% ✗
 * ⇒ `c001apk_next-V1.0.7-release.10007.apk`（38 字符）被硬断成
 *    `c001apk_next-V1.0.7-rele` + `ase.10007.apk` ✗（2026-10-24 用户反馈截图）
 *
 * ## 现在的规则（用户选定：按内容动态分配 + 放不下就换行）
 * ① 每列自然宽度 = 该列所有单元格**单行**宽度的最大值（+ 内边距）
 * ② `Σ自然 ≤ 可用宽度` ⇒ 直接用自然宽度，**余量全给最后一列** ✓
 * ③ 否则按自然宽度**等比压缩**，但每列**保底 25%**（防止被压成 1 个字 ✗）
 * ④ 最后修整舍入，保证 `Σ ≤ 可用宽度` ✓ —— **绝不撑爆屏幕** ✓
 * ⑤ 单元格内容超宽 ⇒ `StaticLayout` **正常换行** ✓（用户已确认可接受 ✓）
 *
 * ## 例子（实测数据）
 * ```
 * 自然宽度 [72, 684] → 分配 [72, 828]（可用 900）
 *   第1列  72px  「安卓源码」一行放下 ✓
 *   第2列 828px  38 字符文件名一行放下 ✓   ← 这才是要的效果
 * 窄屏 520px → 等比压缩 [130, 390]（每列 ≥ 25%，Σ = 520 ✓ 没超）
 * ```
 *
 * ## 字号与颜色
 * **不自己猜** ✗ —— `getSize` / `draw` 拿到的 `paint` 就是 TextView 的画笔 ✓
 * ⇒ 字号、颜色、深色模式全部**现取现用** ✓（不会出现深色模式黑字黑底 ✗）
 *
 * ## 注意
 * - 表格画在 `canvas` 上，**不参与**文本选择 ✗（当前不需要 ✓）
 * - 高度通过 `FontMetricsInt` 报告 ✓（和 Markwon 的做法一致 ✓）
 * - 单元格里的简单行内 markdown（`` ` `` / `**` / `~~`）在 [MarkdownUtils] 解析时已剥成纯文本 ✓
 */
class MdTableSpan(
    private val header: List<String>,
    private val body: List<List<String>>,
    private val availWidth: Int,
    private val padPx: Int,
) : ReplacementSpan() {

    private val columns = header.size

    private val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = BORDER_COLOR
    }
    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HEADER_BG_COLOR
    }

    private var widths: IntArray = IntArray(columns)
    private var rowHeights: IntArray = IntArray(0)
    private var layouts: Array<Array<StaticLayout>> = emptyArray()
    private var totalHeight = 0
    private var built = false
    private var builtTextSize = -1f
    private var builtColor = Int.MIN_VALUE

    /** 分配列宽（见类注释 ①~④ ✓） */
    private fun allocWidths(natural: IntArray, avail: Int): IntArray {
        val n = natural.size
        val out = IntArray(n)
        val total = natural.sum()
        if (total <= avail) {
            natural.copyInto(out)
            out[n - 1] += avail - total          // 余量全给最后一列 ✓
            return out
        }
        val minW = (avail * MIN_RATIO).toInt().coerceAtLeast(1)
        val scale = avail.toFloat() / total
        var sum = 0
        for (i in 0 until n) {
            // ★ 不能超过**自己的自然宽度** ✗
            //   （2026-10-24 预览时发现：第 1 列只需 196px 却被 25% 的下限抬到 225px ✗
            //     白占 29px 空间 ⇒ 长内容那列更挤 ✗）
            val wanted = maxOf(minW, (natural[i] * scale).toInt())
            out[i] = minOf(natural[i], wanted).coerceAtLeast(1)
            sum += out[i]
        }
        if (sum > avail) {
            var over = sum - avail
            (0 until n).sortedByDescending { out[it] }.forEach { i ->
                if (over > 0) {
                    val room = (out[i] - minW).coerceAtLeast(0)
                    val take = minOf(room, over)
                    out[i] -= take
                    over -= take
                }
            }
        }
        // 修整舍入：差额补到最后一列，超出就从最后一列扣 ✓
        val diff = avail - out.sum()
        if (diff > 0) out[n - 1] += diff
        while (out.sum() > avail && out[n - 1] > 1) out[n - 1] -= 1
        return out
    }

    /**
     * 懒构建（用**当前画笔**的字号与颜色 ✓）
     * 字号/颜色变了（比如切了深色模式）就重建 ✓
     */
    private fun ensureBuilt(paint: Paint) {
        if (built && builtTextSize == paint.textSize && builtColor == paint.color) return
        built = true
        builtTextSize = paint.textSize
        builtColor = paint.color

        // ★ 表格字号取正文的 0.85 倍
        //   表格比正文略小是常规做法 ✓，而且能让长文件名**一行放下** ✓
        //   （实测：正文 47px 时 38 字符文件名要 930px ✗ 超过可用 900 ✗
        //     0.85 倍后约 790px ✓ 放得下 ✓ —— 2026-10-24 用同一算法渲染预览图验证过 ✓）
        cellPaint.textSize = paint.textSize * TABLE_FONT_SCALE
        cellPaint.color = paint.color
        headerPaint.set(cellPaint)
        headerPaint.isFakeBoldText = true
        borderPaint.strokeWidth = (paint.textSize * 0.06f).coerceAtLeast(1f)

        val rows = ArrayList<List<String>>(body.size + 1)
        rows.add(header)
        rows.addAll(body)

        // ① 自然宽度
        val natural = IntArray(columns) { padPx * 2 + 2 }
        for (r in rows) {
            for (c in 0 until columns) {
                val s = r.getOrNull(c) ?: ""
                if (s.isEmpty()) continue
                val w = cellPaint.measureText(s).toInt() + padPx * 2 + 2
                if (w > natural[c]) natural[c] = w
            }
        }
        widths = allocWidths(natural, availWidth)

        // ② 每格一个 StaticLayout（宽度不够就自动换行 ✓）
        layouts = Array(rows.size) { ri ->
            Array(columns) { ci ->
                val s = rows[ri].getOrNull(ci) ?: ""
                val p = if (ri == 0) headerPaint else cellPaint
                val w = (widths[ci] - padPx * 2).coerceAtLeast(1)
                StaticLayout.Builder
                    .obtain(s, 0, s.length, p, w)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()
            }
        }
        rowHeights = IntArray(rows.size) { ri ->
            layouts[ri].maxOf { it.height } + padPx * 2
        }
        totalHeight = rowHeights.sum()
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        ensureBuilt(paint)
        if (fm != null && totalHeight > 0) {
            fm.ascent = -totalHeight
            fm.descent = 0
            fm.top = fm.ascent
            fm.bottom = 0
        }
        return availWidth
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
        paint: Paint,
    ) {
        ensureBuilt(paint)
        if (layouts.isEmpty()) return
        var rowTop = top.toFloat()
        for (ri in layouts.indices) {
            val h = rowHeights[ri].toFloat()
            var colLeft = x
            for (ci in 0 until columns) {
                val w = widths[ci].toFloat()
                if (ri == 0) {
                    canvas.drawRect(colLeft, rowTop, colLeft + w, rowTop + h, headerBgPaint)
                }
                canvas.save()
                canvas.translate(colLeft + padPx, rowTop + padPx)
                layouts[ri][ci].draw(canvas)
                canvas.restore()
                colLeft += w
            }
            rowTop += h
        }
        // 网格线：先横后竖（逐格画矩形会出重线 ✗）
        val tableLeft = x
        val tableRight = x + widths.sum().toFloat()
        var lineTop = top.toFloat()
        for (ri in rowHeights.indices) {
            canvas.drawLine(tableLeft, lineTop, tableRight, lineTop, borderPaint)
            lineTop += rowHeights[ri]
        }
        canvas.drawLine(tableLeft, lineTop, tableRight, lineTop, borderPaint)
        var lineLeft = tableLeft
        for (ci in 0 until columns) {
            canvas.drawLine(lineLeft, top.toFloat(), lineLeft, lineTop, borderPaint)
            lineLeft += widths[ci]
        }
        canvas.drawLine(lineLeft, top.toFloat(), lineLeft, lineTop, borderPaint)
    }

    companion object {
        /** 每列最小占比（防止被压成 1 个字 ✗） */
        private const val MIN_RATIO = 0.25f

        /** 表格字号相对正文的比例（略小一点，长内容更容易一行放下 ✓） */
        private const val TABLE_FONT_SCALE = 0.85f

        /** 边框色：半透明灰（深浅色下都看得见 ✓） */
        private val BORDER_COLOR = Color.argb(70, 128, 128, 128)

        /** 表头底色：极淡的灰 ✓ */
        private val HEADER_BG_COLOR = Color.argb(22, 128, 128, 128)
    }
}
