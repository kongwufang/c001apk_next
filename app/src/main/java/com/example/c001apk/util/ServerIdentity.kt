package com.example.c001apk.util

import okhttp3.Request

/**
 * 本 App 与**自建服务器**（service.houlangs.cn）之间的接口契约版本。
 *
 * ## 为什么单独一个版本号（2026-11-07 用户要求）
 *
 * 用户原话：
 *   「把 APP 请求我的服务器的接口增加一个新字段，叫做接口版本，
 *     也是版本号硬编码在代码里。这样的话，下游代码就算改了 APP 版本号，
 *     接口的版本号也不变，我也能准确认出他是哪个版本复刻过来的。目前版本号为 1」
 *
 * ⇒ 关键点：**接口版本与 App 版本号完全无关** ✗
 *   · [BuildConfig.VERSION_NAME] / VERSION_CODE 下游随便改 ✓ 我们照样认得出 ✅
 *   · 这个常量**只在接口语义变化时** +1 ✓（加字段/改字段含义/改协议）
 *   · 复刻者通常整份拷代码 ⇒ 只要他没改这里，就能看出他是从哪一代分叉的 ✓
 *
 * ## 版本历史
 * ```
 * 1  2026-11-07  首个版本（heartbeat / notice / update / userverify / reliablelink /
 *                blocklist / config/gates / report / share 全部带上）
 * ```
 *
 * ## 用法
 * ```kotlin
 * ServerIdentity.applyTo(builder)              // OkHttp Request.Builder
 * ServerIdentity.asHeaderPairs()               // 需要 map 的地方（UpdateChecker）
 * ```
 */
object ServerIdentity {

    /** HTTP 头名 */
    const val HEADER_API = "X-Client-Api"

    /**
     * ★ 接口版本 —— **改动它之前先想清楚** ✗
     *
     * 什么时候 +1：
     *   · 响应里新增/删除/改名了字段 ✓
     *   · 某个字段的**含义**变了 ✓（哪怕名字没变）
     *   · 认证/签名/限流规则变了 ✓
     * 什么时候**不要**动：
     *   · 只改 App 内部实现 ✗
     *   · 只改 UI / 文案 ✗
     *   · App 版本号涨了 ✗（这是两套号 ✓）
     */
    const val API_VERSION = 1

    /** OkHttp：给请求加上接口版本头 */
    fun applyTo(builder: Request.Builder): Request.Builder =
        builder.header(HEADER_API, API_VERSION.toString())

    /** 需要「头名 → 值」列表的地方（比如 UpdateChecker 的 headers map） */
    fun asHeaderPairs(): List<Pair<String, String>> =
        listOf(HEADER_API to API_VERSION.toString())
}
