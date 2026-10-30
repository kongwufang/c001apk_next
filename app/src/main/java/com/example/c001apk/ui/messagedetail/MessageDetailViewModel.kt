package com.example.c001apk.ui.messagedetail

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.model.OSSUploadPrepareModel
import com.example.c001apk.logic.model.OSSUploadPrepareResponse
import com.example.c001apk.logic.model.StringEntity
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.logic.repository.RecentEmojiRepo
import com.example.c001apk.util.Event
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.MessageKit
import com.example.c001apk.util.PrefManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 私信聊天页。
 *
 * 会话有两种进入方式：
 * 1. 从会话列表点进来：已经有 ukey，直接拉历史；
 * 2. 从别人主页点「私信」进来：只有对方的 uid，**还没有会话**，此时列表是空的，
 *    发出第一条消息时服务端才隐式建立会话（`/v6/message/send?uid=`），发完再回头把 ukey 认领回来。
 *
 * ## 乐观发送（2026-10-30 改）
 *
 * 以前是「服务端确认后才上屏」✗ 点发送到看见自己那条要等一次网络往返
 * （实测 0.3~1s，弱网更久），体感像卡住 ✗
 *
 * 现在：点发送 ⇒ **立刻**插入一条 [ChatItem.STATE_SENDING] 的本地消息 ✓
 * 服务端成功 ⇒ 变 [ChatItem.STATE_SENT]（转圈消失）✓
 * 失败 ⇒ 变 [ChatItem.STATE_FAILED]（红色感叹号，点一下问是否重发）✓
 *
 * 关键细节：
 * - 本地消息的主键 [ChatItem.localId] **永远不变**，否则 DiffUtil 会当成删除+新增 ⇒ 闪一下 ✗
 * - 成功后把服务端 id 记进 [claimedIds] ⇒ 后续 `loadHistory` / 轮询不会再把同一条插一遍 ✓
 * - `loadHistory` 只覆盖「已确认」部分，**本地未确认的（发送中/失败）永远追加在末尾** ✓
 */
@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val networkRepo: NetworkRepo,
    private val recentEmojiRepo: RecentEmojiRepo,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** 列表项 = 消息 + 发送状态（见 [ChatItem]） */
    val chatData = MutableLiveData<List<ChatItem>>()

    val toastText = MutableLiveData<Event<String>>()

    /**
     * 内部可变副本。所有增删改都在这里做，改完 [publish] 一次性抛给 UI ✓
     * （直接对 LiveData 反复 `value = value!!.toMutableList().also{...}` 会丢更新）
     */
    private val items = mutableListOf<ChatItem>()

    /**
     * 已被本地消息「认领」的服务端消息 id。
     * 发送成功后服务端会返回真实消息，`loadHistory` 再拉一遍就会重复 ✗
     * ⇒ 拉回来时把这些 id 过滤掉 ✓
     */
    private val claimedIds = HashSet<String>()

    /** 当前登录 uid（本地乐观消息的 fromuid 必须是它，否则气泡跑到左边 ✗） */
    private val myUid: String get() = PrefManager.uid

    /** 表情面板「最近」页的数据源，跟回复页共用同一张表 */
    val recentEmojiLiveData: LiveData<List<StringEntity>> = recentEmojiRepo.loadAllListLive()

    /** 首次进来「最近」是空的，面板要自动落到「默认」页（只做一次） */
    var isEmojiInit = true

    /** 表情用过就记一笔，下次排在「最近」里（跟回复页同一套逻辑，表最多留 27 条） */
    fun updateRecentEmoji(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (recentEmojiRepo.checkEmoji(data)) {
                recentEmojiRepo.updateEmoji(data, System.currentTimeMillis())
            } else {
                if (recentEmojiLiveData.value?.size == 27)
                    recentEmojiLiveData.value?.last()?.data?.let {
                        recentEmojiRepo.updateEmoji(it, data, System.currentTimeMillis())
                    }
                else
                    recentEmojiRepo.insertEmoji(StringEntity(data))
            }
        }
    }

    /** 调试用：长按「最近」清空记录（跟回复页一致，只在 debug 包挂上去） */
    fun deleteAllEmoji() {
        viewModelScope.launch(Dispatchers.IO) {
            recentEmojiRepo.deleteAll()
        }
    }

    /** OSS 上传准备就绪：带上 STS 凭证和**服务端分配的对象名**，交给页面去直传 */
    val uploadImage = MutableLiveData<Event<OSSUploadPrepareResponse.Data>>()

    /**
     * `showImage` 换来的签名地址缓存。auth_key 有效期约半小时，一次会话够用；
     * 列表来回滚动会反复 bind，不缓存的话每滚一次都要重新请求一遍。
     */
    private val picUrlCache = HashMap<String, MessageKit.MessagePic>()

    var ukey = ""
    var uid = ""

    // ---------------------------------------------------------------- 列表发布

    private fun publish() {
        chatData.postValue(items.toList())
    }

    /** 列表里当前所有「未确认」的项（发送中 / 失败），`loadHistory` 要保住它们 */
    private fun pendingItems(): List<ChatItem> = items.filter { it.isPending }

    // ---------------------------------------------------------------- 加载历史

    /** 拉当前会话的历史记录。没有 ukey（还没说过话）时直接给空列表 */
    fun loadHistory() {
        if (ukey.isEmpty()) {
            // 还没建会话：列表里只可能有「没发出去的本地消息」，保住它们 ✓
            // ⚠️ 必须先取再 clear —— 反过来取到的是空列表 ✗
            val pending = pendingItems()
            items.clear()
            items.addAll(pending)
            publish()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getChatHistory(ukey, 1).collect { result ->
                val feed = result.getOrNull()
                if (feed == null) {
                    toastText.postValue(Event("加载聊天记录失败"))
                    return@collect
                }
                if (!feed.message.isNullOrEmpty()) {
                    toastText.postValue(Event(feed.message.toString()))
                    return@collect
                }
                // 服务端返回的顺序不保证，按时间升序排一遍（跟桌面版一致），最后滚到底就是最新一条
                // ⚠️ `id` 可能是 null —— `/v6/message/chat` 里的 messageEmpty 条目就是
                //   `"id": null` ✗（模型声明成非空 String，Gson 照样塞 null 进去 ✓）
                //   写 `it.id.isBlank()` 会 NPE：StringsKt → CharSequence.length() ✗
                //   ⇒ 一律走 `.orEmpty()` 兜住 ✓
                val server = feed.data.orEmpty()
                    .filter { it.id.orEmpty().isBlank() || it.id.orEmpty() !in claimedIds }
                    .sortedBy { it.dateline }
                    // 统一归一化：Gson 会把这些**声明成非空**的 String 字段填成 null ✗
                    // （`messageEmpty` 条目没有 message / id，`messageExt` 可能没有 fromuid）
                    // ⇒ 渲染层（LinkTextView / 气泡判定）都会撞 null ✗ 这里一次性兜住 ✓
                    .map { ChatItem(it.normalized()) }
                val pending = pendingItems()
                items.clear()
                items.addAll(server)
                items.addAll(pending)      // 未确认的永远在末尾 ✓
                publish()
            }
        }
    }

    // ---------------------------------------------------------------- 发送

    /**
     * 发文字。**立刻上屏**（转圈），再走网络。
     * 服务端返回失败也只是把那条标成「发送失败」，不丢内容 ✓
     */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        enqueue(ChatItem.local(myUid = myUid, text = content))
    }

    /**
     * 图片走跟发动态同一套 OSS 直传：先 `ossUploadPrepare` 拿 STS 凭证和**服务端分配的对象名**，
     * 传完 OSS 再把这个对象名当 `message_pic` 发出去（见 [sendImageMessage]）。
     *
     * 与发动态的差别只有三个参数（2026-10-06 抓包核对）：bucket / dir 都是 `message`
     * ——图片落在私信专用桶 `coolapk-oss-message`，域名也是另一个；
     * `toUid` 必须填**对方** uid（动态那边传空串）。
     */
    fun prepareImageUpload(image: OSSUploadPrepareModel) {
        if (uid.isEmpty()) return
        val data = hashMapOf(
            "uploadBucket" to "message",
            "uploadDir" to "message",
            "is_anonymous" to "0",
            "uploadFileList" to Gson().toJson(listOf(image)),
            "toUid" to uid,
        )
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.postOSSUploadPrepare(data).collect { result ->
                val feed = result.getOrNull()
                if (feed == null) {
                    toastText.postValue(Event("图片上传准备失败"))
                    return@collect
                }
                if (!feed.message.isNullOrEmpty()) {
                    toastText.postValue(Event(feed.message.toString()))
                    return@collect
                }
                val prepared = feed.data
                if (prepared == null || prepared.fileInfo.isEmpty()) {
                    toastText.postValue(Event("图片上传准备失败"))
                    return@collect
                }
                uploadImage.postValue(Event(prepared))
            }
        }
    }

    /**
     * 图片已经传到 OSS，把服务端给的 `uploadFileName` 发出去。
     * `message_pic` 要**带前导斜杠**：prepare 返回的是 `message/2026/1006/xxx.png`，
     * 抓包里发出去的是 `/message/2026/1006/xxx.png`。
     *
     * @param localUri 相册里的本地 uri（`content://…`）—— 只用于**立刻显示缩略图** ✓
     */
    fun sendImageMessage(uploadFileName: String, localUri: String = "") {
        if (uploadFileName.isEmpty()) return
        val apiPic = "/$uploadFileName"
        enqueue(
            ChatItem.local(
                myUid = myUid,
                text = "",
                localPic = localUri,
                apiPic = apiPic
            )
        )
    }

    /** 把一条本地消息塞进列表（转圈）并开始发送 */
    private fun enqueue(local: ChatItem) {
        if (uid.isEmpty()) {
            toastText.postValue(Event("找不到对方用户"))
            return
        }
        items.add(local)
        publish()
        doSend(local)
    }

    /** 真正的网络发送。成功/失败都只更新那一项的状态，**不重建列表** ✓ */
    private fun doSend(local: ChatItem) {
        val toUid = uid
        if (toUid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.sendMessage(toUid, local.data.message, local.apiPic).collect { result ->
                val idx = items.indexOfFirst { it.localId != null && it.localId == local.localId }
                if (idx < 0) return@collect          // 用户已经退页面/列表被清空
                val feed = result.getOrNull()
                val ok = feed != null && feed.message.isNullOrEmpty()

                if (ok) {
                    // 服务端可能在 data 里回真实消息（官方签名是 Result<List<Entity>>），
                    // 有就采用它的 id/dateline；没有（data 为空或形状不对）也不影响：
                    // 本地那条直接标成已发送，后续轮询按 claimedIds 去重 ✓
                    val confirmed = feed?.data.orEmpty().lastOrNull { it.id.orEmpty().isNotBlank() }
                    if (confirmed != null) {
                        claimedIds.add(confirmed.id.orEmpty())
                        items[idx] = items[idx].withData(
                            items[idx].data.copy(
                                id = confirmed.id.orEmpty(),
                                entityId = confirmed.entityId ?: confirmed.id.orEmpty(),
                                dateline = confirmed.dateline.takeIf { it > 0 }
                                    ?: items[idx].data.dateline
                            )
                        )
                    }
                    items[idx] = items[idx].withState(ChatItem.STATE_SENT)
                    // 第一条消息发出去后服务端才建会话，这时 ukey 还是空的，回头认领一次
                    if (ukey.isEmpty()) ukey = findUkeyByUid()
                } else {
                    items[idx] = items[idx].withState(ChatItem.STATE_FAILED)
                    feed?.message?.takeIf { it.isNotEmpty() }
                        ?.let { toastText.postValue(Event(it)) }
                }
                publish()
            }
        }
    }

    /**
     * 点红色感叹号 ⇒ 重发同一条。内容不丢（还在列表里 ✓），只是状态回到「发送中」。
     */
    fun retrySend(item: ChatItem) {
        val idx = items.indexOfFirst { it.localId != null && it.localId == item.localId }
        if (idx < 0) return
        if (!items[idx].isFailed) return
        items[idx] = items[idx].withState(ChatItem.STATE_SENDING)
        publish()
        doSend(items[idx])
    }

    /** 把失败的本地消息删掉（弹窗里选「删除」时用） */
    fun discardFailed(item: ChatItem) {
        val idx = items.indexOfFirst { it.localId != null && it.localId == item.localId }
        if (idx < 0) return
        if (!items[idx].isFailed) return
        items.removeAt(idx)
        publish()
    }

    // ---------------------------------------------------------------- 图片

    /**
     * 气泡里的图片地址。`message_pic` 只是 OSS 对象名，CDN 裸地址会被 auth_key 挡，
     * 得先问 `showImage` 拿 302 出来的签名地址。结果按消息 id 缓存，见 [picUrlCache]。
     *
     * 顺便量一下图片原始宽高（跟地址一起缓存）：气泡要按真实比例撑开，
     * 不然横图竖图都按同一个占位尺寸显示，图片回来时列表会跳一下。
     */
    fun loadMessagePic(id: String, onReady: (MessageKit.MessagePic?) -> Unit) {
        picUrlCache[id]?.let {
            onReady(it)
            return
        }
        if (id.isEmpty()) {
            onReady(null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val url = networkRepo.getMessagePicUrl(id).first().getOrNull()
            if (url.isNullOrEmpty()) {
                withContext(Dispatchers.Main) { onReady(null) }
                return@launch
            }
            val size = ImageUtil.loadImageSize(appContext, url)
            val pic = MessageKit.MessagePic(url, size?.first ?: 0, size?.second ?: 0)
            picUrlCache[id] = pic
            withContext(Dispatchers.Main) { onReady(pic) }
        }
    }

    /** 把会话标记为已读（新会话没有 ukey，不用调） */
    fun markRead() {
        if (ukey.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.readMessage(ukey).collect { }
        }
    }

    /**
     * 刚发出第一条消息时还没拿到 ukey，回会话列表里按对方 uid 认领。
     * 服务端建会话有延迟，列表可能还没刷出来，所以重试几次。
     */
    private suspend fun findUkeyByUid(): String {
        repeat(3) { attempt ->
            if (attempt > 0) delay(600)
            val result = networkRepo.getMessage("/v6/message/list", 1, null).first()
            val data = result.getOrNull()?.data.orEmpty()
            val ukey = data.firstOrNull { MessageKit.partnerUid(it) == uid }?.ukey.orEmpty()
            if (ukey.isNotEmpty()) return ukey
        }
        return ""
    }

}

/**
 * 把服务端下发的 [MessageResponse.Data] 归一化成「所有文本字段都非 null」。
 *
 * 为什么需要：模型里这些字段声明成非空 `String` ✗ 但 **Gson 反射填值不走构造器** ✓
 * 服务端没下发（或显式给 null）时，运行时就是 null ✗
 * 实测 `/v6/message/chat` 的 `messageEmpty` 条目 `id` / `message` 都是 null ✓
 * ⇒ 直接拿去渲染会 NPE（`CharSequence.length()` on null）✗
 */
private fun MessageResponse.Data.normalized(): MessageResponse.Data = copy(
    id = id.orEmpty(),
    entityType = entityType.orEmpty(),
    message = message.orEmpty(),
    fromuid = fromuid.orEmpty(),
    uid = uid.orEmpty(),
    title = title.orEmpty(),
    username = username.orEmpty(),
    fromusername = fromusername.orEmpty(),
    fromUserAvatar = fromUserAvatar.orEmpty(),
    userAvatar = userAvatar.orEmpty(),
    entityId = entityId?.takeIf { it.isNotBlank() },
    messagePic = messagePic?.takeIf { it.isNotBlank() }
)
