package com.example.c001apk.logic.model

/**
 * 搜索联想（输入过程中的下拉建议）
 * 接口：GET /v6/search/suggestSearchWordsNew?searchValue=xx&type=app
 *
 * 首项固定是「搜索用户：<关键词>」，url = searchTab://user?keyword=xx（logo 是本地图标名）；
 * 其余项是普通联想词，url = searchTab://all?keyword=xx。
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
        val entityType: String? = null
    )
}
