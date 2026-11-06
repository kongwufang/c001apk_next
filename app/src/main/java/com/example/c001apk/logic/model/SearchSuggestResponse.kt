package com.example.c001apk.logic.model

import com.google.gson.annotations.SerializedName

/**
 * 搜索联想（输入过程中的下拉建议）
 * 接口：GET /v6/search/suggestSearchWordsNew?searchValue=xx&type=app
 *
 * 实测（2026-11-06，`薅羊毛`，24 条）真实结构比原先想得复杂：
 *
 *   · `entityType=searchWord`（21 条）—— 普通联想词
 *       { logo, title, url="searchTab://all?keyword=xx", entityType }
 *       首项固定是「搜索用户：<关键词>」，url = searchTab://user?keyword=xx
 *
 *   · `entityType=card` + `entityTemplate=iconListCard`（2 条）—— 列表卡
 *       卡里 `entities[]` 是 1~3 个 **topic** 实体：
 *         { title, description, logo, url="/t/xxx",
 *           hot_num_txt="13.2万", commentnum_txt="11.0万",
 *           follownum_txt="5.4万", entityType="topic" }
 *       官方布局：卡片 = item_title_list（cardStyle 2 + 内嵌 RecyclerView + 1dp 分隔线）
 *                行   = item_topic_view（56dp logo + 标题 + description + 右箭头）
 *       见 `_rev/card_scan_out/_DEX_TPL_LAYOUT2.tsv`
 *
 *   · `entityType=card` + `entityTemplate=imageScaleCard`（1 条）—— 横幅
 *       `entities[0]` = { entityType="imageScale", title, pic, url="/clickUrl/..." }
 *       官方布局：item_image_list_card（Banner，aspectRatio 3%）
 *
 * `extraDataArr` 里是卡片容器参数（cardContainerRadius / cardId / cardPageName …）。
 */
data class SearchSuggestResponse(
    val status: Int? = null,
    val message: String? = null,
    val data: List<Data>? = null
) {
    data class Data(
        val logo: String? = null,
        val title: String? = null,
        val url: String? = null,
        val entityType: String? = null,
        /** card 才有：iconListCard / imageScaleCard / … */
        val entityTemplate: String? = null,
        /** card 才有：卡内条目 */
        val entities: List<Entity>? = null,
        /** card 才有：{cardId, cardContainerRadius, cardPageName, info} 等 */
        @SerializedName("extraDataArr")
        val extraData: ExtraData? = null,
        val entityId: String? = null,
    ) {
        /** card 里的条目；searchWord 那条没有子实体 */
        data class Entity(
            val title: String? = null,
            val description: String? = null,
            val logo: String? = null,
            /** imageScale 用它当图 */
            val pic: String? = null,
            val url: String? = null,
            val entityType: String? = null,
            val entityId: String? = null,
            @SerializedName("hot_num_txt") val hotNumTxt: String? = null,
            @SerializedName("commentnum_txt") val commentNumTxt: String? = null,
            @SerializedName("follownum_txt") val followNumTxt: String? = null,
            val username: String? = null,
        )

        data class ExtraData(
            val cardId: String? = null,
            @SerializedName("cardContainerRadius") val radius: String? = null,
            @SerializedName("cardPageName") val pageName: String? = null,
            val info: String? = null,
        )

        /** 是不是卡片条目 */
        val isCard: Boolean get() = entityType == "card"

        /** 卡片模板名（searchWord 为 null） */
        val template: String get() = entityTemplate.orEmpty()

        /** 第一条子实体（banner 用） */
        val firstEntity: Entity? get() = entities?.firstOrNull()
    }
}
