package com.example.c001apk.logic.repository

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.c001apk.R
import com.example.c001apk.logic.dao.OfflineCacheDao
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.OfflineFeedEntity
import com.example.c001apk.logic.model.OfflineFeedSummary
import com.example.c001apk.logic.model.TotalReplyResponse
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.LOCAL_IMAGE_SCHEME
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 离线缓存：把动态 / 图文连图带评论存到本机，断网（甚至原帖被删）也能看。
 *
 * 存下来的是**详情接口返回的原样 JSON**，只把里面的图片 URL 递归换成本地路径，
 * 所以详情页不用为「离线」再写一套渲染 —— 把存档解回 `HomeFeedResponse.Data`，
 * 走的是和在线页面完全同一条渲染路径（见 `FeedViewModel.offline`）。
 *
 * 一次存的内容：帖子文稿 + 全部图片 + 置顶评论 + 前 [REPLY_LIMIT] 条评论。
 */
@Singleton
class OfflineCacheRepo @Inject constructor(
    private val offlineCacheDao: OfflineCacheDao,
    private val networkRepo: NetworkRepo,
    @ApplicationContext private val context: Context,
    /**
     * 单帖第三方分享。
     *
     * 用它做「缓存即上云」：官方常删帖，本地缓存只在这台手机上 ✗
     * 缓存成功后顺手传一份到云（123 冷 + OSS 热）⇒ 换手机 / 清数据 / 原帖被删都能找回 ✓
     * 顺手拿到的分享链接也在里面（用户之后点分享就不用再传一遍 ✓）
     */
    private val shareLinkRepo: ShareLinkRepo,
) {

    private val gson = Gson()

    /**
     * 缓存要下几十张图，用户点完就把面板划走了，下载不能跟着入口的生命周期陪葬，
     * 所以这里自己持一个应用级作用域，结果用 Toast 反馈（面板在不在都能收到）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 正在缓存的帖子 id，操作和读取分别在主线程 / IO 线程，用同步集兜住 */
    private val saving = Collections.synchronizedSet(mutableSetOf<String>())

    /** 给界面看的快照：按钮拿它显示「缓存中…」，顺便防重复点击 */
    val savingIds = MutableLiveData<Set<String>>(emptySet())

    fun loadAllLive(): LiveData<List<OfflineFeedSummary>> = offlineCacheDao.loadAllLive()

    suspend fun get(fid: String): OfflineFeedEntity? = offlineCacheDao.get(fid)

    /**
     * 拿某个帖子的**本地已缓存图片**（按文件名排序）。
     *
     * 给「单帖第三方分享」用：缓存过的帖子分享时不用再下一次图 ✓
     * 没缓存过（或缓存里没图）⇒ 返回空列表 ✓ 那边就只发文字 ✓
     */
    suspend fun localImageFiles(fid: String): List<File> = withContext(Dispatchers.IO) {
        if (fid.isBlank()) return@withContext emptyList()
        val e = offlineCacheDao.get(fid) ?: return@withContext emptyList()
        val dir = File(e.imageDir)
        if (!dir.isDirectory) return@withContext emptyList()
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 1024 }
            ?.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun isSaving(fid: String): Boolean = fid in saving

    fun saveAsync(fid: String) {
        // add 落在同步集里，返回 false 就说明这一条正在下，直接忽略重复点击
        if (!saving.add(fid)) return
        publishSaving()
        scope.launch {
            val error = runCatching { save(fid) }
                .getOrElse { context.getString(R.string.offline_cache_failed) }
            saving.remove(fid)
            publishSaving()
            toast(error ?: context.getString(R.string.offline_cache_saved))
        }
    }

    private fun publishSaving() {
        savingIds.postValue(saving.toSet())
    }

    suspend fun delete(fid: String) = withContext(Dispatchers.IO) {
        offlineCacheDao.get(fid)?.let { File(it.imageDir).deleteRecursively() }
        offlineCacheDao.delete(fid)
        Unit
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        offlineCacheDao.loadAll().forEach { File(it.imageDir).deleteRecursively() }
        offlineCacheDao.deleteAll()
        File(context.filesDir, DIR).deleteRecursively()
        Unit
    }

    /** 存档 → 详情页要的 `HomeFeedResponse.Data`（图片已经是本地路径） */
    fun feedDataOf(entity: OfflineFeedEntity): HomeFeedResponse.Data? =
        runCatching { gson.fromJson(entity.feedJson, HomeFeedResponse.Data::class.java) }.getOrNull()

    /** 存档 → 评论列表（置顶那条不在里面，它跟着详情数据走） */
    fun replyListOf(entity: OfflineFeedEntity): List<TotalReplyResponse.Data> =
        runCatching {
            gson.fromJson<List<TotalReplyResponse.Data>>(
                entity.replyJson,
                object : TypeToken<List<TotalReplyResponse.Data>>() {}.type
            )
        }.getOrNull().orEmpty()

    /**
     * 真正干活的：拉详情 → 拉评论 → 下图 → 落库。
     * @return null 表示成功，否则是给用户看的原因。
     */
    private suspend fun save(fid: String): String? {
        val failed = context.getString(R.string.offline_cache_failed)
        val feed = networkRepo.getFeedContent(fid, null).firstOrNull()?.getOrNull()
            ?: return failed
        // 帖主可见性之类的服务端原因（原帖已删时 data 也是空的），照原话回给用户
        if (!feed.message.isNullOrBlank()) return feed.message
        val data = feed.data ?: return failed

        val feedObj = runCatching { JSONObject(gson.toJson(data)) }.getOrNull() ?: return failed

        val dir = File(File(context.filesDir, DIR), fid)
        // 重存时先清掉上次的图，免得留下再也引用不到的孤儿文件
        dir.deleteRecursively()
        dir.mkdirs()

        // 已经下过的图不重复下：一张图在正文、头像、评论里可能反复出现
        val downloaded = HashMap<String, String>()
        rewriteObject(feedObj, dir, downloaded)

        val replies = fetchReply(fid, feedObj.optString("feedType"))
        // 用 JSON 文本构造：评论是 Gson 解析出来的对象，直接往 org.json 的数组里塞
        // 要看它 wrap 猜不猜得出类型，序列化一次再解析回来稳妥
        val replyArr = JSONArray(gson.toJson(replies))
        rewriteArray(replyArr, dir, downloaded)

        val userInfo = feedObj.optJSONObject("userInfo")
        offlineCacheDao.insert(
            OfflineFeedEntity(
                fid = fid,
                uid = userInfo?.optString("uid"),
                uname = userInfo?.optString("username"),
                avatar = userInfo?.optString("userAvatar"),
                title = feedObj.optString("messageTitle").ifBlank { null },
                message = feedObj.optString("message").plainText(),
                cover = coverOf(feedObj),
                device = feedObj.optString("deviceTitle").ifBlank { null },
                dateline = feedObj.optLong("dateline"),
                replyCount = feedObj.optInt("replynum", 0),
                savedReplyCount = replyArr.length(),
                savedAt = System.currentTimeMillis(),
                imageDir = dir.absolutePath,
                feedJson = feedObj.toString(),
                replyJson = replyArr.toString()
            )
        )
        // ④ 缓存成功 ⇒ **顺手传一份到云**（防止官方删帖后彻底丢失 ✗）
        //   静默执行：失败只记日志，不打扰用户（本地缓存本身是好的 ✓）
        autoCloud(fid)
        return null
    }

    /**
     * 缓存成功后的「上云」。
     *
     * 从数据库把刚存好的实体读回来渲染成单帖 HTML，连同本地图片一起上传 ✓
     * 失败不影响本地缓存 ✓（下次用户手动点分享会再试一次）
     */
    private fun autoCloud(fid: String) {
        scope.launch {
            runCatching {
                val e = offlineCacheDao.get(fid) ?: return@runCatching
                val data = feedDataOf(e) ?: return@runCatching
                val imgs = localImageFiles(fid)
                val r = shareLinkRepo.createShare(data, imgs)
                r.onSuccess { android.util.Log.i("OfflineCache", "上云成功 $fid -> ${it.url}") }
                    .onFailure { android.util.Log.w("OfflineCache", "上云失败 $fid: ${it.message}") }
            }.onFailure {
                android.util.Log.w("OfflineCache", "上云异常 $fid: ${it.message}")
            }
        }
    }

    /** 置顶评论跟着详情数据走（在 `topReplyRows` 里），这里只取前 [REPLY_LIMIT] 条普通评论 */
    private suspend fun fetchReply(fid: String, feedType: String?): List<TotalReplyResponse.Data> =
        networkRepo.getFeedContentReply(
            fid,
            "lastupdate_desc",
            1,
            null,
            null,
            1,
            feedType.orEmpty(),
            0,
            0
        ).firstOrNull()?.getOrNull()?.data.orEmpty().take(REPLY_LIMIT)

    private fun coverOf(feedObj: JSONObject): String? = listOf(
        feedObj.optString("messageCover"),
        feedObj.optString("pic"),
        feedObj.optJSONArray("picArr")?.optString(0).orEmpty()
    ).firstOrNull { it.isNotBlank() }

    // ------------------------------------------------------------ 图片落地

    /** 递归改写：对象 / 数组就地改，字符串如果有新值就返回给上层写回 */
    private suspend fun rewriteNode(value: Any?, dir: File, done: MutableMap<String, String>): Any? =
        when {
            value is String -> when {
                IMAGE_URL.matches(value) -> done.getOrPut(value) {
                    localImage(value, dir) ?: value
                }
                // 「JSON 装在字符串里」的字段：图文的 messageRawOutput 就是这类，
                // 而且它是**数组**（`[{...},{...}]`），正文里的图全在这里面，得钻进去改
                value.trimStart().startsWith("[") -> jsonInString(value) { s ->
                    runCatching {
                        JSONArray(s).also { rewriteArray(it, dir, done) }.toString()
                    }.getOrNull()
                }
                value.trimStart().startsWith("{") -> jsonInString(value) { s ->
                    runCatching {
                        JSONObject(s).also { rewriteObject(it, dir, done) }.toString()
                    }.getOrNull()
                }
                else -> null
            }
            value is JSONObject -> {
                rewriteObject(value, dir, done)
                null
            }
            value is JSONArray -> {
                rewriteArray(value, dir, done)
                null
            }
            else -> null
        }

    private suspend fun rewriteObject(obj: JSONObject, dir: File, done: MutableMap<String, String>) {
        obj.keys().forEach { key ->
            rewriteNode(obj.opt(key), dir, done)?.let { obj.put(key, it) }
        }
    }

    private suspend fun rewriteArray(arr: JSONArray, dir: File, done: MutableMap<String, String>) {
        for (i in 0 until arr.length()) {
            rewriteNode(arr.opt(i), dir, done)?.let { arr.put(i, it) }
        }
    }

    /**
     * 把字符串里装着的 JSON 解出来改写，返回新串。
     *
     * 只有真的改到了内容才回写：`extraData` 那种 `{"avgData":"6.9"}` 的小对象解析一遍也会
     * 拼回同样的串，返回 null 让上层保留原值，不必给每个字符串都白白重排一次。
     */
    private suspend fun jsonInString(
        raw: String,
        rewrite: suspend (String) -> String?
    ): String? = rewrite(raw)?.takeIf { it != raw }

    /**
     * 下到本机，返回交给渲染层的地址：`file://` + 本地绝对路径 + **原 URL 上的尺寸提示**。
     *
     * 尺寸提示必须留着：图文正文里每张图的宽高比是 `ImageUtil.getImageLp` 从地址里解出来的
     * （`@1000x1000.jpg` 这种），只留一个干净路径的话，宽图会被当成 1:1 裁成方的。
     */
    private suspend fun localImage(url: String, dir: File): String? =
        ImageUtil.downloadToFile(context, url, File(dir, imageFileName(url)))
            ?.let { LOCAL_IMAGE_SCHEME + it + urlSuffix(url) }

    /** 原 URL 挂在文件名后面的尺寸 / 处理后缀（`@1000x1000.jpg`、`?x-oss-process=...`） */
    private fun urlSuffix(url: String): String {
        val tail = url.substringAfterLast('/')
        val i = tail.indexOfFirst { it == '@' || it == '?' }
        return if (i == -1) "" else tail.substring(i)
    }

    private fun imageFileName(url: String): String {
        val path = url.substringBefore('?').substringBefore('@')
        val ext = path.substringAfterLast('.', "").lowercase()
            .filter { it.isLetterOrDigit() }.take(4).ifBlank { "jpg" }
        return "${abs(url.hashCode())}.$ext"
    }

    private fun String?.plainText(): String? = this
        ?.replace(Regex("<[^>]*>"), "")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.ifBlank { null }

    private suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val DIR = "offline_cache"

        /** 存评论的条数：置顶那条在详情数据里，不占这个额度 */
        private const val REPLY_LIMIT = 20

        /**
         * 认图片 URL 的口径：coolapk 的图后面常跟 `@1000w_1000h.webp` 这类处理串、
         * 或者 `?x-oss-process=...` 的查询串，所以扩展名后面允许再挂任意非空白字符。
         */
        private val IMAGE_URL =
            Regex("""^https?://\S+\.(jpg|jpeg|png|webp|gif|bmp|heic)\S*$""", RegexOption.IGNORE_CASE)
    }
}
