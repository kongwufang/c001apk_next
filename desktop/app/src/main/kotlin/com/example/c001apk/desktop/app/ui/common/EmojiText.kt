package com.example.c001apk.desktop.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.core.text.Emoji
import com.example.c001apk.desktop.core.text.HtmlText
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 把 [HtmlText] 的解析结果装成带样式、带表情行内内容的 `AnnotatedString`。
 *
 * 这是动态正文的统一渲染入口：链接用主色、表情走 `appendInlineContent`。
 *
 * @param pieces [HtmlText.parse] 的产出
 * @param linkColor 链接颜色（默认主色）
 * @param emojiSizeEm 表情相对字号的大小（1.25 表示 1.25 倍字号）
 * @return 装配好的 AnnotatedString；是否含表情可由 [EmojiImages] 缓存情况决定
 */
@Composable
fun buildRichText(
    pieces: List<HtmlText.Piece>,
    linkColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
): AnnotatedString {
    // 预取本串用到的表情（触发重组后即可同步取到）
    val codes = pieces.filterIsInstance<HtmlText.Piece.Emoji>().map { it.code }.distinct()
    LaunchedEffect(codes) { codes.forEach { EmojiImages.ensure(it) } }

    return buildAnnotatedString {
        pieces.forEach { p ->
            when (p) {
                is HtmlText.Piece.Text ->
                    if (p.href != null) {
                        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                            append(p.text)
                        }
                    } else append(p.text)

                is HtmlText.Piece.Emoji -> {
                    val file = Emoji.fileName(p.code)
                    val bmp = file?.let { EmojiImages.get(it) }
                    if (bmp == null) {
                        append(p.code)                 // 图缺失：回退成表情码文字
                    } else {
                        appendInlineContent("emoji:${p.code}", p.code)
                    }
                }
            }
        }
    }
}

/**
 * 取出这些片段里「已加载成功」的表情，构造 `inlineContent` 表。
 *
 * 注意：只有出现在 [buildRichText] 里同一批 [pieces] 的表情才会被用到，
 * 传错集合会导致 `IllegalArgumentException`（行内内容缺占位）。
 */
@Composable
fun emojiInlineContents(pieces: List<HtmlText.Piece>): Map<String, androidx.compose.ui.text.InlineTextContent> {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val codes = remember(pieces) {
        pieces.filterIsInstance<HtmlText.Piece.Emoji>().map { it.code }.distinct()
    }
    return remember(codes, density) {
        codes.mapNotNull { code ->
            if (EmojiImages.get(Emoji.fileName(code) ?: return@mapNotNull null) == null) return@mapNotNull null
            "emoji:$code" to emojiInlineContent(code, density)
        }.toMap()
    }
}

/** 构造单个表情的行内内容 */
private fun emojiInlineContent(code: String, density: Density): androidx.compose.ui.text.InlineTextContent {
    // 占位：宽度 = 1.25em，高度 = 0.3em（基线上方一点），图片再整体下移对齐基线
    val placeholderW = with(density) { EMOJI_SIZE_EM.sp.toDp() }
    val placeholderH = with(density) { (EMOJI_SIZE_EM * 0.25f).sp.toDp() }

    return androidx.compose.ui.text.InlineTextContent(
        placeholder = androidx.compose.ui.text.Placeholder(
            width = placeholderW,
            height = placeholderH,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.AboveBaseline,
        ),
        children = { EmojiImage(code = code, size = placeholderW, shiftDown = placeholderH) },
    )
}

/** 表情大小（相对字号倍数）：1.25em 与 Android 端表情的行内观感接近 */
private const val EMOJI_SIZE_EM = 17f

@Composable
private fun EmojiImage(code: String, size: androidx.compose.ui.unit.Dp, shiftDown: androidx.compose.ui.unit.Dp) {
    val file = Emoji.fileName(code)
    val bmp = file?.let { EmojiImages.get(it) }
    if (bmp == null) {
        Text(code)
        return
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val boxPx = with(density) { size.toPx() }
    val shiftPx = with(density) { shiftDown.toPx() }

    Box(
        Modifier
            .size(size)
            .drawWithContent {
                // 占位只占基线上方一点，把图整体下移，使其贴合基线并与文字居中对齐
                translate(top = shiftPx) {
                    drawImage(
                        image = bmp,
                        dstSize = androidx.compose.ui.unit.IntSize(boxPx.toInt(), boxPx.toInt()),
                    )
                }
            },
    )
}

/**
 * 表情图全局缓存。
 *
 * 151 张、共约 1.2 MB：按需加载（首次遇到某表情才读它的 PNG），
 * 之后一直驻留。读的是 classpath 的 `emoji` 目录下的 png。
 */
object EmojiImages {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap?>()

    /** 同步取；未加载过则立即加载 */
    fun get(file: String): ImageBitmap? = cache.computeIfAbsent(file) { read(it) }

    /** 预取 */
    fun ensure(code: String) {
        val file = Emoji.fileName(code) ?: return
        cache.computeIfAbsent(file) { read(it) }
    }

    fun loadedCount(): Int = cache.count { it.value != null }

    private fun read(file: String): ImageBitmap? = runCatching {
        val stream = EmojiImages::class.java.classLoader
            .getResourceAsStream("emoji/$file.png") ?: return null
        val bytes = stream.use { it.readBytes() }
        ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
    }.getOrNull()
}
