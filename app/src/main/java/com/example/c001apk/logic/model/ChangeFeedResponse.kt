package com.example.c001apk.logic.model

/**
 * `changeDetail` / `changeFeed` 的返回（**单个动态对象** ✓）
 *
 * ⚠️ 不能复用 [HomeFeedResponse] ✗ —— 它的 `data` 是 `List<Data>`（给列表接口用的 ✗）
 * 而这两个接口返回的 `data` 是**一个对象** ✗
 *
 * 实测（2026-10-24）：
 * - `GET /v6/feed/changeDetail?id=` ⇒ `{"data":{…134 个字段…}}`
 * - `POST /v6/feed/changeFeed`      ⇒ `{"data":{…改完的动态…}}`
 * - 失败时 ⇒ `{"status":500,"message":"…"}` 或 `{"status":-2,"message":"…"}`
 */
data class ChangeFeedResponse(
    val data: HomeFeedResponse.Data? = null,
    val message: String? = null,
    val status: Int? = null,
    val messageStatus: Any? = null,
)
