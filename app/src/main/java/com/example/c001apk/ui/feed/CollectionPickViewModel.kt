package com.example.c001apk.ui.feed

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.CollectionAction
import com.example.c001apk.logic.model.CollectionData
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 收藏夹（多收藏夹）弹窗的 ViewModel。
 *
 * 接口关系（HAR 实测）：
 * - 列表：`GET /v6/collection/list?uid=&id=<动态id>&type=feed&showDefault=1&page=1`，`isBeCollected=1` 表示已在该夹
 * - 收藏：`POST /v6/collection/addItem`（`id=夹id`、`cancelId` 空）
 * - 取消：同一接口（`id` 空、`cancelId=夹id`）
 * - 新建：`POST /v6/collection/create`（multipart），封面先 `uploadImage`
 * - 改：`POST /v6/collection/update`（表单）
 */
@HiltViewModel
class CollectionPickViewModel @Inject constructor(
    private val networkRepo: NetworkRepo
) : ViewModel() {

    val list = MutableLiveData<List<CollectionData>>()
    val loading = MutableLiveData<Boolean>()
    val toastText = MutableLiveData<Event<String>>()

    /**
     * 一次 addItem 的净结果：服务端回的最新收藏数 + 该动态在本夹的状态。
     *
     * 多收藏夹下本地加减算不准——同一个夹重复点、别人同时收藏都会偏，所以数字只认接口返回值；
     * 状态也不再去重拉列表或详情，弹窗和详情页都拿这个结果直接改 UI
     * （详情那份 favnum 还有服务端缓存滞后，重拉反而更不准）。
     */
    val actionState = MutableLiveData<CollectionAction>()

    fun load(feedId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)
            val data = runCatching {
                networkRepo.getCollectionList("", feedId.orEmpty(), "feed", 1, 1)
                    .firstOrNull()?.getOrNull()?.data
            }.getOrNull()
            list.postValue(data.orEmpty())
            loading.postValue(false)
        }
    }

    /**
     * 点某个收藏夹：已在里面就取消，否则收藏进去。
     *
     * 只发这一枪，不再跟一次列表刷新：响应里就把最新收藏数和本夹状态给全了，
     * 弹窗按结果就地改这一项的勾，详情页按结果改底栏数字与星标。
     */
    fun toggle(feedId: String, item: CollectionData) {
        viewModelScope.launch(Dispatchers.IO) {
            val collected = item.isBeCollected == 1
            val body = runCatching {
                networkRepo.addToCollection(
                    id = if (collected) "" else item.id.orEmpty(),
                    cancelId = if (collected) item.id.orEmpty() else "",
                    targetId = feedId,
                    type = "feed"
                ).firstOrNull()
            }.getOrNull()?.getOrNull()
            // 成功体只有 data；失败才是 {"status":-5,"message":"该内容已存在"}，把服务端的话透出去
            val action = body?.data
            toastText.postValue(
                Event(
                    when {
                        action == null -> body?.message ?: "操作失败"
                        collected -> "已取消收藏"
                        else -> "已收藏到「${item.title.orEmpty()}」"
                    }
                )
            )
            action?.let {
                actionState.postValue(
                    CollectionAction(
                        collectionId = item.id.orEmpty(),
                        favnum = it.favnum,
                        // 服务端没回 collect 就按点击方向推
                        collect = it.collect ?: if (collected) 0 else 1
                    )
                )
            }
        }
    }

    /** 新建收藏夹（可选封面，传了 uri 会先上传拿 URL）；新建后若给了动态就顺带收藏进去 */
    fun create(
        feedId: String?,
        title: String,
        description: String,
        isOpen: Int,
        coverUri: Uri?,
        resolver: ContentResolver?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)
            val uploaded = upload(coverUri, resolver)
            val pic = uploaded.orEmpty()
            val result = runCatching {
                networkRepo.createCollection(
                    isOpen.toString(), pic, description, title, ""
                ).firstOrNull()
            }.getOrNull()
            val ok = result?.isSuccess == true
            val newId = result?.getOrNull()?.data?.id
            if (ok && !newId.isNullOrEmpty() && !feedId.isNullOrEmpty()) {
                val added = runCatching {
                    networkRepo.addToCollection(newId, "", feedId, "feed").firstOrNull()
                }.getOrNull()?.getOrNull()?.data
                // 新建后顺带收藏也会让收藏数变，把结果一起带出去（新夹还没进列表，弹窗只转发给详情页）
                added?.let {
                    actionState.postValue(
                        CollectionAction(
                            collectionId = newId,
                            favnum = it.favnum,
                            collect = it.collect ?: 1
                        )
                    )
                }
            }
            toastText.postValue(
                Event(
                    when {
                        !ok -> "创建失败"
                        coverUri != null && uploaded == null -> "创建成功，但封面上传失败"
                        else -> "创建成功"
                    }
                )
            )
            load(feedId)
            loading.postValue(false)
        }
    }

    /** 改收藏夹：标题 / 简介 / 公开私密 / 封面 */
    fun update(
        id: String,
        title: String,
        description: String,
        isOpen: Int,
        coverUri: Uri?,
        oldCover: String?,
        resolver: ContentResolver?,
        feedId: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)
            val uploaded = upload(coverUri, resolver)
            val pic = uploaded ?: oldCover.orEmpty()
            val ok = runCatching {
                networkRepo.updateCollection(id, title, description, pic, isOpen)
                    .firstOrNull()?.isSuccess == true
            }.getOrDefault(false)
            toastText.postValue(
                Event(
                    when {
                        !ok -> "保存失败"
                        coverUri != null && uploaded == null -> "已保存，但封面上传失败"
                        else -> "已保存"
                    }
                )
            )
            load(feedId)
            loading.postValue(false)
        }
    }

    fun checkCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = runCatching {
                networkRepo.getCollectionCheckCount().firstOrNull()?.getOrNull()?.data
            }.getOrNull()
            if (!msg.isNullOrEmpty()) toastText.postValue(Event(msg))
        }
    }

    /** 收藏夹详情（编辑前取最新数据），成功后通过 [detail] 通知 */
    val detail = MutableLiveData<CollectionData>()
    val deleted = MutableLiveData<Event<Boolean>>()

    fun loadDetail(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val d = runCatching {
                networkRepo.getCollectionDetail(id).firstOrNull()?.getOrNull()?.data
            }.getOrNull()
            if (d == null) toastText.postValue(Event("加载收藏夹信息失败"))
            else publishDetail(d)
        }
    }

    /** lint NullSafeMutableLiveData 看不到 smart-cast，参数显式非空 */
    private fun publishDetail(d: CollectionData) {
        detail.postValue(d)
    }

    /** 清除收藏夹内无效内容（服务端异步执行，约 5 分钟后生效） */
    fun clearUnUse(colId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                networkRepo.removeUnUseCollectionItem(colId).firstOrNull()
            }.getOrNull()
            toastText.postValue(Event(r?.getOrNull()?.data ?: "操作失败"))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                networkRepo.deleteCollection(id).firstOrNull()
            }.getOrNull()
            val ok = r?.isSuccess == true
            toastText.postValue(Event(r?.getOrNull()?.data ?: if (ok) "删除成功" else "删除失败"))
            if (ok) deleted.postValue(Event(true))
        }
    }

    /**
     * 上传封面图，成功返回图片 URL，失败返回 null。
     *
     * 服务端按「文件名扩展名 / part 的 Content-Type」校验类型（curl 实测）：
     * - image/jpeg + 无扩展名文件名 → 成功
     * - 通配类型（image 加斜杠星号）+ 无扩展名文件名 → 103「请选择正确的文件类型」
     * - 通配类型 + xxx.jpg → 成功
     * 所以 type 必须具体、文件名必须带扩展名，不能再用通配类型 + md5 当文件名。
     */
    private suspend fun upload(uri: Uri?, resolver: ContentResolver?): String? {
        if (uri == null || resolver == null) return null
        val srcBytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (srcBytes == null || srcBytes.isEmpty()) return null

        val (bytes, mime, ext) = when (runCatching { resolver.getType(uri) }.getOrNull()) {
            "image/png" -> Triple(srcBytes, "image/png", "png")
            "image/webp" -> Triple(srcBytes, "image/webp", "webp")
            "image/gif" -> Triple(srcBytes, "image/gif", "gif")
            "image/jpeg", "image/jpg" -> Triple(srcBytes, "image/jpeg", "jpg")
            // HEIF/AVIF 等：服务端不认，解码重编码成 JPEG 再传
            else -> compressToJpeg(resolver, uri) ?: return null
        }

        val md5 = MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val part = MultipartBody.Part.createFormData(
            "picFile", "$md5.$ext", bytes.toRequestBody(mime.toMediaTypeOrNull())
        )
        return runCatching {
            networkRepo.uploadCollectionImage(md5, part).firstOrNull()?.getOrNull()?.data
        }.getOrNull()
    }

    /** 非白名单图片格式转 JPEG（最长边限制 2048，避免大图 OOM） */
    private fun compressToJpeg(
        resolver: ContentResolver,
        uri: Uri
    ): Triple<ByteArray, String, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                })
            }
        }.getOrNull() ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return Triple(out.toByteArray(), "image/jpeg", "jpg")
    }
}
