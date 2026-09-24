package com.example.c001apk.logic.model

import com.google.gson.annotations.SerializedName

/**
 * 搜索页「热门搜索 + 热搜榜」的返回值
 * 接口：GET /v6/search?type=hotSearch&refresh=0&returnType=all
 *
 * data 是卡片数组：
 *  - entityTemplate = "hotSearch"         → 热门搜索词条容器
 *  - entityTemplate = "searchHotListCard" → 热搜榜容器（数码热榜/资讯热榜/品牌实时榜/话题热榜）
 *
 * 热门搜索词条、榜单 tab、榜单项三者字段完全一致，靠嵌套的 entities 区分层级。
 */
data class SearchHotResponse(
    val status: Int? = null,
    val message: String? = null,
    val data: List<Card>? = null
) {

    data class Card(
        val entityType: String? = null,
        val entityTemplate: String? = null,
        val title: String? = null,
        val url: String? = null,
        val entities: List<Item>? = null
    )

    data class Item(
        val id: String? = null,
        val title: String? = null,
        val logo: String? = null,
        val url: String? = null,
        val entityType: String? = null,
        @SerializedName("hot_num") val hotNum: String? = null,
        /** 服务端已格式化好的热度文案（如「528万」），优先展示它 */
        @SerializedName("hot_num_txt") val hotNumTxt: String? = null,
        /** 只有榜单 tab 才带下一级榜单条目 */
        val entities: List<Item>? = null
    )
}
