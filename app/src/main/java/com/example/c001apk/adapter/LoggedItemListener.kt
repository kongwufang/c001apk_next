package com.example.c001apk.adapter

import android.view.View
import com.example.c001apk.util.AppLog
import com.example.c001apk.util.LogModule

/**
 * 点击日志装饰器（2026-11-08）
 *
 * 用户要求：**每一个卡片的每一次点击都要有日志**。
 * 一个个在 ViewHolder 里加 `setOnClickListener` 之前的日志不现实（几十处，且容易漏）✗
 * 这里把 [ItemListener] 整个包一层：
 *
 * ```
 * AppAdapter(LoggedItemListener(viewModel.ItemClickListener()))   // 由 AppAdapter 内部自动包
 * ```
 *
 * 用 Kotlin 的 `by` 委托，未覆盖的方法直接透传（零成本）；
 * 覆盖的都是「点了会跳转/会发请求」的方法，统一打 `ITEM_CLICK`：
 *
 * ```
 * I/ui/Click ITEM_CLICK what=onViewFeed id=… uid=… page=<当前请求>
 * ```
 *
 * ⚠️ onOpenLink 自己已经打过 CLICK_OPEN，这里不重复包。
 */
class LoggedItemListener(
    private val delegate: ItemListener,
) : ItemListener by delegate {

    private fun log(what: String, vararg kv: Pair<String, Any?>) {
        AppLog.i(LogModule.UI, "Click") {
            buildString {
                append("ITEM_CLICK what=").append(what)
                kv.forEach { (k, v) ->
                    val s = v?.toString().orEmpty()
                    if (s.isNotEmpty()) append(' ').append(k).append('=').append(s.take(80))
                }
                append(" page=").append(AppLog.page)
            }
        }
    }

    override fun onViewFeed(
        view: View, id: String?, uid: String?, username: String?, userAvatar: String?,
        deviceTitle: String?, message: String?, dateline: String?, rid: Any?, isViewReply: Any?,
        feedData: Any?
    ) {
        log("onViewFeed", "id" to id, "uid" to uid, "username" to username)
        delegate.onViewFeed(
            view, id, uid, username, userAvatar, deviceTitle, message, dateline, rid, isViewReply, feedData
        )
    }

    override fun onViewUser(view: View, uid: String?) {
        log("onViewUser", "uid" to uid)
        delegate.onViewUser(view, uid)
    }

    override fun onViewApk(view: View, id: String?) {
        log("onViewApk", "id" to id)
        delegate.onViewApk(view, id)
    }

    override fun onViewTopic(view: View, type: String?, title: String?, url: String?, id: String?) {
        log("onViewTopic", "type" to type, "title" to title, "url" to url, "id" to id)
        delegate.onViewTopic(view, type, title, url, id)
    }

    override fun onViewCoolPic(view: View, title: String?) {
        log("onViewCoolPic", "title" to title)
        delegate.onViewCoolPic(view, title)
    }

    override fun onLikeClick(type: String, id: String, isLike: Int) {
        log("onLikeClick", "type" to type, "id" to id, "isLike" to isLike)
        delegate.onLikeClick(type, id, isLike)
    }

    override fun onFavoriteClick(id: String?) {
        log("onFavoriteClick", "id" to id)
        delegate.onFavoriteClick(id)
    }

    override fun onShareFeed(id: String?) {
        log("onShareFeed", "id" to id)
        delegate.onShareFeed(id)
    }

    override fun onSearchSort(value: String) {
        log("onSearchSort", "value" to value)
        delegate.onSearchSort(value)
    }

        log("onFollowUser", "uid" to uid, "followAuthor" to followAuthor)
        delegate.onFollowUser(uid, followAuthor)
    }

    override fun loadImage(view: View, imageUrl: String?) {
        log("loadImage", "url" to imageUrl)
        delegate.loadImage(view, imageUrl)
    }

    override fun onMessClicked(view: View, note: String) {
        log("onMessClicked", "note" to note)
        delegate.onMessClicked(view, note)
    }

    override fun viewFFFList(view: View, uid: String?, isEnable: Boolean, type: String) {
        log("viewFFFList", "uid" to uid, "type" to type)
        delegate.viewFFFList(view, uid, isEnable, type)
    }
}
