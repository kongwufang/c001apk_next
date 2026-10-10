package com.example.c001apk.logic.model

import com.google.gson.JsonElement

/**
 * `POST /v6/player/getUrl`（酷安播放器解析接口）的响应。
 *
 * 实测（2026-10-11，微博 / B 站 / 酷安自托管 4 个 provider 各打一条）：
 * ```
 * 成功：{"data":{"urlList":["https://f.video.weibocdn.com/...mp4?..."],
 *               "audioList":["..."], "durationList":[11676]}}
 * 失败：{"data":[]}
 * ```
 * 两个要点：
 *  - **`data` 声明成 [JsonElement] 而不是具体类**。失败时它是个 JSON 数组，声明成对象会让 Gson
 *    在转换层抛 `JsonSyntaxException`，把「这条解析不出来」变成一次崩溃上报；自己判形状更干净。
 *  - B 站是 DASH，`urlList` 只是**视频**流、`audioList` 才是**音频**流，只有一条就是没声音的哑片，
 *    播放器必须两条都用（见 `FeedVideoView`）。
 */
data class VideoUrlResponse(
    val data: JsonElement?,
)
