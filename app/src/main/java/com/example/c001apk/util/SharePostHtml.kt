package com.example.c001apk.util

import android.text.Html
import com.example.c001apk.logic.model.HomeFeedResponse

/**
 * 把一篇动态渲染成**单帖分享用的 HTML**。
 *
 * 设计取向（跟大合集导出不同）：
 * - 单帖 ⇒ 一屏能看完，**不要目录、不要序号**，就是一个干净的阅读页 ✓
 * - 图片位置用 `{{IMG0}}`、`{{IMG1}}` 占位 ✓ 真正上传时由服务器替换成
 *   `img/01.jpg` 并把分享版改写成 CDN 绝对地址 ✓
 * - 深色自动适配 + 打印友好 + 图片按数量自适应网格 ✓
 */
object SharePostHtml {

    private const val CSS = """
:root{--bg:#f6f7f9;--card:#fff;--fg:#1b1f24;--muted:#6a737d;--line:#e3e6ea;
 --accent:#3b82f6;--tag:#eef2f7;--shadow:0 1px 2px rgba(0,0,0,.06),0 8px 24px rgba(0,0,0,.04)}
@media (prefers-color-scheme:dark){:root{--bg:#0f1216;--card:#171b21;--fg:#e6e9ee;
 --muted:#98a2b3;--line:#262c34;--accent:#60a5fa;--tag:#1e242c;
 --shadow:0 1px 2px rgba(0,0,0,.4),0 8px 24px rgba(0,0,0,.25)}}
*{box-sizing:border-box}
body{margin:0;padding:24px 14px 60px;background:var(--bg);color:var(--fg);
 font:16px/1.72 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
 -webkit-font-smoothing:antialiased}
.wrap{max-width:720px;margin:0 auto}
article{background:var(--card);border:1px solid var(--line);border-radius:16px;
 padding:20px 20px 14px;box-shadow:var(--shadow)}
h1{margin:0 0 12px;font-size:20px;line-height:1.45;letter-spacing:-.01em}
.meta{display:flex;flex-wrap:wrap;gap:6px 14px;color:var(--muted);font-size:13px;margin-bottom:16px}
.meta .who{color:var(--fg);font-weight:600}
.body{white-space:pre-wrap;word-break:break-word;font-size:15.5px;margin:0 0 14px}
.tags{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 14px}
.tags span{font-size:12px;color:var(--accent);background:var(--tag);border-radius:6px;padding:2px 8px}
.grid{display:grid;gap:8px;margin:0 0 14px}
.grid img{width:100%;border-radius:10px;border:1px solid var(--line);display:block;object-fit:cover;background:var(--bg)}
.grid.g1{grid-template-columns:1fr}
.grid.g1 img{max-height:520px;object-fit:contain;background:transparent;padding:4px}
.grid.g2{grid-template-columns:1fr 1fr}
.grid.g3{grid-template-columns:repeat(3,1fr)}
.grid.g2 img,.grid.g3 img{aspect-ratio:1/1}
footer{display:flex;flex-wrap:wrap;gap:12px;align-items:center;border-top:1px solid var(--line);
 padding-top:12px;font-size:13px;color:var(--muted)}
footer a{color:var(--accent);text-decoration:none}
footer a:hover{text-decoration:underline}
.brand{text-align:center;color:var(--muted);font-size:12.5px;margin-top:26px}
@media print{body{background:#fff;padding:0}article{box-shadow:none;break-inside:avoid}}
"""

    /** HTML 转义（正文是 HTML，先降级成纯文本再转义 ✓） */
    private fun esc(s: String?): String = (s ?: "").replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * 正文降级成纯文本。
     *
     * 酷安正文是 HTML（带 `<a class="feed-link-tag">`）✗ 直接塞进 HTML 会双重渲染 ✗
     * 这里用平台 `Html.fromHtml` 降级 ✓ 保留换行 ✓（跟 MessageKit.copyText 一个思路）
     */
    fun plainText(html: String?): String {
        val raw = html ?: return ""
        if (raw.isBlank()) return ""
        if (raw.indexOf('<') < 0) return raw.trim()
        return try {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        } catch (e: Throwable) {
            raw
        }
    }

    /** 标题：正文首行，最多 80 字；没正文就用「xxx的动态」 */
    fun titleOf(data: HomeFeedResponse.Data): String {
        val first = plainText(data.message).lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        return when {
            !first.isNullOrBlank() -> first.take(80)
            !data.username.isNullOrBlank() -> "${data.username}的动态"
            else -> "动态"
        }
    }

    /** 图片列表：只收图片型 URL（`picArr` 里的原图 ✓） */
    fun picsOf(data: HomeFeedResponse.Data): List<String> =
        (data.picArr ?: emptyList()).filter { it.isNotBlank() }.take(12)

    private fun timeText(ts: Long): String {
        if (ts <= 0) return ""
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        return sdf.format(java.util.Date(ts * 1000))
    }

    /**
     * 生成单帖 HTML。
     *
     * @param imgCount 实际会上传的图片数 ⇒ 生成 `{{IMG0}}`…`{{IMGn}}` 占位
     * @return HTML 字符串（含占位符，由服务器替换 ✓）
     */
    fun build(data: HomeFeedResponse.Data, imgCount: Int): String {
        val title = titleOf(data)
        val body = plainText(data.message)
        val tags = (data.tags ?: "")
            .split(",")
            .map { it.replace("#", "").trim() }
            .filter { it.isNotBlank() }

        val tagsHtml = if (tags.isEmpty()) "" else
            """<div class="tags">${tags.joinToString("") { "<span>#${esc(it)}</span>" }}</div>"""

        val grid = if (imgCount <= 0) "" else {
            val cls = when {
                imgCount == 1 -> "g1"
                imgCount <= 4 -> "g2"
                else -> "g3"
            }
            val imgs = (0 until imgCount).joinToString("") {
                """<img src="{{IMG$it}}" alt="" loading="lazy">"""
            }
            """<div class="grid $cls">$imgs</div>"""
        }

        val id = data.id ?: ""
        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
            append("<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
            append("<meta name=\"robots\" content=\"noindex,nofollow\">\n")
            append("<title>${esc(title)}</title>\n")
            append("<style>$CSS</style>\n</head>\n<body>\n<div class=\"wrap\">\n<article>\n")
            append("  <h1>${esc(title)}</h1>\n")
            append("  <div class=\"meta\">\n")
            append("    <span class=\"who\">${esc(data.username)}</span>\n")
            append("    <span>${esc(timeText(data.dateline ?: 0L))}</span>\n")
            append("    <span>👍 ${data.likenum ?: "0"}</span>\n")
            append("    <span>💬 ${data.commentnum ?: "0"}</span>\n")
            append("  </div>\n")
            append("  <div class=\"body\">${esc(body)}</div>\n")
            append("  $tagsHtml\n")
            append("  $grid\n")
            append("  <footer>\n")
            append("    <a href=\"https://www.coolapk.com/feed/$id\" target=\"_blank\" rel=\"noreferrer\">查看原帖 ↗</a>\n")
            append("    <span>ID $id</span>\n")
            append("  </footer>\n")
            append("</article>\n<div class=\"brand\">由 c001apk_next 分享</div>\n</div>\n</body>\n</html>\n")
        }
    }
}
