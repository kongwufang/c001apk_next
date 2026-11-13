package com.example.c001apk.util

/**
 * 实况图片（Live Photo）识别与地址推导（2026-11-13）
 *
 * 逆向结论（官方 dex）：
 * · 没有专门的实况接口，识别靠 **URL 正则**
 *   `https?://\S+livepic\S*@\d+x\d+\.(jpg|jpeg|png|gif|webp)`
 * · 视频与封面共用同一段 hash 前缀，视频就是把 `@宽x高.jpg` 去掉再加 `.mp4`
 *   （对应官方那句 "it has not a live photo hash string"）
 * · 官方 UI：live_photo_tag（角标）/ live_photo_view（播放）/ live_photo_switch_view（切换）
 *
 * 样本（真机抓到的）：
 *   封面 http://image.coolapk.com/feed/2026/1109/20/44881806_cbfafce2_7304_3823_91-livepic@872x1920.jpg
 *   视频 http://image.coolapk.com/feed/2026/1109/20/44881806_cbfafce2_7304_3823_91-livepic.mp4
 */
object LivePhoto {

    /** 命中这个标记就当作实况图（官方正则里的关键字） */
    private const val MARK = "livepic"

    fun isLive(url: String?): Boolean =
        !url.isNullOrEmpty() && url.contains(MARK, ignoreCase = true)

    fun isLive(urls: Collection<String>?): Boolean =
        urls != null && urls.any { isLive(it) }

    /**
     * 从封面地址推导配套视频地址
     *
     * 规则：
     *  1. 先去掉九宫格缩略图后缀 `.s.jpg` / `.thumbnail.jpg`
     *  2. 去掉 `@872x1920` 这类尺寸后缀（含其后的扩展名）
     *  3. 补 `.mp4`
     */
    fun videoUrl(coverUrl: String?): String? {
        if (!isLive(coverUrl)) return null
        var u = coverUrl!!.trim()
        u = u.removeSuffix(".s.jpg").removeSuffix(".thumbnail.jpg")
        val at = u.lastIndexOf('@')
        if (at > 0) u = u.substring(0, at)
        // 去掉可能残留的扩展名（.jpg/.jpeg/.png/.webp/.gif）
        listOf(".jpeg", ".jpg", ".png", ".webp", ".gif").forEach { ext ->
            if (u.endsWith(ext, ignoreCase = true)) u = u.dropLast(ext.length)
        }
        return u + ".mp4"
    }

    /** 拿去显示的封面（去掉 .s.jpg 缩略后缀，拿原始 jpg） */
    fun coverUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return url.removeSuffix(".s.jpg").removeSuffix(".thumbnail.jpg")
    }
}
