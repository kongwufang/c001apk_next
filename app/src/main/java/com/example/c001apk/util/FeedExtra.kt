package com.example.c001apk.util

import com.example.c001apk.logic.model.HomeFeedResponse
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * 动态卡里除正文/媒体/热评之外的附加信息块：**点评评分 / 二手商品 / 商品清单**。
 *
 * 这块的取舍完全按实测（482 条真实 feed 实体，`_rev/_t_feed_slot_scope_out.txt`）来的 ——
 * 官方 24 槽表里有、但**真实数据里根本没有**的槽位一律不做：
 *  - 二手：`ershou_info` 非空 23 条 → 做
 *  - 评分点评：`star` / `rating_score` / `rating_item_info` 非空 48 条 → 做
 *  - 商品清单：`extra_title` / `extra_url` / `extra_pic` 非空 24 条 → 做
 *  - 富源卡变体（官方 `source_space_view`）：`source_id` 全是空串 → **不做**
 *  - 已删除蒙层（官方 `foreground_text_view`）：`isDelete` / `foreground_text` 在 feed 上不存在 → **不做**
 *  - 问大家：只有 `questionUrl` / `questionStatus`，`question_title`、`question_content` 全空，
 *    回答数/关注数都是 0 → **不做**（造出来就是一个空条）
 *  - 京东商品清单 `extra_entities` / `goodsListInfo` → 不做（只出现在导购 tab，要单独一套商品卡）
 *
 * 字段一律走 [HomeFeedResponse.Data.raw]，口径与 [FeedVideo] 一致，不往模型里加字段。
 * 内层键名取自真实样本（`_rev/_t_slot_shapes_out.txt`），不是猜的。
 */
data class FeedExtra(
    val rating: Rating? = null,
    val ershou: Ershou? = null,
    val goods: Goods? = null
) {
    val isEmpty: Boolean get() = rating == null && ershou == null && goods == null

    /**
     * 点评评分。样本（feed 74276786）：
     * `star = 5`（五星制）、`rating_score = 10`（十分制）、
     * `rating_item_info = [{"name":"续航","v4_score":8,...}, ...]` 六个维度，`v4_score` 是 0~10。
     */
    data class Rating(
        val star: String,
        val score: String,
        val items: List<Dim>
    ) {
        data class Dim(val name: String, val score: Int)
    }

    /**
     * 二手商品。样本（feed 74276825）内层键名：
     * `product_title` / `product_price`（数字）/ `product_config_data`（JSON 串，里面 `configTitle`）/
     * `province` + `city` / `store_type_txt` / `product_logo` / `link_url` + `link_source`（"闲鱼"）。
     */
    data class Ershou(
        val title: String,
        val price: String,
        val config: String,
        val area: String,
        val storeType: String,
        val logo: String,
        val linkUrl: String,
        val linkSource: String
    )

    /** 商品清单单卡：`extra_title` / `extra_url`（形如 `/goodsList/38916622`）/ `extra_pic` */
    data class Goods(
        val title: String,
        val url: String,
        val pic: String
    )

    companion object {
        fun from(data: HomeFeedResponse.Data): FeedExtra? = data.raw?.let { from(it) }

        fun from(raw: JsonObject): FeedExtra? {
            val extra = FeedExtra(
                rating = rating(raw),
                ershou = ershou(raw),
                goods = goods(raw)
            )
            return extra.takeUnless { it.isEmpty }
        }

        private fun rating(raw: JsonObject): Rating? {
            val items = array(raw, "rating_item_info").mapNotNull { node ->
                val name = str(node, "name")
                val score = str(node, "v4_score").toIntOrNull() ?: return@mapNotNull null
                if (name.isEmpty()) null else Rating.Dim(name, score)
            }
            val star = str(raw, "star")
            val score = str(raw, "rating_score")
            // 只有个孤零零的分数不算点评（实测 `rank_score` 这种恒为 0 的字段到处都是）
            if (star.isEmpty() && items.isEmpty()) return null
            return Rating(star = star, score = score, items = items)
        }

        private fun ershou(raw: JsonObject): Ershou? {
            val info = obj(raw, "ershou_info") ?: return null
            val title = str(info, "product_title")
            if (title.isEmpty()) return null
            val config = obj(info, "product_config_data")?.let { str(it, "configTitle") } ?: ""
            val province = str(info, "province")
            val city = str(info, "city")
            return Ershou(
                title = title,
                price = priceText(str(info, "product_price")),
                config = config,
                area = if (city == province || city.isEmpty()) province else "$province $city",
                storeType = str(info, "store_type_txt"),
                logo = str(info, "product_logo").http2https,
                linkUrl = str(info, "link_url"),
                linkSource = str(info, "link_source")
            )
        }

        private fun goods(raw: JsonObject): Goods? {
            val title = str(raw, "extra_title")
            val url = str(raw, "extra_url")
            val pic = str(raw, "extra_pic")
            if (title.isEmpty() && pic.isEmpty()) return null
            return Goods(title = title, url = url, pic = pic.http2https)
        }

        private fun array(node: JsonObject?, vararg keys: String): List<JsonObject> {
            val value = element(node, *keys) ?: return emptyList()
            if (!value.isJsonArray) return emptyList()
            return value.asJsonArray.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
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
            element(node, *keys)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""

        /**
         * 对象本身，或「装着对象的 JSON 串」都能拿到。`product_config_data` / `v4_rating_message`
         * 这类字段实测都是**被转义的 JSON 字符串**，直接 `getAsJsonObject` 会炸。
         */
        private fun obj(node: JsonObject?, vararg keys: String): JsonObject? {
            val value = element(node, *keys) ?: return null
            if (value.isJsonObject) return value.asJsonObject
            if (!value.isJsonPrimitive) return null
            val text = value.asString.trim()
            if (!text.startsWith("{")) return null
            return try {
                JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            } catch (_: Exception) {
                null
            }
        }

        /** 价格：样本里是数字 `190`，也兜字符串小数，非数字原样返回 */
        private fun priceText(value: String): String {
            if (value.isEmpty()) return ""
            val number = value.toDoubleOrNull() ?: return value
            return if (number == number.toLong().toDouble()) {
                number.toLong().toString()
            } else {
                String.format(Locale.ROOT, "%.2f", number)
            }
        }
    }
}
