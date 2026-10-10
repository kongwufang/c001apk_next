package com.example.c001apk.ui.base

import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.constant.Constants
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.HistoryFavoriteRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.Event
import com.example.c001apk.util.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 版本历史条目下载：直链取回后由 [BaseAppFragment] 统一弹下载框 */
data class ApkDownloadInfo(
    val url: String,
    val fileName: String,
    val title: String,
    val size: String,
)

abstract class BaseAppViewModel(
    val blackListRepo: BlackListRepo,
    val historyRepo: HistoryFavoriteRepo,
    val networkRepo: NetworkRepo
) : BaseViewModel() {

    val dataList = MutableLiveData<List<HomeFeedResponse.Data>>()

    /**
     * 下载接口 /v6/apk/download 需要「数字 ID + 包名 + 版本号」三件套。
     * 版本历史条目自带包名与版本号，数字 ID 只能由子类按区块提供。
     */
    protected open val appIdForDownload: String? get() = null
    protected open val packageNameForDownload: String? get() = null

    /**
     * 列表里下发的 feedCover 是否也按完整动态卡渲染，默认 false。
     * 「图文」tab 那类页面会在同一列表中混下发 feedCover 与 feed，而官方那一页与「动态」排法一致。
     * 由 [BaseAppFragment] 转交给 AppAdapter.feedCoverAsFeed。
     */
    open val feedCoverAsFeed: Boolean get() = false

    val apkDownload = MutableLiveData<Event<ApkDownloadInfo>>()

    val footerState = MutableLiveData<FooterState>()
    val toastText = MutableLiveData<Event<String?>>()

    // 需要弹出「修改可见性」选择框的动态 id
    val publishStatusEvent = MutableLiveData<Event<String>>()

    open fun showCollection(id: String, title: String) {}

    inner class ItemClickListener : ItemListener {
        override fun onShowCollection(id: String, title: String) {
            showCollection(id, title)
        }

        override fun onDownloadVersion(
            view: View,
            packageName: String?,
            versionCode: Long?,
            versionName: String?,
            size: String?
        ) {
            onGetVersionDownloadLink(packageName, versionCode, versionName, size)
        }

        override fun onViewFeed(
            view: View,
            id: String?,
            uid: String?,
            username: String?,
            userAvatar: String?,
            deviceTitle: String?,
            message: String?,
            dateline: String?,
            rid: Any?,
            isViewReply: Any?,
            feedData: Any?
        ) {
            super.onViewFeed(
                view,
                id,
                uid,
                username,
                userAvatar,
                deviceTitle,
                message,
                dateline,
                rid,
                isViewReply,
                feedData
            )
            viewModelScope.launch(Dispatchers.IO) {
                if (!uid.isNullOrEmpty() && PrefManager.isRecordHistory)
                    historyRepo.saveHistory(
                        id.toString(), uid.toString(), username.toString(), userAvatar.toString(),
                        deviceTitle.toString(), message.toString(), dateline.toString()
                    )
            }
        }

        override fun onFollowUser(uid: String, followAuthor: Int) {
            if (PrefManager.isLogin) {
                val url = if (followAuthor == 1) "/v6/user/unfollow" else "/v6/user/follow"
                onPostFollowUnFollow(url, uid, followAuthor)
            }
        }

        override fun onLikeClick(type: String, id: String, isLike: Int) {
            if (PrefManager.isLogin) {
                if (type == "feed")
                    onPostLikeFeed(id, isLike)
                else
                    onPostLikeReply(id, isLike)
            }
        }

        override fun onBlockUser(id: String, uid: String, position: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                blackListRepo.saveUid(uid)
            }
            val currentList = dataList.value?.toMutableList() ?: ArrayList()
            currentList.removeAt(position)
            dataList.postValue(currentList)
        }

        override fun onDeleteClicked(entityType: String, id: String, position: Int) {
            val url = if (entityType == "feed") "/v6/feed/deleteFeed"
            else "/v6/feed/deleteReply"
            onDeleteFeed(url, id, position)
        }

        override fun onChangePublishStatus(id: String, position: Int) {
            publishStatusEvent.postValue(Event(id))
        }

        override fun onChangeStickTop(id: String, isStickTop: Boolean, position: Int) {
            onPostStickTop(id, isStickTop)
        }
    }

    /**
     * 版本历史条目下载：拿该版本的直链，成功后抛 [apkDownload] 事件让界面弹下载框。
     *
     * 条目自带 packageName，个别情况下为空则退回子类给的包名；
     * 数字 ID 只有子类知道（版本历史用的是应用数字 ID）。
     */
    fun onGetVersionDownloadLink(
        packageName: String?,
        versionCode: Long?,
        versionName: String?,
        size: String?,
    ) {
        val pn = packageName?.takeIf { it.isNotEmpty() } ?: packageNameForDownload
        val aid = appIdForDownload
        if (pn.isNullOrEmpty() || aid.isNullOrEmpty() || versionCode == null) {
            toastText.postValue(Event("该版本没有可下载的安装包"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getAppDownloadLink(pn, aid, versionCode.toString())
                .collect { result ->
                    val link = result.getOrNull()
                    if (!link.isNullOrEmpty()) {
                        apkDownload.postValue(
                            Event(
                                ApkDownloadInfo(
                                    url = link,
                                    fileName = "${versionName.orEmpty()}.apk".replace('/', '_'),
                                    title = versionName.orEmpty(),
                                    size = size.orEmpty(),
                                )
                            )
                        )
                    } else {
                        toastText.postValue(Event("下载链接获取失败"))
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    // 取列表里该动态当前的可见性（1 = 仅自己可见，0 = 公开，null = 列表没带这个字段）
    fun publishStatusOf(id: String): Int? =
        dataList.value?.firstOrNull { it.id == id }?.publishStatus

    // 修改动态可见性：publishStatus 1 = 仅自己可见，0 = 公开
    fun onPostPublishStatus(id: String, publishStatus: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = HashMap<String, String?>()
            data["id"] = id
            data["publish_status"] = publishStatus.toString()
            networkRepo.postPublishStatus(data)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.data == "设置成功") {
                            dataList.value?.let { list ->
                                dataList.postValue(
                                    list.map {
                                        if (it.id == id) it.copy(publishStatus = publishStatus)
                                        else it
                                    }
                                )
                            }
                            toastText.postValue(
                                Event(if (publishStatus == 1) "已设为仅自己可见" else "已设为公开")
                            )
                        } else if (!response.message.isNullOrEmpty()) {
                            response.message.let {
                                toastText.postValue(Event(it))
                            }
                        }
                    } else {
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    // 个人主页置顶 / 取消置顶：isStickTop = 该条当前是否已置顶
    // nodeType=member + nodeId=自己的 uid，只能操作自己的动态
    fun onPostStickTop(id: String, isStickTop: Boolean) {
        // 菜单项本来就只在「自己的动态」上出现，没登录时 uid 为空不会命中
        if (!PrefManager.isLogin || PrefManager.uid.isEmpty()) return
        val myUid = PrefManager.uid
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                if (isStickTop) networkRepo.cancelTopFromNode("member", myUid, id)
                else networkRepo.addTopToNode("member", myUid, id)
            result.collect { r ->
                val response = r.getOrNull()
                if (response != null) {
                    if (!response.message.isNullOrEmpty()) {
                        toastText.postValue(Event(response.message))
                    } else {
                        // 本地先切换小标记并让置顶项排到最前，和主页的服务端顺序一致
                        dataList.value?.let { list ->
                            val updated = list.map {
                                if (it.id == id) it.copy(isStickTop = if (isStickTop) 0 else 1)
                                else it
                            }
                            val target = updated.firstOrNull { it.id == id }
                            dataList.postValue(
                                if (!isStickTop && target != null)
                                    listOf(target) + updated.filter { it.id != id }
                                else updated
                            )
                        }
                        toastText.postValue(
                            Event(response.data ?: if (isStickTop) "已取消置顶" else "置顶成功")
                        )
                    }
                } else {
                    r.exceptionOrNull()?.printStackTrace()
                }
            }
        }
    }

    fun onDeleteFeed(url: String, id: String, position: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postDelete(url, id)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.data == "删除成功") {
                            toastText.postValue(Event("删除成功"))
                            val updateList = dataList.value?.toMutableList() ?: ArrayList()
                            updateList.removeAt(position)
                            dataList.postValue(updateList)
                        } else if (!response.message.isNullOrEmpty()) {
                            response.message.let {
                                toastText.postValue(Event(it))
                            }
                        }
                    } else {
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    // like reply
    fun onPostLikeReply(id: String, isLike: Int) {
        val likeType = if (isLike == 1) "unLikeReply" else "likeReply"
        val likeUrl = "/v6/feed/$likeType"
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postLikeReply(likeUrl, id)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.data != null) {
                            val currentList = dataList.value?.map {
                                if (it.id == id) {
                                    it.copy(
                                        likenum = response.data,
                                        userAction = it.userAction?.copy(like = if (isLike == 1) 0 else 1)
                                    )
                                } else it
                            } ?: emptyList()
                            dataList.postValue(currentList)
                        } else {
                            response.message?.let {
                                toastText.postValue(Event(it))
                            }
                        }
                    } else {
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    // like feed
    fun onPostLikeFeed(id: String, isLike: Int) {
        val likeType = if (isLike == 1) "unlike" else "like"
        val likeUrl = "/v6/feed/$likeType"
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postLikeFeed(likeUrl, id)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.data != null) {
                            val currentList = dataList.value?.map {
                                if (it.id == id) {
                                    it.copy(
                                        likenum = response.data.count,
                                        userAction = it.userAction?.copy(like = if (isLike == 1) 0 else 1)
                                    )
                                } else it
                            } ?: emptyList()
                            dataList.postValue(currentList)
                        } else {
                            response.message?.let {
                                toastText.postValue(Event(it))
                            }
                        }
                    } else {
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    // follow user
    fun onPostFollowUnFollow(url: String, uid: String, followAuthor: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postFollowUnFollow(url, uid)
                .collect { result ->
                    val response = result.getOrNull()
                    if (response != null) {
                        if (response.message != null) {
                            toastText.postValue(Event(response.message))
                        } else {
                            val isFollow = if (followAuthor == 1) 0 else 1
                            val currentList = dataList.value?.toMutableList()?.map {
                                if (it.uid == uid)
                                    it.copy(isFollow = isFollow)
                                else it
                            } ?: emptyList()
                            dataList.postValue(currentList)
                            toastText.postValue(
                                Event(
                                    if (isFollow == 1) "关注成功"
                                    else "取消关注成功"
                                )
                            )
                        }
                    } else {
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

    fun saveTopic(title: String) {
        viewModelScope.launch {
            blackListRepo.saveTopic(title)
        }
    }

    fun deleteTopic(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            blackListRepo.deleteTopic(title)
        }
    }

}
