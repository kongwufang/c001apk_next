package com.example.c001apk.desktop.core.text

/**
 * 表情解析：把正文里的 `[受虐滑稽]` 这类**表情码**切成可渲染的分段。
 *
 * 表情表 [EmojiTable] 与图片 PNG 都来自 Android 端同一仓库
 * （`util/EmojiUtils.kt` + `res/drawable-v1/`），图片放在本模块的 `resources/emoji/`。
 *
 * ⚠️ 这里**只做纯文本解析**，不涉及 Compose/图形类型 —— 因为 `core` 是纯 JVM 模块。
 * 图片加载与绘制在 app 模块（见 `ui/home/EmojiText.kt`）。
 *
 * ## 为什么要拆成「分段」而不是直接替换成占位符
 * 表情在 Compose 里必须走 `AnnotatedString.appendInlineContent`（行内内容），
 * 而它在**构建 AnnotatedString 时**就要知道每段是文字还是表情；
 * 图片本身是异步加载的。所以先切段，再由渲染层按段装配。
 */
object Emoji {

    /** 一段文本：要么是普通文字，要么是一个表情码 */
    sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Code(val code: String) : Piece
    }

    /** 表情码形如 `[xxx]` */
    private val CODE_RE = Regex("""\[[^\[\]]{1,12}]""")

    /**
     * 把文本切成 [Piece] 序列。
     *
     * 只有**在表情表里能找到**的 `[...]` 才算表情，否则保持原文 ——
     * 这样才能正确显示 `[1]`、`[图片]`（表里无图）这类普通方括号内容。
     */
    fun parse(text: String): List<Piece> {
        if (text.isEmpty() || '[' !in text) return listOf(Piece.Text(text))

        val out = mutableListOf<Piece>()
        var last = 0
        for (m in CODE_RE.findAll(text)) {
            val code = m.value
            if (!EmojiTable.BY_NAME.containsKey(code)) continue
            if (m.range.first > last) out.add(Piece.Text(text.substring(last, m.range.first)))
            out.add(Piece.Code(code))
            last = m.range.last + 1
        }
        if (last == 0) return listOf(Piece.Text(text))
        if (last < text.length) out.add(Piece.Text(text.substring(last)))
        return out
    }

    /** 文本里是否含表情 */
    fun hasEmoji(text: String): Boolean = parse(text).any { it is Piece.Code }

    /** 表情码 → 资源文件名（不含扩展名），不在表里返回 null */
    fun fileName(code: String): String? = EmojiTable.BY_NAME[code]
}
