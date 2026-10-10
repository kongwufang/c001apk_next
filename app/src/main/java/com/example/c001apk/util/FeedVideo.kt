package com.example.c001apk.util

import com.example.c001apk.logic.model.HomeFeedResponse
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * 动态里的视频信息（封面 / 时长 / 播放地址 / 播放请求头）。
 *
 * 字段不写进 [HomeFeedResponse.Data]，一律走 [HomeFeedResponse.Data.raw] 按别名链取 ——
 * 视频这块同一份数据有好几个来源（顶层 `media_*`、`video` 对象、`media_info` 里的 JSON 串），
 * 而且不同 provider 下发的位置不一样，跟参考实现（_rev/ref_src 的 `feedMedia.ts`）同一套路，
 * 免得往模型里堆一堆字段还要发版。
 *
 * 口径来自 19 条真实视频动态（`_rev/card_scan_out/06_V9_HOME_TAB_SHIPIN.json` 等）：
 *  - 类型判定：`media_type = 2`（数字）或 `feedType = "video"` / `feedTypeName = "视频"`，
 *    `media_info.mediaType` 也会给 `"video"`。
 *  - **`pic` / `picArr` 实测全为空**，封面只在 `media_info.cover` 里 —— 所以九图槽位对视频动态
 *    不出图，必须由视频槽自己出封面。
 *  - `media_url` 顶层多数是「分享页地址」（9 条 B 站 + 8 条微博），不是能播的地址。
 *  - 只有 `media_info.requestParams` 里 `fromType = coolapkVideo` 的那一档，`"0"` 才是可直接播放的
 *    mp4（实测 2/19）。requestParams 形如：
 *    `{"普通":{"fromType":"coolapkVideo","0":"https://.../xxx.mp4?feedId=1","1":80992}}`
 *  - `media_info.duration` 是**毫秒**；`media_info.width` / `height` 多数时候不给（给的时候是 1280x720）。
 *
 * 微博 / B 站这类 `media_url` 只是分享页地址的，播放地址要再过一次官方解析接口
 * `POST /v6/player/getUrl`（表单字段 `params`）——[withResolved] 负责吃掉它的返回。实测
 * （2026-10-11，4 个 provider 各打一条）：
 *  - `params` 必须是**单个档位对象**，形如 `{"fromType":"weiboDirect250924","0":"https://..."}`，
 *    返回 `{"data":{"urlList":["https://f.video.weibocdn.com/...mp4?..."]}}`；
 *  - 传整张档位表（`{"普通":{...},"32":{...}}`）一律返回 `{"data":[]}` —— 参考实现的注释
 *    「值是 provider-specific requestParams」说得含糊，按整表传是不通的；
 *  - **B 站是 DASH**：`urlList` 是视频流、`audioList` 才是音频流，只播前者会没声音；
 *  - **B 站还要带请求头**（`media_info.playHeaders` 给 `Referer: https://www.bilibili.com/video/BV...`），
 *    不带就是 403。
 */
data class FeedVideo(
    /** 可直接播放的地址；空串表示还没解析出来，只能先出封面 */
    val url: String,
    val poster: String,
    /** 已格式化成 `m:ss` / `h:mm:ss`；空串表示服务端没给 */
    val duration: String,
    /** `media_info.requestParams` 里的 fromType，仅用于排查 */
    val provider: String,
    /**
     * 交 `POST /v6/player/getUrl` 解析用的 `params`：**单个档位对象**的 JSON 原文，
     * 不是整张档位表（实测口径见类注释）。
     */
    val requestParams: String = "",
    /** DASH 的音频流（B 站音视频分开下发）；空串表示单条就够 */
    val audioUrl: String = "",
    /** 播放请求头；实测只有 B 站要 `Referer` */
    val headers: Map<String, String> = emptyMap(),
    /** 封面宽高比 = width / height；服务端多数不给，缺省 16:9 */
    val aspectRatio: Float = DEFAULT_ASPECT
) {
    /** 已经有能播的地址（直接就是直链，或已经解析过） */
    val playable: Boolean get() = url.isNotEmpty()

    /** 还没地址但有解析参数 —— 点一下可以尝试解析 */
    val resolvable: Boolean get() = url.isEmpty() && requestParams.isNotEmpty()

    /**
     * 吃掉 `/v6/player/getUrl` 的返回，补上可播地址。
     *
     * 解析不出来时服务端给的是 `{"data":[]}`（数组），所以这里必须自己判形状，形状不对就原样
     * 返回 —— 界面继续只出封面，绝不抛异常给上层。
     */
    fun withResolved(data: JsonElement?): FeedVideo {
        val node = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return this
        val video = firstUrl(node, "urlList", "url_list")
        if (video.isEmpty()) return this
        return FeedVideo(
            url = video.http2https,
            poster = poster,
            duration = duration,
            provider = provider,
            requestParams = requestParams,
            audioUrl = firstUrl(node, "audioList", "audio_list").http2https,
            headers = headers,
            aspectRatio = aspectRatio
        )
    }

    companion object {
        /** 官方 feed 封面的默认比例 */
        const val DEFAULT_ASPECT = 16f / 9f

        private const val TYPE_VIDEO = "video"
        private const val TYPE_VIDEO_CN = "视频"

        /** 这些 provider 的 `"0"` 就是能直接播的地址 */
        private val DIRECT_PROVIDERS = setOf("coolapkvideo", "localvideo")

        /** 地址以这些结尾才算媒体直链（分享页地址永远不带后缀，天然被排除） */
        private val MEDIA_SUFFIX = listOf(".mp4", ".m3u8", ".m4v")

        fun from(data: HomeFeedResponse.Data): FeedVideo? =
            data.raw?.let { from(it, data.feedType, data.feedTypeName) }

        fun from(
            raw: JsonObject,
            feedType: String? = null,
            feedTypeName: String? = null
        ): FeedVideo? {
            val info = obj(raw, "media_info", "mediaInfo")
            val mediaType = str(raw, "media_type", "mediaType")
            val isVideo = mediaType == "2" ||
                    mediaType.equals(TYPE_VIDEO, ignoreCase = true) ||
                    str(info, "mediaType", "media_type").equals(TYPE_VIDEO, ignoreCase = true) ||
                    feedType.equals(TYPE_VIDEO, ignoreCase = true) ||
                    feedTypeName == TYPE_VIDEO_CN
            if (!isVideo) return null

            val video = obj(raw, "video", "videoInfo", "video_info")
            val params = lastRequestParams(info)
            val provider = str(params, "fromType", "from_type")

            val url = when {
                provider.lowercase(Locale.ROOT) in DIRECT_PROVIDERS ->
                    str(params, "0", "url", "url0")
                else -> ""
            }
                .ifEmpty { str(raw, "video_url", "videoUrl", "videoURL", "videoSrc", "video_src") }
                .ifEmpty { str(video, "url", "videoUrl", "src", "playUrl", "play_url") }
                .ifEmpty { directMediaUrl(raw) }

            val poster = str(raw, "video_pic", "videoPic", "video_cover", "videoCover")
                .ifEmpty { str(raw, "media_pic", "mediaPic") }
                .ifEmpty { str(info, "cover", "pic", "poster", "thumbnail") }
                .ifEmpty { str(video, "pic", "cover", "poster", "thumbnail") }
                .ifEmpty { str(raw, "pic") }
                .http2https

            val duration = formatDuration(
                str(raw, "video_duration", "videoDuration")
                    .ifEmpty { str(video, "duration", "videoDuration") },
                assumeMillis = false
            ).ifEmpty {
                formatDuration(str(info, "duration", "videoDuration"), assumeMillis = true)
            }

            return FeedVideo(
                url = url.http2https,
                poster = poster,
                duration = duration,
                provider = provider,
                requestParams = params?.toString() ?: "",
                headers = headersOf(info),
                aspectRatio = aspectOf(info)
            )
        }

        /**
         * `media_info.playHeaders`，实测形如 `{"Referer":"https://www.bilibili.com/video/BV..."}`。
         * 值可能是字符串也可能是嵌套对象，非字符串一律丢掉（ExoPlayer 只认字符串头）。
         */
        private fun headersOf(info: JsonObject?): Map<String, String> {
            val node = obj(info, "playHeaders", "play_headers") ?: return emptyMap()
            val result = LinkedHashMap<String, String>()
            for ((key, value) in node.entrySet()) {
                if (value.isJsonPrimitive) result[key] = value.asString
            }
            return result
        }

        /** 封面比例。服务端给错值时不至于把卡片撑坏，夹在 1:2 ~ 2.4:1 之间 */
        private fun aspectOf(info: JsonObject?): Float {
            val width = str(info, "width").toFloatOrNull() ?: return DEFAULT_ASPECT
            val height = str(info, "height").toFloatOrNull() ?: return DEFAULT_ASPECT
            if (width <= 0f || height <= 0f) return DEFAULT_ASPECT
            return (width / height).coerceIn(0.5f, 2.4f)
        }

        /** 取数组里的第一个非空字符串（`urlList` / `audioList` 都是数组，失败时整个 data 是 `[]`） */
        private fun firstUrl(node: JsonObject, vararg keys: String): String {
            val array = element(node, *keys)?.takeIf { it.isJsonArray }?.asJsonArray ?: return ""
            for (item in array) {
                if (item.isJsonPrimitive) {
                    val text = item.asString.trim()
                    if (text.isNotEmpty()) return text
                }
            }
            return ""
        }

        /** `media_url` / `mediaUrl`，仅当它本身就是媒体直链时才认 */
        private fun directMediaUrl(raw: JsonObject): String {
            val url = str(raw, "media_url", "mediaUrl", "mediaURL")
            return if (MEDIA_SUFFIX.any { url.lowercase(Locale.ROOT).contains(it) }) url else ""
        }

        /**
         * 取 requestParams 的最后一档（参考实现 `selectVideoRequestParams` 取的是 `Object.values(...).at(-1)`）。
         * 值可能是对象，也可能是 JSON 串，两种都兜住。
         */
        private fun lastRequestParams(info: JsonObject?): JsonObject? {
            val node = parseObject(element(info, "requestParams", "request_params")) ?: return null
            var last: JsonElement? = null
            for (entry in node.entrySet()) last = entry.value
            return parseObject(last)
        }

        private fun element(node: JsonObject?, vararg keys: String): JsonElement? {
            if (node == null) return null
            for (key in keys) {
                val value = node.get(key) ?: continue
                if (!value.isJsonNull) return value
            }
            return null
        }

        private fun str(node: JsonObject?, vararg keys: String): String =
            element(node, *keys)
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?: ""

        /** 对象本身，或是「装着对象的 JSON 串」都能拿到，其余一律 null */
        private fun obj(node: JsonObject?, vararg keys: String): JsonObject? =
            parseObject(element(node, *keys))

        private fun parseObject(node: JsonElement?): JsonObject? {
            if (node == null || node.isJsonNull) return null
            if (node.isJsonObject) return node.asJsonObject
            if (!node.isJsonPrimitive) return null
            val text = node.asString.trim()
            if (!text.startsWith("{")) return null
            return try {
                JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            } catch (_: Exception) {
                null
            }
        }

        /**
         * 时长格式化。服务端给过 `"1:23"` 这种成品，也给过纯数字：
         * 数字默认按秒算，`media_info.duration` 明确是毫秒所以调用处传 [assumeMillis]。
         */
        private fun formatDuration(value: String, assumeMillis: Boolean): String {
            if (value.isEmpty()) return ""
            if (value.contains(':')) return value
            val number = value.toDoubleOrNull() ?: return ""
            if (number <= 0) return ""
            val total = (if (assumeMillis || number >= 1000) number / 1000 else number).toInt()
            val hour = total / 3600
            val minute = total % 3600 / 60
            val second = total % 60
            return if (hour > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hour, minute, second)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minute, second)
            }
        }
    }
}
