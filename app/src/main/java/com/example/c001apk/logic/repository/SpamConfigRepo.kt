package com.example.c001apk.logic.repository

import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.SpamConfig
import com.example.c001apk.util.MessageAccount
import com.example.c001apk.util.PrefManager
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * 其他屏蔽项（关键字 / 用户 / 节点）。
 *
 * 服务端读键与写键不一致：读 `spam_word_config` 时关键字在 `custom` 里，写的时候键名是 `word`
 * （用户 `user`、节点 `node` 读写一致，节点值是 `[{tid,name}]`）。
 * 云端配置按账号本地缓存，切换后不会读到上一账号的设置。
 */
@Singleton
class SpamConfigRepo @Inject constructor(
    private val networkRepo: NetworkRepo
) {

    private val empty = SpamConfig()
    private val gson = Gson()
    private var cached: Pair<MessageAccount, SpamConfig>? = null

    val current: SpamConfig get() = synchronized(PrefManager) {
        val owner = MessageAccount.current() ?: return@synchronized empty
        cached?.takeIf { it.first == owner }?.second ?: run {
            val config = runCatching { gson.fromJson(PrefManager.getCloudSpam(owner), SpamConfig::class.java) }
                .getOrNull() ?: empty
            cached = owner to config
            config
        }
    }

    suspend fun refresh(): Boolean {
        val owner = MessageAccount.current() ?: return false
        val result = networkRepo.getSpamWordList().firstOrNull()
        val raw = result?.getOrNull()?.data?.spam_word_config ?: return false
        val config = runCatching { gson.fromJson(raw, SpamConfig::class.java) }.getOrNull() ?: return false
        return synchronized(PrefManager) {
            if (!PrefManager.saveCloudSpam(owner, raw)) return@synchronized false
            cached = owner to config
            true
        }
    }

    private suspend fun update(value: String): Boolean {
        // 写接口用的是 urlencoded 表单，value 本身是 JSON 字符串
        val ok = networkRepo.updateConfig("spam_word_config", value).firstOrNull()?.isSuccess == true
        if (ok) refresh()
        return ok
    }

    suspend fun addKeyword(word: String) = update("""{"word":{"add":${Gson().toJson(word)}}}""")

    suspend fun cancelKeyword(word: String) = update("""{"word":{"cancel":${Gson().toJson(word)}}}""")

    suspend fun addUser(uid: String) = update("""{"user":{"add":${Gson().toJson(uid)}}}""")

    suspend fun cancelUser(uid: String) = update("""{"user":{"cancel":${Gson().toJson(uid)}}}""")

    suspend fun addNode(tid: String, name: String) = update(
        """{"node":{"add":[{"tid":${Gson().toJson(tid)},"name":${Gson().toJson(name)}}]}}"""
    )

    suspend fun cancelNode(tid: String, name: String) = update(
        """{"node":{"cancel":[{"tid":${Gson().toJson(tid)},"name":${Gson().toJson(name)}}]}}"""
    )

    /** 供列表同步判断：命中关键字 / 用户 / 节点任一即算屏蔽 */
    fun isSpam(data: HomeFeedResponse.Data?): Boolean {
        if (data == null) return false
        val config = current
        if (config.custom.isEmpty() && config.user.isEmpty() && config.node.isEmpty()) return false

        val text = (data.message ?: "") + "\n" + (data.messageTitle ?: "") + "\n" + (data.title ?: "")
        if (config.custom.any { !it.title.isNullOrEmpty() && text.contains(it.title) })
            return true

        val uid = data.userInfo?.uid ?: data.uid
        if (uid != null && config.user.any { it.uid == uid })
            return true

        val nodeText = listOfNotNull(
            data.tags,
            data.ttitle,
            data.relationRows?.getOrNull(0)?.title,
            data.targetRow?.title
        ).joinToString(" ")
        val nodeIds = listOfNotNull(
            data.targetRow?.id,
            data.relationRows?.getOrNull(0)?.id
        )
        return config.node.any { node ->
            (!node.title.isNullOrEmpty() && nodeText.contains(node.title)) ||
                    (!node.targetFullId.isNullOrEmpty() && nodeIds.any { it == node.targetFullId })
        }
    }

    /** 后台线程用（列表加载时逐条判断，内容多，避免占用主线程） */
    suspend fun isSpamAsync(data: HomeFeedResponse.Data?): Boolean =
        withContext(Dispatchers.Default) { isSpam(data) }
}
