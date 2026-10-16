package com.example.c001apk.desktop.core.net

import com.example.c001apk.desktop.core.model.HomeEntity
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.test.Test

/**
 * 用**真实接口数据**验证首页解析链路，定位「界面上只有入口标题、没有动态正文」。
 *
 * 背景：`/v6/main/indexV8` 的 `data` 是异质混合数组，实测构成：
 * ```
 * 顶层 11 项 = card(4) + feed(7)
 *   card 卡内：image_1 / iconLink / product / topic / feed
 * 全层级：feed 27 个，**全部带正文**
 * ```
 * UI 里用 `runCatching { gson.fromJson(el, HomeEntity::class.java) }` 解析 feed，
 * `runCatching` 会把 Gson 的类型异常**静默吞掉** → 动态就消失了。
 * 这个测试把解析失败的原因暴露出来。
 */
class HomeParseTest {

    private val gson = Gson()
    private val enabled = System.getProperty("runLiveTests") == "true"
    private val session = Session()
    private val client = Client(session = session)

    @Test
    fun `用真实数据验证 feed 能否解析成 HomeEntity`() {
        if (!enabled) {
            println("跳过：需要 -DrunLiveTests=true")
            return
        }
        val resp = client.api.indexV8(page = 1).executeSync()
        val data: List<JsonElement> = resp.data ?: error("data 为空")

        var feedCount = 0
        var parsedOk = 0
        val failures = mutableListOf<String>()

        fun emit(el: JsonElement, depth: Int) {
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return
            val type = obj.get("entityType")?.takeIf { it.isJsonPrimitive }?.asString
            if (type == "feed" || obj.has("message")) {
                feedCount++
                val r = runCatching { gson.fromJson(el, HomeEntity::class.java) }
                val e = r.getOrNull()
                if (e == null) {
                    failures.add("解析抛异常：${r.exceptionOrNull()?.let { it::class.simpleName + ": " + it.message }}")
                } else if ((e.message ?: "").isBlank()) {
                    failures.add("解析成功但 message 为空（entityId=${e.entityId} username=${e.username}）")
                } else {
                    parsedOk++
                }
                return
            }
            if (depth < 3) {
                obj.getAsJsonArray("entities")?.forEach { emit(it, depth + 1) }
            }
        }
        data.forEach { emit(it, 0) }

        println("=== 首页解析验证 ===")
        println("feed 总数      : $feedCount")
        println("解析成功且非空 : $parsedOk")
        println("失败           : ${failures.size}")
        failures.take(8).forEach { println("  ✗ $it") }

        // 额外：检查是否有个别字段类型导致整条失败
        if (failures.isNotEmpty()) {
            val sample = runCatching { findFirstFeed(data) }.getOrNull()
            if (sample != null) {
                println()
                println("--- 首个 feed 的字段类型实况（用于对齐 POJO）---")
                sample.entrySet().take(40).forEach { (k, v) ->
                    println("  %-24s %s".format(k, describe(v)))
                }
            }
        }
    }

    private fun findFirstFeed(data: List<JsonElement>): JsonObject? {
        fun find(el: JsonElement, depth: Int): JsonObject? {
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val t = o.get("entityType")?.takeIf { it.isJsonPrimitive }?.asString
            if (t == "feed") return o
            if (depth < 3) {
                val inner = o.getAsJsonArray("entities") ?: return null
                for (i in 0 until inner.size()) {
                    find(inner.get(i), depth + 1)?.let { return it }
                }
            }
            return null
        }
        for (el in data) {
            find(el, 0)?.let { return it }
        }
        return null
    }

    private fun describe(v: JsonElement): String = when {
        v.isJsonNull -> "null"
        v.isJsonPrimitive -> {
            val p = v.asJsonPrimitive
            when {
                p.isBoolean -> "Boolean = ${p.asBoolean}"
                p.isNumber -> "Number = ${p.asNumber}  (原始 ${p.asString})"
                else -> "String = ${p.asString.take(60)}"
            }
        }
        v.isJsonArray -> "Array[${v.asJsonArray.size()}]"
        else -> "Object{${v.asJsonObject.entrySet().take(5).joinToString { it.key }}}"
    }
}
