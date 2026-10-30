package com.example.c001apk.ui.messagedetail

import com.example.c001apk.logic.model.MessageResponse

/**
 * 聊天列表的一项 = **服务端消息** + **本地发送状态**。
 *
 * ## 为什么需要这一层
 *
 * 原来列表直接吃 `List<MessageResponse.Data>`，只有服务端确认过的消息才会出现 ✗
 * —— 用户点发送后要等一次网络往返（实测 0.3~1s，弱网更久）才看到自己那条，
 * 体感就是「卡了」✗
 *
 * 现在改成**乐观发送**：
 * 1. 点发送 ⇒ 立刻往列表塞一条 `state = SENDING` 的本地消息（左侧转圈）✓
 * 2. 服务端返回成功 ⇒ `state = SENT`，转圈消失 ✓
 * 3. 失败 ⇒ `state = FAILED`，显示红色感叹号，点一下弹「是否重新发送」✓
 *
 * ## 主键
 *
 * [key] 优先用 [localId]（本地消息一旦建立就**不再变**，保证 DiffUtil 认为是同一项、
 * 只刷新状态，不会整条重建导致闪烁 ✓）；服务端消息退回 `entityId` → `id` → 兜底串。
 */
data class ChatItem(
    val data: MessageResponse.Data,
    /** 见 [STATE_SENT] / [STATE_SENDING] / [STATE_FAILED] */
    val state: Int = STATE_SENT,
    /**
     * 本地乐观消息的稳定标识（`local:<uuid>`）；服务端消息为 null。
     * ⚠️ 发送成功后**不清空** —— 清空会让 key 变掉、DiffUtil 把同一项当成删除+新增 ✗
     */
    val localId: String? = null,
    /**
     * 真正发给服务端的图片对象名（带前导斜杠，如 `/message/2026/1006/x.png`）。
     *
     * 为什么单独存：本地消息的 `data.messagePic` 存的是**本地 uri**（用于立刻显示缩略图 ✓），
     * 而接口要的是 OSS 对象名 ✗ 两者不能混。重发时要用这个 ✔
     */
    val apiPic: String = ""
) {

    val key: String
        get() = localId
            ?: data.entityId?.takeIf { it.isNotBlank() }
            ?: data.id.takeIf { it.isNotBlank() }
            ?: "${data.dateline}-${data.fromuid}-${data.message}"

    val isSending: Boolean get() = state == STATE_SENDING

    val isFailed: Boolean get() = state == STATE_FAILED

    /** 还没被服务端确认（发送中或失败）—— `loadHistory` 时要把这些保留在末尾 ✓ */
    val isPending: Boolean get() = state != STATE_SENT

    fun withState(newState: Int): ChatItem = copy(state = newState)

    fun withData(newData: MessageResponse.Data): ChatItem = copy(data = newData)

    companion object {
        /** 服务端已确认 */
        const val STATE_SENT = 0

        /** 正在发（显示转圈） */
        const val STATE_SENDING = 1

        /** 发送失败（显示红色感叹号，可点击重发） */
        const val STATE_FAILED = 2

        /**
         * 构造一条**本地乐观消息**。
         *
         * 字段只填渲染必需的那几个（`Data` 其余字段都有默认值 ✓）：
         * `fromuid` **必须等于当前登录 uid**，否则气泡会跑到左边 ✗
         *
         * @param localPic 本地图片 uri（`content://…`），只用于立刻显示缩略图；
         *                 纯文字消息传空串
         * @param apiPic   真正发给服务端的 OSS 对象名（`/message/…png`）；纯文字传空串
         */
        fun local(
            myUid: String,
            text: String,
            localPic: String = "",
            apiPic: String = "",
            at: Long = System.currentTimeMillis() / 1000
        ): ChatItem = ChatItem(
            data = MessageResponse.Data(
                entityType = "message",
                fromuid = myUid,
                uid = myUid,
                dateline = at,
                message = text,
                messagePic = localPic.takeIf { it.isNotBlank() },
                entityId = null,
                id = ""
            ),
            state = STATE_SENDING,
            localId = "local:" + java.util.UUID.randomUUID(),
            apiPic = apiPic
        )
    }
}
