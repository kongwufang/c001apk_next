package com.example.c001apk.desktop.core.text

/**
 * 酷安动态正文里的**行内 HTML** 解析器。
 *
 * ── 为什么需要 ──
 * `/v6/main/indexV8`、`/v6/feed/detail` 等接口的 `message` 字段**不是纯文本**，
 * 而是内嵌 HTML，实测形如：
 * ```
 * 我一直好奇…为什么还 1.5k？ <a class="feed-link-tag" href="/t/%E9%85%B7%E5%AE%89%E5%A4%9C%E8%AF%9D?type=0">#酷安夜话#</a>
 * ```
 * 直接显示会露出标签源码（这就是「页面显示有问题」的根因）。
 *
 * ── 解析结果 ──
 * 产出 [TextRun] 序列：文本片段 + 是否为链接（带 href）。
 * 渲染层（Compose）把它转成 `AnnotatedString`：链接用主色、可点击。
 *
 * ── 支持的标签 ──
 * * `<a ...>text</a>` → 链接（取 `href` 属性）
 * * `<br>` / `<br/>` → 换行
 * * `<p>` → 前后各换行
 * * 其它标签 → 丢弃标签本身，但插入一个空格分隔（避免"词粘在一起"）
 *
 * ── 实体解码 ──
 * 常见的 `&amp; &lt; &gt; &quot; &#39; &nbsp;` 以及数字实体 `&#123;` / `&#x1F600;`。
 */
object HtmlText {

    /**
     * 解析结果的一段。
     *
     * 三种形态一起产出，渲染层只需按类型装配 `AnnotatedString`：
     * * [Text] —— 普通文字（可能带 [Text.href]，表示它在 `<a>` 里）
     * * [Emoji] —— 表情码，如 `[受虐滑稽]`；由渲染层换成表情图
     */
    sealed interface Piece {
        val href: String?

        /** 普通文字 */
        data class Text(val text: String, override val href: String? = null) : Piece

        /** 表情码 */
        data class Emoji(val code: String, override val href: String? = null) : Piece
    }

    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\\s+[^<>]*?)?)(/?)>", RegexOption.DOT_MATCHES_ALL)
    private val ATTR = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0", "ensp" to "\u2002", "emsp" to "\u2003",
        "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "times" to "×", "middot" to "·", "bull" to "•", "deg" to "°",
    )

    /**
     * 解析行内 HTML。
     *
     * @param html 原始字段值（可能为 null）
     * @param keepNewlines 是否保留原有的 `\n`（动态正文通常保留）
     */
    fun parse(html: String?, keepNewlines: Boolean = true): List<Piece> {
        if (html.isNullOrEmpty()) return emptyList()

        val runs = mutableListOf<Piece>()
        val buf = StringBuilder()
        var linkHref: String? = null

        fun flush() {
            if (buf.isEmpty()) return
            val t = buf.toString()
            buf.setLength(0)
            // 顺手把表情码切出来：酷安正文里 [受虐滑稽] 这类写法要渲染成图片
            Emoji.parse(t).forEach { p ->
                when (p) {
                    is Emoji.Piece.Text -> {
                        val last = runs.lastOrNull()
                        // 合并相邻同型片段，减少片段数
                        if (last is Piece.Text && last.href == linkHref) {
                            runs[runs.size - 1] = last.copy(text = last.text + p.text)
                        } else {
                            runs.add(Piece.Text(p.text, linkHref))
                        }
                    }
                    is Emoji.Piece.Code -> runs.add(Piece.Emoji(p.code, linkHref))
                }
            }
        }

        /** 当前位置是否已经处于「行首」——buf 为空且已产出内容以换行结尾 */
        fun atLineStart(): Boolean {
            if (buf.isNotEmpty()) return buf[buf.length - 1] == '\n'
            val last = runs.lastOrNull() ?: return true
            val t = when (last) {
                is Piece.Text -> last.text
                is Piece.Emoji -> last.code
            }
            return t.isEmpty() || t.last() == '\n'
        }

        var i = 0
        while (i < html.length) {            val lt = html.indexOf('<', i)
            if (lt < 0) {
                buf.append(decodeEntities(html.substring(i)))
                break
            }
            if (lt > i) buf.append(decodeEntities(html.substring(i, lt)))

            val m = TAG.find(html, lt)
            if (m == null || m.range.first != lt) {
                // 不是合法标签，当普通字符处理（避免死循环）
                buf.append('<')
                i = lt + 1
                continue
            }

            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val attrsRaw = m.groupValues[3]
            val selfClosing = m.groupValues[4] == "/"

            when {
                name == "br" -> {
                    flush()
                    if (keepNewlines) buf.append('\n')
                }

                name == "a" -> {
                    if (!closing) {
                        flush()
                        linkHref = attr(attrsRaw, "href")
                    } else {
                        flush()
                        linkHref = null
                    }
                }

                // 块级标签：只在「当前不以换行结尾」时补一个换行，
                // 避免 <p>甲</p><p>乙</p> 这种连续块产生空行
                name == "p" || name == "div" -> {
                    flush()
                    if (keepNewlines && !atLineStart()) buf.append('\n')
                }

                else -> {
                    // 其它标签（img/span/b/i/…）：丢弃标签，补一个空格避免词粘连
                    flush()
                    if (!selfClosing && !closing && buf.isNotEmpty() && !buf.endsWith(" ")) {
                        buf.append(' ')
                    }
                }
            }
            i = m.range.last + 1
        }
        flush()

        return runs.filter {
            when (it) {
                is Piece.Text -> it.text.isNotEmpty()
                // 表情永远保留（哪怕是空码也不会有）
                is Piece.Emoji -> true
            }
        }
    }

    /**
     * 只要纯文本（用于摘要、日志、搜索）。
     * 表情以原始表情码形式保留 —— 纯文本场景下它比一张图更有信息量。
     */
    fun toPlainText(html: String?, keepNewlines: Boolean = true): String =
        parse(html, keepNewlines).joinToString("") {
            when (it) {
                is Piece.Text -> it.text
                is Piece.Emoji -> it.code
            }
        }.trim()

    private fun attr(raw: String, key: String): String? {
        for (m in ATTR.findAll(raw)) {
            if (m.groupValues[1].equals(key, ignoreCase = true)) {
                val v = m.groupValues[3].ifEmpty {
                    m.groupValues[4].ifEmpty { m.groupValues[5] }
                }
                return v
            }
        }
        return null
    }

    internal fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x", true) ->
                    body.drop(2).toIntOrNull(16)?.let { cp(it) } ?: m.value
                body.startsWith("#") ->
                    body.drop(1).toIntOrNull()?.let { cp(it) } ?: m.value
                else -> NAMED_ENTITIES[body.lowercase()] ?: m.value
            }
        }
    }

    private fun cp(code: Int): String =
        runCatching { String(Character.toChars(code)) }.getOrDefault("")
}

/**
 * 片段的「文本形态」：普通文字取文字本身，表情取原始表情码（如 `[受虐滑稽]`）。
 *
 * 用于摘要 / 日志 / 拼接纯文本等不需要图片的场景。
 *
 * 注意必须定义在**顶层** —— 放在 `HtmlText` object 内部属于「成员扩展」，
 * object 外部（含测试）无法解析到，会报 `Unresolved reference`。
 */
val HtmlText.Piece.plain: String
    get() = when (this) {
        is HtmlText.Piece.Text -> text
        is HtmlText.Piece.Emoji -> code
    }