package com.example.c001apk.desktop.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.c001apk.desktop.core.text.Emoji
import com.example.c001apk.desktop.core.text.HtmlText
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 富文本渲染：把 [HtmlText.parse] 的产出装成 `AnnotatedString`，
 * 链接走主色、表情走行内图片（`appendInlineContent`）。
 *
 * ## 表情在 Compose 里怎么对齐
 * `InlineTextContent.placeholder` 的宽高是 **`TextUnit`**（不是 `Dp`）—— 容易写错。
 * `PlaceholderVerticalAlign.AboveBaseline` 的意思是「占位底边落在基线**之上**」，
 * 所以占位只取很小的高度（0.5 倍表情大小），再把图片**下移**到基线位置：
 * 占位底边在基线，图片需下移 `占位高 + 表情边长`。
 * 占位取小值还有个好处：行高不会被表情撑开。
 */
@Composable
fun buildRichText(
    pieces: List<HtmlText.Piece>,
    linkColor: Color = MaterialTheme.colorScheme.primary,
): AnnotatedString {
    // 预取本串用到的表情（首次用到会同步读盘，之后命中缓存）
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
                    } else {
                        append(p.text)
                    }

                is HtmlText.Piece.Emoji -> {
                    val file = Emoji.fileName(p.code)
                    // 图缺失或尚未加载好：回退成表情码文字，保证不丢信息
                    if (file == null || EmojiImages.get(file) == null) {
                        append(p.code)
                    } else {
                        appendInlineContent(inlineKey(p.code), p.code)
                    }
                }
            }
        }
    }
}

/**
 * 构造本批片段里**已加载成功**的表情的行内内容表。
 *
 * ⚠️ 必须与 [buildRichText] 传入**同一批 pieces**：`AnnotatedString` 里出现过的
 * inline key 若在表里缺失，`Text` 会抛 `IllegalArgumentException`。
 */
@Composable
fun emojiInlineContents(pieces: List<HtmlText.Piece>): Map<String, InlineTextContent> {
    val codes = remember(pieces) {
        pieces.filterIsInstance<HtmlText.Piece.Emoji>().map { it.code }.distinct()
    }
    // 与 buildRichText 用同一条预取逻辑：保证两边对「这个表情加载好了没」判断一致。
    // 否则 inline key 与 inlineContent 表不匹配时，Text 会抛 IllegalArgumentException。
    LaunchedEffect(codes) { codes.forEach { EmojiImages.ensure(it) } }

    // 注意：不能在 remember{} 里调 @Composable 函数（其 lambda 不是 @Composable 上下文），
    // 故直接构建 —— 一行里表情通常只有几个，开销可接受。
    return codes.mapNotNull { code ->
        val file = Emoji.fileName(code) ?: return@mapNotNull null
        if (EmojiImages.get(file) == null) return@mapNotNull null
        inlineKey(code) to emojiInlineContent(code)
    }.toMap()
}

private fun inlineKey(code: String) = "emoji:$code"

/** 表情边长（相对字号倍数，1.25 与 Android 端表情观感接近） */
private const val EMOJI_EM = 1.25f

/**
 * 单个表情的行内内容。
 *
 * `Placeholder` 的宽高是 `TextUnit`，用 `sp` 表达可自动跟随字体缩放；
 * 显式 `* 1f` 是为了拿到 `TextUnit` 而不是 `Float`。
 */
@Composable
private fun emojiInlineContent(code: String): InlineTextContent {
    val density = LocalDensity.current
    val sideSp = (EMOJI_EM * 16f).sp
    val placeholderHSp = (EMOJI_EM * 8f).sp
    val sideDp = with(density) { sideSp.toDp() }

    return InlineTextContent(
        placeholder = Placeholder(
            width = sideSp,
            height = placeholderHSp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
        ),
        children = {
            EmojiImage(
                code = code,
                box = sideDp,
                // 占位底边已在基线上方，图片再下移「占位高 + 半个表情」使其视觉居中于文字
                shiftDownPx = with(density) {
                    (placeholderHSp.toDp() + sideDp * 0.5f).toPx()
                },
            )
        },
    )
}

@Composable
private fun EmojiImage(code: String, box: Dp, shiftDownPx: Float) {
    val file = Emoji.fileName(code)
    val bmp: ImageBitmap? = file?.let { EmojiImages.get(it) }
    if (bmp == null) {
        Text(code)
        return
    }
    val sidePx = with(LocalDensity.current) { box.toPx() }
    Box(
        Modifier
            .size(box)
            .drawWithContent {
                translate(top = shiftDownPx) {
                    drawImage(
                        image = bmp,
                        dstSize = IntSize(sidePx.toInt(), sidePx.toInt()),
                    )
                }
            },
    )
}

/**
 * 表情图全局缓存。
 *
 * 151 张、共约 1.2 MB：按需加载（首次遇到某表情才读它的 PNG），之后常驻。
 * 资源位于 classpath 的 `emoji/` 目录（由 core 模块的 `resources/emoji` 打包而来）。
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

    /** 已成功加载的数量（诊断用） */
    fun loadedCount(): Int = cache.count { it.value != null }

    private fun read(file: String): ImageBitmap? = runCatching {
        val stream = EmojiImages::class.java.classLoader
            .getResourceAsStream("emoji/$file.png") ?: return null
        val bytes = stream.use { it.readBytes() }
        ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
    }.getOrNull()
}
