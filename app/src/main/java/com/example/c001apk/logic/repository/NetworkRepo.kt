package com.example.c001apk.logic.repository

import com.example.c001apk.di.Api1Service
import com.example.c001apk.di.Api1ServiceNoRedirect
import com.example.c001apk.di.Api2Service
import com.example.c001apk.logic.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class NetworkRepo @Inject constructor(
    @Api1Service
    private val apiService: ApiService,
    @Api1ServiceNoRedirect
    private val apiServiceNoRedirect: ApiService,
    @Api2Service
    private val api2Service: ApiService,
) {

    suspend fun getHomeFeed(
        page: Int,
        firstLaunch: Int,
        installTime: String,
        firstItem: String?,
        lastItem: String?
    ) = fire {
        Result.success(
            api2Service.getHomeFeed(page, firstLaunch, installTime, firstItem, lastItem).await()
        )
    }

    suspend fun getFeedContent(id: String, rid: String?) = fire {
        Result.success(api2Service.getFeedContent(id, rid).await())
    }

    suspend fun getFeedContentReply(
        id: String,
        listType: String,
        page: Int,
        firstItem: String?,
        lastItem: String?,
        discussMode: Int,
        feedType: String,
        blockStatus: Int,
        fromFeedAuthor: Int
    ) = fire {
        Result.success(
            api2Service.getFeedContentReply(
                id,
                listType,
                page,
                firstItem,
                lastItem,
                discussMode,
                feedType,
                blockStatus,
                fromFeedAuthor
            ).await()
        )
    }

    suspend fun getSearch(
        type: String, feedType: String, sort: String, keyWord: String, pageType: String?,
        pageParam: String?, page: Int, lastItem: String?
    ) = fire {
        Result.success(
            apiService.getSearch(
                type, feedType, sort, keyWord, pageType, pageParam, page, lastItem
            ).await()
        )
    }

    /** 搜索页默认态的「热门搜索 + 热搜榜」（一次请求拿全） */
    suspend fun getSearchHot(refresh: Int = 0) = fire {
        Result.success(apiService.getSearchHot(refresh = refresh).await())
    }

    /** 输入过程中的搜索联想 */
    suspend fun getSuggestSearchWords(keyWord: String) = fire {
        Result.success(apiService.getSuggestSearchWords(keyWord).await())
    }

    suspend fun getReply2Reply(id: String, page: Int, lastItem: String?) = fire {
        Result.success(apiService.getReply2Reply(id, page, lastItem).await())
    }

    suspend fun getTopicLayout(tag: String) = fire {
        Result.success(api2Service.getTopicLayout(tag).await())
    }

    suspend fun getProductLayout(id: String) = fire {
        Result.success(apiService.getProductLayout(id).await())
    }

    suspend fun getEventDetail(id: String) = fire {
        Result.success(apiService.getEventDetail(id).await())
    }

    suspend fun getUserSpace(uid: String) = fire {
        Result.success(apiService.getUserSpace(uid).await())
    }

    suspend fun getBlackList(page: Int) = fire {
        Result.success(apiService.getBlackList(page).await())
    }

    suspend fun addToBlackList(uid: String) = fire {
        Result.success(apiService.addToBlackList(uid).await())
    }

    suspend fun removeFromBlackList(uid: String) = fire {
        Result.success(apiService.removeFromBlackList(uid).await())
    }

    suspend fun getLimitAction(uid: String) = fire {
        Result.success(apiService.getLimitAction(uid).await())
    }

    suspend fun getUserFeed(uid: String, page: Int, lastItem: String?) = fire {
        Result.success(apiService.getUserFeed(uid, page, lastItem).await())
    }

    suspend fun getUserHtmlFeed(uid: String, page: Int, lastItem: String?) = fire {
        Result.success(apiService.getUserHtmlFeed(uid, page, lastItem).await())
    }

    suspend fun getUserQuestionAndAnswer(uid: String, page: Int, lastItem: String?) = fire {
        Result.success(apiService.getUserQuestionAndAnswer(uid, page, lastItem).await())
    }

    suspend fun getAppInfo(id: String) = fire {
        Result.success(apiService.getAppInfo(id).await())
    }

    suspend fun getAppDownloadLink(pn: String, aid: String, vc: String) = fire {
        val appResponse = apiServiceNoRedirect.getAppDownloadLink(pn, aid, vc).response()
        Result.success(appResponse.headers()["Location"])
    }

    suspend fun getProfile(uid: String) = fire {
        Result.success(api2Service.getProfile(uid).await())
    }

    // ---------------- 编辑资料 ----------------

    /** 改资料，只提交改动的那一项（key 为空表示 value 是 JSON：生日 / 地区） */
    suspend fun changeProfile(key: String, value: String) = fire {
        Result.success(apiService.changeProfile(key, value).await())
    }

    /** 生日（key=birth）/ 地区（key=location）恢复「保密」 */
    suspend fun resetProfile(key: String) = fire {
        Result.success(apiService.resetProfile(key).await())
    }

    suspend fun changeAvatar(part: MultipartBody.Part) = fire {
        Result.success(apiService.changeAvatar(part).await())
    }

    suspend fun changeAvatarCover(url: String) = fire {
        Result.success(apiService.changeAvatarCover(url).await())
    }

    suspend fun getFollowList(url: String, uid: String, page: Int, lastItem: String?) = fire {
        Result.success(apiService.getFollowList(url, uid, page, lastItem).await())
    }

    suspend fun postLikeFeed(url: String, id: String) = fire {
        Result.success(apiService.postLikeFeed(url, id).await())
    }

    suspend fun postLikeReply(url: String, id: String) = fire {
        Result.success(apiService.postLikeReply(url, id).await())
    }

    suspend fun checkLoginInfo() = fire {
        Result.success(apiService.checkLoginInfo().response())
    }

    // ===== 其他屏蔽项（关键字 / 用户 / 节点）=====
    suspend fun getSpamWordList() = fire {
        Result.success(apiService.getSpamWordList().await())
    }

    suspend fun updateConfig(key: String, value: String) = fire {
        Result.success(apiService.updateConfig(key, value).await())
    }

    // ===== 收藏夹（多收藏夹）=====
    suspend fun getCollectionList(
        uid: String,
        id: String,
        type: String,
        showDefault: Int,
        page: Int
    ) = fire {
        Result.success(apiService.getCollectionList(uid, id, type, showDefault, page).await())
    }

    /** 收藏：id=收藏夹 id；取消收藏：cancelId=收藏夹 id（见 HAR 实测） */
    suspend fun addToCollection(
        id: String,
        cancelId: String,
        targetId: String,
        type: String
    ) = fire {
        Result.success(apiService.addToCollection(id, cancelId, targetId, type).await())
    }

    suspend fun createCollection(
        isOpen: String,
        pic: String,
        description: String,
        title: String,
        sourceId: String
    ) = fire {
        Result.success(apiService.createCollection(isOpen, pic, description, title, sourceId).await())
    }

    suspend fun updateCollection(
        id: String,
        title: String,
        description: String,
        pic: String,
        isOpen: Int
    ) = fire {
        Result.success(apiService.updateCollection(id, title, description, pic, isOpen).await())
    }

    suspend fun uploadCollectionImage(fileMd5: String, file: MultipartBody.Part) = fire {
        Result.success(
            apiService.uploadCollectionImage("picFile", "feed_image", fileMd5, file).await()
        )
    }

    suspend fun getCollectionCheckCount() = fire {
        Result.success(apiService.getCollectionCheckCount().await())
    }

    suspend fun getCollectionDetail(id: String) = fire {
        Result.success(apiService.getCollectionDetail(id).await())
    }

    suspend fun removeUnUseCollectionItem(colId: String) = fire {
        Result.success(apiService.removeUnUseCollectionItem(colId).await())
    }

    suspend fun deleteCollection(id: String) = fire {
        Result.success(apiService.deleteCollection(id).await())
    }

    suspend fun getHitHistoryList(page: Int, firstItem: String?, lastItem: String?) = fire {
        Result.success(apiService.getHitHistoryList(page, firstItem, lastItem).await())
    }

    suspend fun getValidateCaptcha(url: String) = fire {
        Result.success(apiService.getValidateCaptcha(url).response())
    }

    suspend fun postReply(data: HashMap<String, String>, id: String, type: String) = fire {
        Result.success(apiService.postReply(data, id, type).await())
    }

    suspend fun getDataList(
        url: String, title: String, subTitle: String?, lastItem: String?, page: Int
    ) = fire {
        Result.success(apiService.getDataList(url, title, subTitle, lastItem, page).await())
    }

    suspend fun getDyhDetail(dyhId: String, type: String, page: Int, lastItem: String?) =
        fire {
            Result.success(apiService.getDyhDetail(dyhId, type, page, lastItem).await())
        }

    suspend fun getMessage(url: String, page: Int, lastItem: String?) = fire {
        Result.success(apiService.getMessage(url, page, lastItem).await())
    }

    suspend fun postFollowUnFollow(url: String, uid: String) = fire {
        Result.success(apiService.postFollowUnFollow(url, uid).await())
    }

    suspend fun postCreateFeed(data: HashMap<String, String>) = fire {
        Result.success(apiService.postCreateFeed(data).await())
    }

    suspend fun postRequestValidate(data: HashMap<String, String?>) = fire {
        Result.success(apiService.postRequestValidate(data).await())
    }

    suspend fun getVoteComment(
        fid: String,
        extraKey: String,
        page: Int,
        firstItem: String?,
        lastItem: String?,
    ) = fire {
        Result.success(apiService.getVoteComment(fid, extraKey, page, firstItem, lastItem).await())
    }

    suspend fun getAnswerList(
        id: String,
        sort: String,
        page: Int,
        firstItem: String?,
        lastItem: String?,
    ) = fire {
        Result.success(apiService.getAnswerList(id, sort, page, firstItem, lastItem).await())
    }

    suspend fun getProductList() = fire {
        Result.success(apiService.getProductList().await())
    }

    suspend fun getCollectionList(
        url: String,
        uid: String?,
        id: String?,
        showDefault: Int,
        page: Int,
        lastItem: String?
    ) = fire {
        Result.success(
            apiService.getCollectionList(url, uid, id, showDefault, page, lastItem).await()
        )
    }

    suspend fun postDelete(url: String, id: String) = fire {
        Result.success(apiService.postDelete(url, id).await())
    }

    suspend fun postPublishStatus(data: HashMap<String, String?>) = fire {
        Result.success(apiService.postPublishStatus(data).await())
    }

    // 个人主页置顶 / 取消置顶（只能操作自己的动态，nodeId 传自己的 uid）
    suspend fun addTopToNode(nodeType: String, nodeId: String, feedId: String) = fire {
        Result.success(apiService.addTopToNode(nodeType, nodeId, feedId).await())
    }

    suspend fun cancelTopFromNode(nodeType: String, nodeId: String, feedId: String) = fire {
        Result.success(apiService.cancelTopFromNode(nodeType, nodeId, feedId).await())
    }

    suspend fun postFollow(data: HashMap<String, String>) = fire {
        Result.success(apiService.postFollow(data).await())
    }

    suspend fun getFollow(url: String, tag: String?, id: String?) = fire {
        Result.success(apiService.getFollow(url, tag, id).await())
    }

    suspend fun postOSSUploadPrepare(data: HashMap<String, String>) = fire {
        Result.success(apiService.postOSSUploadPrepare(data).await())
    }

    suspend fun getSearchTag(
        query: String,
        page: Int,
        recentIds: String?,
        firstItem: String?,
        lastItem: String?,
    ) = fire {
        Result.success(apiService.getSearchTag(query, page, recentIds, firstItem, lastItem).await())
    }

    suspend fun loadShareUrl(url: String) = fire {
        Result.success(apiService.loadShareUrl(url).await())
    }

    suspend fun checkCount() = fire {
        Result.success(apiService.checkCount().await())
    }

    private suspend fun <T> Call<T>.await(): T {
        return suspendCoroutine { continuation ->
            enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    val body = response.body()
                    if (body != null) continuation.resume(body)
                    else continuation.resumeWithException(
                        RuntimeException("response body is null")
                    )
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    continuation.resumeWithException(t)
                }
            })
        }
    }

    private suspend fun <T> Call<T>.response(): Response<T> {
        return suspendCoroutine { continuation ->
            enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    continuation.resumeWithException(t)
                }
            })
        }
    }

    private fun <T> fire(block: suspend () -> Result<T>) =
        flow {
            val result = try {
                block()
            } catch (e: Exception) {
                Result.failure(e)
            }
            emit(result)
        }.flowOn(Dispatchers.IO)

}