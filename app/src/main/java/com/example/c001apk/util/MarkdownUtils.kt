package com.example.c001apk.util

import android.content.Context
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextUtils
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.image.glide.GlideImagesPlugin
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 酷安服务端不支持 Markdown，此处在客户端自行渲染：
 * 自动检测正文中的 md 特征（哪怕是插在普通文本中间的片段），
 * 命中则走 Markwon 渲染，否则保持原有 Html.fromHtml 逻辑。
 */
object MarkdownUtils {

    // ---------------- 自动检测 ----------------

    // 酷安话题 #[xxx]#，先剔除避免干扰标题/链接判断
    private val topicPattern = Pattern.compile("#\\[[^\\]\\n]*\\]#")

    // 强特征：任一命中即按 md 渲染
    private val fencePattern = Pattern.compile("(?m)^`{3,}")
    private val headingPattern = Pattern.compile("(?m)^#{1,6}\\s+\\S")
    private val inlineCodePattern = Pattern.compile("`[^`\\n]+`")
    private val strikePattern = Pattern.compile("~~[^~\\n]+~~")
    private val linkPattern = Pattern.compile("\\[[^\\]\\n]*\\]\\(\\s*[^\\s)]+\\s*\\)")
    private val imagePattern = Pattern.compile("!\\[[^\\]\\n]*\\]\\(\\s*[^\\s)]+\\s*\\)")
    private val boldPattern = Pattern.compile("(\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__)")

    // 服务端已把 md 链接的 URL 换成 <a> 标签: [文字](<a href="...">查看链接</a>)
    private val serverLinkPattern = Pattern.compile("\\][ \\t]*\\(\\s*<a\\b", Pattern.CASE_INSENSITIVE)

    // 弱特征：累计 2 个以上才认为是 md，降低误报
    private val bulletPattern = Pattern.compile("(?m)^[-*+]\\s+\\S")
    private val orderedPattern = Pattern.compile("(?m)^\\d{1,3}\\.\\s+\\S")
    private val quotePattern = Pattern.compile("(?m)^>\\s?\\S")
    private val hrPattern = Pattern.compile("(?m)^ {0,3}(-{3,}|\\*{3,}|_{3,})\\s*$")
    private val tableRowPattern = Pattern.compile("(?m)^\\|.+\\|\\s*$")

    fun isMarkdown(text: String?): Boolean {
        if (text.isNullOrBlank() || text.length < 4) return false
        val t = topicPattern.matcher(text).replaceAll("")
        if (fencePattern.matcher(t).find() ||
            headingPattern.matcher(t).find() ||
            inlineCodePattern.matcher(t).find() ||
            strikePattern.matcher(t).find() ||
            linkPattern.matcher(t).find() ||
            imagePattern.matcher(t).find() ||
            serverLinkPattern.matcher(t).find() ||
            boldPattern.matcher(t).find()
        ) return true
        var score = 0
        if (count(bulletPattern, t) >= 2) score++
        if (orderedPattern.matcher(t).find()) score++
        if (quotePattern.matcher(t).find()) score++
        if (hrPattern.matcher(t).find()) score++
        if (count(tableRowPattern, t) >= 2) score++
        return score >= 2
    }

    private fun count(p: Pattern, text: String): Int {
        val m = p.matcher(text)
        var c = 0
        while (m.find()) c++
        return c
    }

    // ---------------- HTML 预处理 ----------------

    private val brPattern = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE)
    private val aTagPattern = Pattern.compile(
        "<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    // 服务端把 md 链接的 URL 替换成 <a>查看链接</a> 后，与外层 md 语法形成嵌套
    // [外层文字]([查看链接](url))，降级还原为 [外层文字](url)
    private val nestedLinkPattern = Pattern.compile(
        "\\[([^\\]\\n]*)\\]\\(\\[[^\\]\\n]*\\]\\(\\s*([^)\\s]+)\\s*\\)\\)?"
    )

    /**
     * 服务端 URL 自动识别会把正文标点吞进 URL（如 "(url)" 被识别成 "url)"，
     * 去掉未配对的右括号及尾部句读标点。
     */
    fun cleanUrl(url: String): String {
        var s = url.trim()
        val open = s.count { it == '(' }
        val close = s.count { it == ')' }
        if (close > open) {
            var toDrop = close - open
            val sb = StringBuilder(s.length)
            for (i in s.indices.reversed()) {
                val c = s[i]
                if (c == ')' && toDrop > 0) {
                    toDrop--
                    continue
                }
                sb.append(c)
            }
            s = sb.reverse().toString()
        }
        while (s.isNotEmpty() && s.last() in ".,;:!?") s = s.dropLast(1)
        return s
    }

    private fun preprocess(text: String): String {
        var t = brPattern.matcher(text).replaceAll("\n")

        // <a href="url">label</a> → [label](url)
        val m = aTagPattern.matcher(t)
        if (m.find()) {
            val sb = StringBuffer()
            do {
                val rawUrl = m.group(1)?.trim().orEmpty()
                val url = cleanUrl(rawUrl)
                val rawLabel = m.group(2).orEmpty()
                // 服务端用 URL 本身当链接文字时，同样去掉被吞进去的尾随标点
                val label = (if (rawLabel.trim() == rawUrl) url else rawLabel)
                    .replace("\\", "\\\\")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                val md = if (url.isEmpty()) label
                else "[$label](${
                    url.replace(" ", "%20")
                        .replace("(", "%28")
                        .replace(")", "%29")
                })"
                m.appendReplacement(sb, Matcher.quoteReplacement(md))
            } while (m.find())
            m.appendTail(sb)
            t = sb.toString()
        }

        // 常见行内 HTML 标签 → markdown 等价语法
        t = t.replace(Regex("(?i)</?(b|strong)>"), "**")
        t = t.replace(Regex("(?i)</?(i|em)>"), "*")
        t = t.replace(Regex("(?i)</?(s|strike|del)>"), "~~")

        // 服务端 <a> 与外层 md 语法嵌套 → 还原成作者写的 [文字](url)
        val nm = nestedLinkPattern.matcher(t)
        if (nm.find()) {
            val sb = StringBuffer()
            do {
                nm.appendReplacement(
                    sb,
                    Matcher.quoteReplacement(
                        "[${nm.group(1)}](${cleanUrl(nm.group(2).orEmpty())})"
                    )
                )
            } while (nm.find())
            nm.appendTail(sb)
            t = sb.toString()
        }

        // HTML 实体解码：服务端把 " > < & 等转义了，
        // 不还原会导致引用块（&gt;）、代码块内容显示错乱
        t = decodeEntities(t)
        return t
    }

    // ---------------- HTML 实体 ----------------

    private val entityPattern = Pattern.compile(
        "&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,9});"
    )

    private val namedEntities = mapOf(
        "quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">", "apos" to "'",
        "nbsp" to "\u00A0", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "hellip" to "\u2026",
        "mdash" to "\u2014", "ndash" to "\u2013", "middot" to "\u00B7",
        "times" to "\u00D7", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "euro" to "\u20AC", "pound" to "\u00A3", "yen" to "\u00A5",
        "sect" to "\u00A7", "para" to "\u00B6", "deg" to "\u00B0",
        "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
        "bull" to "\u2022", "dagger" to "\u2020"
    )

    fun decodeEntities(text: String): String {
        if (text.indexOf('&') < 0) return text
        val m = entityPattern.matcher(text)
        if (!m.find()) return text
        val sb = StringBuffer()
        do {
            val name = m.group(1).orEmpty()
            val replacement = if (name.startsWith("#"))
                decodeNumericEntity(name) ?: m.group()
            else
                namedEntities[name.lowercase()] ?: m.group()
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        } while (m.find())
        m.appendTail(sb)
        return sb.toString()
    }

    private fun decodeNumericEntity(name: String): String? = try {
        val codePoint = if (name.length > 1 && (name[1] == 'x' || name[1] == 'X'))
            name.substring(2).toInt(16)
        else
            name.substring(1).toInt()
        if (codePoint in 1..0x10FFFF) String(Character.toChars(codePoint)) else null
    } catch (e: NumberFormatException) {
        null
    }

    // ---------------- 渲染 ----------------

    @Volatile
    private var markwon: Markwon? = null

    private fun getMarkwon(context: Context): Markwon =
        markwon ?: synchronized(this) {
            markwon ?: build(context.applicationContext).also { markwon = it }
        }

    private fun build(context: Context): Markwon =
        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(tableTheme(context)))
            .usePlugin(GlideImagesPlugin.create(context))
            .build()

    /** 默认 cellPadding 偏小，单元格文字容易贴边，这里放宽一点 */
    private fun tableTheme(context: Context): TableTheme {
        val padding = (context.resources.displayMetrics.density * 8F).toInt()
        return TableTheme.buildWithDefaults(context)
            .tableCellPadding(padding)
            .build()
    }

    /**
     * 解析 md 文本为 Spanned。
     * Markwon 的 LinkSpan 继承自 URLSpan，会被 SpannableStringBuilderUtil
     * 统一替换为 MyURLSpan，点击行为与普通正文链接一致。
     */
    /**
     * 解析 md 文本为 Spanned。
     *
     * @param availWidth 正文**可用宽度**（px）。
     *   `> 0` 时由 [MdTableSpan] **接管表格渲染** ✓（按内容分配列宽）
     *   `<= 0` 时表格仍交给 Markwon 的 TablePlugin ✓（其它页面行为不变 ✓）
     */
    fun parse(
        context: Context,
        text: String,
        availWidth: Int = 0,
    ): Spanned {
        val tables = ArrayList<MdTable>()
        var md = text
        if (availWidth > 0) md = extractTables(md, tables)
        val spanned = getMarkwon(context).toMarkdown(preprocess(md))
        if (tables.isEmpty() || availWidth <= 0) return spanned
        val pad = (context.resources.displayMetrics.density * 6f).toInt().coerceAtLeast(4)
        return placeTables(spanned, tables, availWidth, pad)
    }

    // ---------------- 表格接管 ----------------

    /** 表格占位符：U+FFFC（Object Replacement Character），Markwon 会原样当普通字符保留 ✓ */
    private const val TABLE_PH = '\uFFFC'

    /** 数据类：解析好的表格 */
    private class MdTable(val header: List<String>, val body: List<List<String>>)

    private val tableRowLine = Pattern.compile("(?m)^\\s*\\|.*\\|\\s*$")

    private val tableSepLine =
        Pattern.compile("^\\s*\\|\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)+\\|?\\s*$")

    /**
     * 把 markdown 表格块换成占位符。
     *
     * ⚠️ 必须在 [preprocess] **之前**做 ✗：
     * preprocess 会解 HTML 实体、改写 `<a>` ⇒ 表格里的内容会被动 ✗
     *
     * 正常处理的表格（满足全部条件才算 ✓）：
     * - 连续 `| … |` 行，≥ 3 行（表头 + 分隔 + 至少一行数据 ✓）
     * - 第 2 行是分隔行（`|---|---|` 或 `|:--|--:|`）
     * - 所有行列数一致
     * ⚠️ ``` 围栏里的内容原样保留 ✗（代码块里可能正好有 md 表格示例 ✗）
     */
    private fun extractTables(text: String, out: MutableList<MdTable>): String {
        if (!text.contains('|')) return text
        val lines = text.split("\n")
        val kept = ArrayList<String>(lines.size)
        var i = 0
        var inFence = false
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                kept.add(line)
                i++
                continue
            }
            if (inFence || !tableRowLine.matcher(line).matches()) {
                kept.add(line)
                i++
                continue
            }
            var j = i
            while (j < lines.size && tableRowLine.matcher(lines[j]).matches()) j++
            val block = lines.subList(i, j)
            val t = runCatching { parseTableBlock(block) }.getOrNull()
            if (t != null) {
                kept.add(TABLE_PH.toString())
                out.add(t)
            } else {
                kept.addAll(block)          // 解析不了 ⇒ 原样留给 Markwon ✓
            }
            i = j
        }
        return kept.joinToString("\n")
    }

    /** @return 解析好的表格；不是合法表格返回 null */
    private fun parseTableBlock(block: List<String>): MdTable? {
        if (block.size < 3) return null
        val header = splitCells(block[0]) ?: return null
        if (header.isEmpty()) return null
        if (!tableSepLine.matcher(block[1]).matches()) return null
        val body = ArrayList<List<String>>(block.size - 2)
        for (k in 2 until block.size) {
            val cells = splitCells(block[k]) ?: return null
            if (cells.size != header.size) return null
            body.add(cells)
        }
        if (body.isEmpty()) return null
        return MdTable(header.map { plainCell(it) }, body.map { row -> row.map { plainCell(it) } })
    }

    /** `| a | b |` → ["a","b"]；不以 `|` 开头返回 null */
    private fun splitCells(line: String): List<String>? {
        var t = line.trim()
        if (!t.startsWith("|")) return null
        t = t.substring(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
        return t.split("|").map { it.trim() }
    }

    /** 单元格里的简单行内 markdown 剥成纯文本（表格里不做富文本 ✗） */
    private fun plainCell(src: String): String {
        var v = decodeEntities(src)
        v = Regex("\\*\\*(.+?)\\*\\*").replace(v) { it.groupValues[1] }
        v = Regex("~~(.+?)~~").replace(v) { it.groupValues[1] }
        v = Regex("`([^`]*)`").replace(v) { it.groupValues[1] }
        v = Regex("\\[([^\\]]*)]\\([^)]*\\)").replace(v) { it.groupValues[1] }
        return v.trim()
    }

    /** 渲染完把占位符替换成 [MdTableSpan] ✓ */
    private fun placeTables(
        spanned: Spanned,
        tables: List<MdTable>,
        availWidth: Int,
        padPx: Int,
    ): Spanned {
        val sb = if (spanned is SpannableStringBuilder) spanned else SpannableStringBuilder(spanned)
        var from = 0
        for (t in tables) {
            val pos = TextUtils.indexOf(sb, TABLE_PH, from)
            if (pos < 0) break
            sb.setSpan(
                MdTableSpan(t.header, t.body, availWidth, padPx),
                pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            from = pos + 1
        }
        return sb
    }
}
