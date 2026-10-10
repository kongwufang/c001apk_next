package com.example.c001apk.logic.model

/** 收藏夹（多收藏夹）相关模型，字段与官方接口一致（HAR 实测） */
data class CollectionListResponse(
    val data: List<CollectionData>?,
    val message: String?
)

data class CollectionDetailResponse(
    val data: CollectionData?,
    val message: String?
)

/**
 * `POST /v6/collection/addItem` 的响应。
 *
 * 成功体**只有 `data` 包装、没有 `status`**：`{"data":{"favnum":95,"collect":1}}`；
 * 失败才有 `status`/`message`（-5「该内容已存在」、-7「该内容不存在于该收藏单」），
 * 所以判成功要看 `data` 在不在，不能看 status。
 */
data class CollectionActionResponse(
    val data: CollectionActionData?,
    val status: Int?,
    val message: String?
)

/** addItem 的净结果：`favnum`=服务端最新收藏数；`collect`=该动态此刻是否在这个夹（1=已收藏） */
data class CollectionActionData(
    val favnum: Int?,
    val collect: Int?
)

/**
 * 一次收藏/取消收藏的结果，弹窗往外传：
 * `collectionId` 是本次动的那个夹（弹窗拿它就地改这一项的勾，不重拉列表），
 * `favnum` / `collect` 给详情页改底栏数字和星标。
 */
data class CollectionAction(
    val collectionId: String,
    val favnum: Int?,
    val collect: Int
)

data class CollectionCheckCountResponse(
    val data: String?,
    val message: String?
)

/** `POST /v6/collection/uploadImage` 返回图片 URL */
data class CollectionUploadResponse(
    val data: String?,
    val message: String?
)

/** 返回纯文本提示的接口，如 `POST /v6/collection/delete`（「删除成功」）、`removeUnUseItem`（「开始清除…」） */
data class StringDataResponse(
    val data: String?,
    val message: String?
)

data class CollectionData(
    val id: String?,
    val uid: String?,
    val username: String?,
    val title: String?,
    val description: String?,
    val cover_pic: String?,
    val is_open: Int?,
    val is_open_title: String?,
    val item_num: Int?,
    val follow_num: Int?,
    val type: Int?,
    val url: String?,
    val entityType: String?,
    val entityId: String?,
    /**
     * 该动态是否已收藏进这个夹（1=已收藏），仅在带 `id=<动态id>&type=feed` 查询时下发。
     * 可变：收藏/取消后按 addItem 回的结果就地改，不再重拉收藏夹列表。
     */
    var isBeCollected: Int?,
    /** 系统默认收藏夹 */
    val isDefault: Int? = null
)
