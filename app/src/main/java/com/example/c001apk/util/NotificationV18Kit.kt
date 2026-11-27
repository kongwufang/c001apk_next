package com.example.c001apk.util

import android.text.TextUtils
import com.example.c001apk.logic.model.MessageResponse
import com.google.gson.Gson

/**
 * `/v6/notificationV18/list` 的适配层。
 *
 * 官方 16.6.x 的消息中心已经换成这条统一通知流：一次返回全部通知，靠 `note_type`
 * 分类，`noteTypeTitle` 是服务端给好的中文标题（「评论了你的动态」/「回复了你的评论」…）。
 * 服务端**不认任何过滤参数**（[list_group] / type / unread 实测都返回同一份混排，
 * 连 `/v6/notificationV18/count` 都是 404），所以分类只能客户端做。
 *
 * 换它的两个好处（对比老 `/v6/notification/list`，同账号实测）：
 * 老接口 31 条里 26 条是酷安小秘书刷屏、正文还是 HTML；V18 的 53 条里小秘书收敛成
 * 1 条系统消息 + 1 条活动消息，正文是纯文本，并且**多出「回复了你的评论」这一类**
 * （老接口一条都没有）。老接口那 5 个业务条目按业务主键（url 里的 rid）在 V18 里
 * 全部能找到，所以换源不丢东西。
 */
object NotificationV18Kit {

    /** V18 通知流端点。NetworkRepo 靠这个字符串决定要不要走归一化 */
    const val URL = "/v6/notificationV18/list"

    /**
     * V18 条目的 `entityType`。
     * 老接口的通知是 `notification`，渲染侧有 entityType 白名单，不认它会整页空白。
     */
    const val ENTITY_TYPE = "notificationV18"

    /**
     * 「我收到的赞」端点，同一家族的另一条流。
     *
     * 它跟 [URL] 不同：这条**只回 `feed_like` 一类**（抓包 20/20 条都是），所以不用过滤；
     * 但字段是另一套 —— 谁点的赞在 `from_uid` / `from_username` / `fromUserAvatar` 上，
     * 被赞的是哪条动态只给到 `target_id` 和它的封面 `addition_info`，动态正文一个字都没有。
     * 老接口 `/v6/notification/feedLikeList` 那边正好相反：正文给的是动态内容，
     * 点赞人藏在 `likeUsername` 里，所以渲染侧一直在显示「我赞了我自己的动态」。
     */
    const val LIKE_URL = "/v6/notificationV18/likeList"

    /**
     * 「回复我的」两种类型。
     *
     * V18 是混排流，还会夹带 `atme`（@你的动态）和 `notify_system` / `notify_activity`
     * （系统 / 活动消息）。这些宫格另有入口，混进来会顶掉 commentMe 的未读预算
     * （下方汇总列表是按「服务端未读数 = 该分类最新 N 条」截断的），所以只留回复类。
     *
     * 注：点评回复被并进了 `feed_reply` —— 实测老接口里 `rating_reply` 的那条
     * （`url` 里的 `rid=607459058`）在 V18 里就是 `feed_reply`，
     * 所以不能再靠类型区分「评论了你的动态」和「评论了你的点评」，文案统一成前者。
     */
    fun isReplyType(noteType: String?): Boolean =
        noteType == "feed_reply" || noteType == "feed_reply_reply"

    // Some wire responses explicitly set id to null despite the legacy model's Kotlin type.
    private fun nonBlank(value: String?): String? = value?.takeIf { it.isNotBlank() }

    private fun cursorOf(data: MessageResponse.Data): String? =
        nonBlank(data.id) ?: nonBlank(data.entityId)

    internal fun normalizePage(url: String, response: MessageResponse): MessageResponse {
        val rows = response.data
        val data = when (url) {
            URL -> rows.orEmpty().filter { isReplyType(it.noteType) }.map(::toMessage)
            LIKE_URL -> rows.orEmpty().map(::toLikeMessage)
            else -> rows
        }
        // The last raw row may be filtered out of the visible category.
        // Keep its cursor instead of taking one from the normalized display rows.
        return response.copy(data = data, rawPageSize = rows?.size ?: -1,
            rawLastItem = rows?.lastOrNull()?.let(::cursorOf))
    }

    /** 只用于 [toMessage] 的补字段往返，线程安全，共用一个实例 */
    private val gson = Gson()

    /**
     * 把 V18 条目归一化成老接口那套渲染模型（[MessageResponse.Data]），
     * 这样通知卡片、未读账本、点击跳转全都不用改。
     *
     * 三处要对齐：
     * - 发送者：V18 叫 `from_uid`，老模型认 `fromuid`；
     * - 分类：V18 叫 `note_type`，老模型认 `type`（黑名单、小秘书判定都用它）；
     * - 正文：V18 把「分类标题 / 正文 / 跳转地址」拆成三个字段，老接口是揉在一段 HTML 里的。
     *   而 `item_message_item.xml` 直接把 `note` 塞给 LinkTextView 渲染，点卡片时
     *   [com.example.c001apk.adapter.ItemListener.onMessClicked] 还要用 Jsoup 从里面的
     *   `<a href>` 抠跳转目标，所以这里按老接口的形状拼回去。
     *
     * 正文先 [TextUtils.htmlEncode] 再放进锚点：回复内容里的 `<` / `&` 否则会把 HTML 搅坏
     * （渲染侧 `SpannableStringBuilderUtil` 走的是 `Html.fromHtml`，转义过的实体会正常还原）。
     *
     * **补字段走 Gson 往返而不是 `data.copy()`**：Gson 反射填值不走 Kotlin 构造器，
     * V18 没下发的那些键（`message` / `fromuid` / `title` / `likenum` …）在这份模型里是
     * 非空类型却留着 null，`copy()` 生成的 null 校验会当场抛
     * `NullPointerException: Parameter specified as non-null is null（parameter message）`
     * ——2026-10-06 在 emulator-5554 上实测整页「加载失败」就是这个。JSON 往返对 null
     * 是无感的：写出去时 null 原样保留，读回来还是 null，不会触发任何校验。
     */
    fun toMessage(data: MessageResponse.Data): MessageResponse.Data {
        val text = data.note.orEmpty()
        val title = data.noteTypeTitle.orEmpty()
        val url = data.url.orEmpty()
        val note = when {
            text.isEmpty() -> title
            url.isEmpty() -> if (title.isEmpty()) text else "$title：$text"
            else -> {
                val link = "<a href=\"$url\">${TextUtils.htmlEncode(text)}</a>"
                if (title.isEmpty()) link else "${TextUtils.htmlEncode(title)}：$link"
            }
        }
        val json = gson.toJsonTree(data).asJsonObject
        json.addProperty("id", cursorOf(data).orEmpty())
        json.addProperty("fromuid", data.fromUid.orEmpty())
        json.addProperty("type", data.noteType)
        json.addProperty("note", note)
        return gson.fromJson(json, MessageResponse.Data::class.java)
    }

    /**
     * 把 likeList 条目归一化成渲染模型。
     *
     * 这一页要的是「谁给我点的赞」，所以把 `from_*` 搬进老模型里点赞人那组字段
     * （`likeUid` / `likeUsername` / `likeAvatar` / `likeTime`），并且把 `username` / `userAvatar`
     * 也一起指过去 —— 老接口这两组是分开的（`username` 是被赞动态的作者、`likeUsername`
     * 才是点赞人），渲染侧只要漏掉一处，显示的就是动态作者，也就是自己。
     *
     * `id` 必须补：这份模型里它是非空 String，而 V18 条目根本没有这个键，只有 `entityId`
     * （服务端主键，形如 `feed-74184314-1585363`，列表 diff 和未读账本都拿它当 key）。
     * Gson 反射填进来的是 null，谁先调 `data.id.isBlank()` 谁就是 NPE。
     */
    fun toLikeMessage(data: MessageResponse.Data): MessageResponse.Data {
        val fromUid = data.fromUid.orEmpty()
        val fromName = data.fromUsername.orEmpty()
        val avatar = data.fromUserAvatar.orEmpty()
        val json = gson.toJsonTree(data).asJsonObject
        json.addProperty("id", data.entityId?.takeIf { it.isNotBlank() } ?: cursorOf(data).orEmpty())
        json.addProperty("fromuid", fromUid)
        json.addProperty("type", data.noteType)
        json.addProperty("likeUid", fromUid)
        json.addProperty("likeUsername", fromName)
        json.addProperty("likeAvatar", avatar)
        json.addProperty("likeTime", data.dateline)
        json.addProperty("username", fromName)
        json.addProperty("userAvatar", avatar)
        // 黑名单是按 uid 静默的，这一页该静默的是点赞人（老接口这里给的是动态作者）
        json.addProperty("uid", fromUid)
        // 被赞的不一定是动态：`reply_like`（赞了你的评论）下发的 target_type 是
        // `feed_reply`、target_id 是**评论 id**，照着 target_id 去开动态只会得到
        // 「文章已被删除」（评论 id 不是动态 id）。条目自带的 url 才是权威入口，
        // 实测两种形态：
        //   赞了你的动态 -> /feed/74184314        （target_id = 动态 id，两者一致）
        //   赞了你的评论 -> /feed/73361859?rid=610334792
        // 所以动态 id 一律取 url 里的 /feed/<数字>，评论 id 取 rid=<数字> ——
        // 后者交给详情页去滚动定位（[MessageResponse.Data.rid]），没有就退回 target_id。
        val url = data.url.orEmpty()
        val feedId = Regex("/feed/(\\d+)").find(url)?.groupValues?.get(1)
        json.addProperty("fid", feedId ?: data.targetId?.toString().orEmpty())
        Regex("[?&]rid=(\\d+)").find(url)?.groupValues?.get(1)?.let {
            json.addProperty("rid", it.toLong())
        }
        json.addProperty("pic", data.additionInfo)
        return gson.fromJson(json, MessageResponse.Data::class.java)
    }
}
