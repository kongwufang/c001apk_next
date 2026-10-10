package com.example.c001apk.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * 界面规格表（uiskin）：把「界面里能调的数值」从布局 / 代码里搬进可热更新的配置。
 *
 * 和 [CardUi] 是同一套路子，但管的东西不同：
 *   - `CardUi` 管「首页顶层卡片用哪个骨架、取哪些字段」（结构）；
 *   - `UiSkin` 管「某个现成控件的字号 / 行距 / 尺寸 / 显隐 / 文案」（数值）。
 *
 * **能力边界**：只能改已经存在的控件，改不了布局结构 —— 加控件、改约束、挪位置仍然要发版。
 * 所以这里的键必须在 App 里有对应的「读取点」，写了没有对应读取点的键等于没写。
 *
 * 远端 JSON 按**页面.控件.属性**嵌套写，解析时摊成扁平键，只写要覆盖的项：
 * ```json
 * {"version":1,
 *  "feed":{"metaTextSize":12,"messageLineSpacing":1.3},
 *  "feedDetail":{"titleTextSize":18,"messageLineSpacing":1.5}}
 * ```
 *
 * **取值一律返回可空**：`null` 表示「没有配 / 配得不像个数」＝别动，继续用布局里的原值。
 * 这样布局仍是默认值的真源，规格表只做覆盖，不会出现两处各维护一份数值的问题。
 */
object UiSkin {

    @Volatile
    private var values: Map<String, Any> = emptyMap()

    /**
     * 装载远端规格表。空串 / 解析不出来时什么都不做（继续用上次的远端表或布局原值），
     * 所以配置写坏了最多是「这次不生效」，不会把界面弄出乱子。
     */
    fun load(json: String?) {
        val text = json?.takeIf { it.isNotBlank() } ?: return
        val parsed = runCatching { parse(text) }.getOrNull() ?: return
        if (parsed.isNotEmpty()) values = parsed
    }

    fun reset() {
        values = emptyMap()
    }

    /** 当前生效的规格项数（排查用） */
    fun size(): Int = values.size

    /** 当前生效的扁平键（排查用，如 `feed.metaTextSize`） */
    fun keys(): Set<String> = values.keys

    /**
     * 取数值：sp、dp、倍率都走这里，单位由调用方决定。
     * 没配过 / 不是数字时返回 null＝保持布局原值。
     */
    fun f(key: String): Float? = when (val v = values[key]) {
        is Float -> v
        is String -> v.toFloatOrNull()
        else -> null
    }

    /** 取开关。没配过 / 不是布尔时返回 null＝保持布局原值 */
    fun b(key: String): Boolean? = values[key] as? Boolean

    /** 取文案。没配过 / 空串都返回 null（空串不当成「要把文字清掉」） */
    fun s(key: String): String? = (values[key] as? String)?.takeIf { it.isNotEmpty() }

    private fun parse(json: String): Map<String, Any> {
        val out = mutableMapOf<String, Any>()
        val root = JSONObject(json)
        flatten("", root, out)
        // version 是链路用的元信息，不该混进规格里（不然它自己就成了一条「规格」）
        out.remove("version")
        out.remove("note")
        return out
    }

    /**
     * 把嵌套 JSON 摊成 `页面.控件.属性` 扁平键。
     * 数组不收：规格表里每一项都该能直接落到某个控件属性上，数组没法直说该落哪。
     */
    private fun flatten(prefix: String, obj: JSONObject, out: MutableMap<String, Any>) {
        obj.keys().forEach { key ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            when (val value = obj.opt(key)) {
                is JSONObject -> flatten(path, value, out)
                is Boolean -> out[path] = value
                is Number -> out[path] = value.toFloat()
                is String -> value.trim().takeIf { it.isNotEmpty() }?.let { out[path] = it }
                is JSONArray -> Unit
                else -> Unit
            }
        }
    }
}
