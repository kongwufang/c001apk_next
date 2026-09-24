package com.example.c001apk.logic.network

import com.example.c001apk.logic.model.BlackListActionResponse
import com.example.c001apk.logic.model.BlackListResponse
import com.example.c001apk.logic.model.CheckCountResponse
import com.example.c001apk.logic.model.CheckResponse
import com.example.c001apk.logic.model.CollectionActionResponse
import com.example.c001apk.logic.model.CollectionCheckCountResponse
import com.example.c001apk.logic.model.CollectionDetailResponse
import com.example.c001apk.logic.model.CollectionListResponse
import com.example.c001apk.logic.model.CollectionUploadResponse
import com.example.c001apk.logic.model.CreateFeedResponse
import com.example.c001apk.logic.model.SpamConfigResponse
import com.example.c001apk.logic.model.EventDetailResponse
import com.example.c001apk.logic.model.FeedContentResponse
import com.example.c001apk.logic.model.HitHistoryListResponse
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.LikeFeedResponse
import com.example.c001apk.logic.model.LikeReplyResponse
import com.example.c001apk.logic.model.LimitActionResponse
import com.example.c001apk.logic.model.LoadUrlResponse
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.logic.model.OSSUploadPrepareResponse
import com.example.c001apk.logic.model.PostReplyResponse
import com.example.c001apk.logic.model.ProfileEditResponse
import com.example.c001apk.logic.model.SearchHotResponse
import com.example.c001apk.logic.model.SearchSuggestResponse
import com.example.c001apk.logic.model.StringDataResponse
import com.example.c001apk.logic.model.TotalReplyResponse
import com.example.c001apk.logic.model.UserProfileResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url


interface ApiService {

    @GET("/v6/main/indexV8")
    fun getHomeFeed(
        @Query("page") page: Int,
        @Query("firstLaunch") firstLaunch: Int,
        @Query("installTime") installTime: String,
        @Query("firstItem") firstItem: String?,
        @Query("lastItem") lastItem: String?,
        @Query("ids") ids: String = "",
    ): Call<HomeFeedResponse>

    @GET("/v6/feed/detail")
    fun getFeedContent(
        @Query("id") id: String,
        @Query("rid") rid: String?
    ): Call<FeedContentResponse>

    @GET("/v6/event/detail")
    fun getEventDetail(
        @Query("id") id: String,
    ): Call<EventDetailResponse>

    @GET("/v6/feed/replyList")
    fun getFeedContentReply(
        @Query("id") id: String,
        @Query("listType") listType: String,
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String?,
        @Query("lastItem") lastItem: String?,
        @Query("discussMode") discussMode: Int,
        @Query("feedType") feedType: String,
        @Query("blockStatus") blockStatus: Int,
        @Query("fromFeedAuthor") fromFeedAuthor: Int
    ): Call<TotalReplyResponse>

    @GET("/v6/search")
    fun getSearch(
        @Query("type") type: String,
        @Query("feedType") feedType: String,
        @Query("sort") sort: String,
        @Query("searchValue") keyWord: String,
        @Query("pageType") pageType: String?,
        @Query("pageParam") pageParam: String?,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?,
        @Query("showAnonymous") showAnonymous: Int = -1
    ): Call<HomeFeedResponse>

    /** 搜索页默认态：热门搜索 + 热搜榜（refresh=1 强制刷新热门搜索） */
    @GET("/v6/search")
    fun getSearchHot(
        @Query("type") type: String = "hotSearch",
        @Query("refresh") refresh: Int = 0,
        @Query("returnType") returnType: String = "all"
    ): Call<SearchHotResponse>

    /** 输入过程中的搜索联想 */
    @GET("/v6/search/suggestSearchWordsNew")
    fun getSuggestSearchWords(
        @Query("searchValue") searchValue: String,
        @Query("type") type: String = "app"
    ): Call<SearchSuggestResponse>

    @GET("/v6/feed/replyList?listType=&discussMode=0&feedType=feed_reply&blockStatus=0&fromFeedAuthor=0")
    fun getReply2Reply(
        @Query("id") id: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<TotalReplyResponse>

    // 用户黑名单（云端）：列表 / 加入 / 移出 / 状态（uid 都在 query 或 form）
    @GET("/v6/user/blackList")
    fun getBlackList(
        @Query("page") page: Int,
    ): Call<BlackListResponse>

    @POST("/v6/user/addToBlackList")
    fun addToBlackList(
        @Query("uid") uid: String,
    ): Call<BlackListActionResponse>

    @POST("/v6/user/removeFromBlackList")
    fun removeFromBlackList(
        @Query("uid") uid: String,
    ): Call<BlackListActionResponse>

    @FormUrlEncoded
    @POST("/v6/user/getLimitAction")
    fun getLimitAction(
        @Field("uid") uid: String,
    ): Call<LimitActionResponse>

    @GET("/v6/user/space")
    fun getUserSpace(
        @Query("uid") uid: String,
    ): Call<UserProfileResponse>

    @GET("/v6/user/feedList?showAnonymous=0&isIncludeTop=1&showDoing=0")
    fun getUserFeed(
        @Query("uid") uid: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<HomeFeedResponse>

    /** 个人主页「图文」tab */
    @GET("/v6/user/htmlFeedList")
    fun getUserHtmlFeed(
        @Query("uid") uid: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<HomeFeedResponse>

    /** 个人主页「问答」tab */
    @GET("/v6/user/questionAndAnswerList")
    fun getUserQuestionAndAnswer(
        @Query("uid") uid: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<HomeFeedResponse>

    @GET("/v6/apk/detail")
    fun getAppInfo(
        @Query("id") id: String,
        @Query("installed") installed: Int = 1,
    ): Call<FeedContentResponse>

    @POST("/v6/apk/download?extra=")
    fun getAppDownloadLink(
        @Query("pn") id: String,
        @Query("aid") aid: String,
        @Query("vc") vc: String,
    ): Call<Any>

    @GET("/v6/topic/newTagDetail")
    fun getTopicLayout(
        @Query("tag") tag: String
    ): Call<FeedContentResponse>

    @GET("/v6/product/detail")
    fun getProductLayout(
        @Query("id") id: String
    ): Call<FeedContentResponse>

    @GET("/v6/user/profile")
    fun getProfile(
        @Query("uid") uid: String
    ): Call<UserProfileResponse>

    @GET
    fun getFollowList(
        @Url url: String,
        @Query("uid") uid: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<HomeFeedResponse>

    @POST
    fun postLikeFeed(
        @Url url: String,
        @Query("id") id: String
    ): Call<LikeFeedResponse>

    @POST
    fun postLikeReply(
        @Url url: String,
        @Query("id") id: String
    ): Call<LikeReplyResponse>

    @GET("/v6/account/checkLoginInfo")
    fun checkLoginInfo(
    ): Call<CheckResponse>

    // 验证码（动态/回复里服务端要求换验证码时用），登录表单相关的接口已随表单登录一起移除
    @GET
    fun getValidateCaptcha(@Url url: String): Call<ResponseBody>

    // ===== 其他屏蔽项（关键字 / 用户 / 节点）：读 spamWordList，写 updateConfig =====

    @GET("/v6/user/spamWordList")
    fun getSpamWordList(): Call<SpamConfigResponse>

    @FormUrlEncoded
    @POST("/v6/account/updateConfig")
    fun updateConfig(
        @Field("key") key: String,
        @Field("value") value: String,
    ): Call<CheckResponse>

    // ===== 收藏夹（多收藏夹）=====

    /** 收藏夹列表；带 `id=<动态id>&type=feed&showDefault=1` 时会下发 `isBeCollected` 表示该动态是否已在此夹 */
    @GET("/v6/collection/list")
    fun getCollectionList(
        @Query("uid") uid: String,
        @Query("id") id: String,
        @Query("type") type: String,
        @Query("showDefault") showDefault: Int,
        @Query("page") page: Int,
    ): Call<CollectionListResponse>

    /** 收藏：`id=收藏夹id`、`cancelId` 留空；取消收藏：`id` 留空、`cancelId=收藏夹id` */
    @FormUrlEncoded
    @POST("/v6/collection/addItem")
    fun addToCollection(
        @Field("id") id: String,
        @Field("cancelId") cancelId: String,
        @Field("targetId") targetId: String,
        @Field("type") type: String,
    ): Call<CollectionActionResponse>

    /** 新建收藏夹（multipart，封面为 OSS URL 或空串） */
    @Multipart
    @POST("/v6/collection/create")
    fun createCollection(
        @Part("isOpen") isOpen: String,
        @Part("pic") pic: String,
        @Part("description") description: String,
        @Part("title") title: String,
        @Part("sourceId") sourceId: String,
    ): Call<CollectionDetailResponse>

    /** 改收藏夹（标题 / 简介 / 封面 / 公开或私密） */
    @FormUrlEncoded
    @POST("/v6/collection/update")
    fun updateCollection(
        @Field("id") id: String,
        @Field("title") title: String,
        @Field("description") description: String,
        @Field("pic") pic: String,
        @Field("isOpen") isOpen: Int,
    ): Call<CollectionDetailResponse>

    /** 收藏夹封面图上传（multipart，fileMd5 为图片 md5，文件名也用 md5） */
    @Multipart
    @POST("/v6/collection/uploadImage")
    fun uploadCollectionImage(
        @Query("fieldName") fieldName: String,
        @Query("uploadDir") uploadDir: String,
        @Query("fileMd5") fileMd5: String,
        @Part file: MultipartBody.Part,
    ): Call<CollectionUploadResponse>

    @GET("/v6/collection/checkCount")
    fun getCollectionCheckCount(): Call<CollectionCheckCountResponse>

    /** 收藏夹详情（编辑前取最新数据） */
    @GET("/v6/collection/detail")
    fun getCollectionDetail(
        @Query("id") id: String,
    ): Call<CollectionDetailResponse>

    /** 清除收藏夹内无效内容（服务端异步执行） */
    @FormUrlEncoded
    @POST("/v6/collection/removeUnUseItem")
    fun removeUnUseCollectionItem(
        @Field("colId") colId: String,
    ): Call<StringDataResponse>

    /** 删除收藏夹 */
    @FormUrlEncoded
    @POST("/v6/collection/delete")
    fun deleteCollection(
        @Field("id") id: String,
    ): Call<StringDataResponse>

    // ===== 云端浏览历史 =====

    /** 酷安云端浏览历史（打开详情接口时服务端自动记录，无需显式上报） */
    @GET("/v6/user/hitHistoryList")
    fun getHitHistoryList(
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String?,
        @Query("lastItem") lastItem: String?,
    ): Call<HitHistoryListResponse>

    @POST("v6/feed/reply")
    @FormUrlEncoded
    fun postReply(
        @FieldMap data: HashMap<String, String>,
        @Query("id") id: String,
        @Query("type") type: String
    ): Call<PostReplyResponse>

    @GET("/v6/page/dataList")
    fun getDataList(
        @Query("url") url: String,
        @Query("title") title: String,
        @Query("subTitle") subTitle: String?,
        @Query("lastItem") lastItem: String?,
        @Query("page") page: Int
    ): Call<HomeFeedResponse>

    @GET("/v6/dyhArticle/list")
    fun getDyhDetail(
        @Query("dyhId") dyhId: String,
        @Query("type") type: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<HomeFeedResponse>

    @GET
    fun getMessage(
        @Url url: String,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<MessageResponse>

    @POST
    fun postFollowUnFollow(
        @Url url: String,
        @Query("uid") uid: String
    ): Call<LikeReplyResponse>

    @POST("/v6/feed/createFeed")
    @FormUrlEncoded
    fun postCreateFeed(
        @FieldMap data: HashMap<String, String>
    ): Call<CreateFeedResponse>

    @POST("/v6/account/requestValidate")
    @FormUrlEncoded
    fun postRequestValidate(
        @FieldMap data: HashMap<String, String?>
    ): Call<LikeReplyResponse>

    @GET("/v6/vote/commentList")
    fun getVoteComment(
        @Query("fid") fid: String,
        @Query("extra_key") extraKey: String,
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String?,
        @Query("lastItem") lastItem: String?,
    ): Call<TotalReplyResponse>

    @GET("/v6/question/answerList")
    fun getAnswerList(
        @Query("id") fid: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String?,
        @Query("lastItem") lastItem: String?,
    ): Call<TotalReplyResponse>

    @GET("/v6/product/categoryList")
    fun getProductList(): Call<HomeFeedResponse>

    @GET
    fun getCollectionList(
        @Url url: String,
        @Query("uid") uid: String?,
        @Query("id") id: String?,
        @Query("showDefault") showDefault: Int,
        @Query("page") page: Int,
        @Query("lastItem") lastItem: String?
    ): Call<HomeFeedResponse>

    @POST
    fun postDelete(
        @Url url: String,
        @Query("id") id: String,
    ): Call<LikeReplyResponse>

    // 修改动态可见性。注意 id 与 publish_status 都必须放在表单 body 里，
    // 放到 query 上服务端只看到空 id → 返回 -1「动态id不能为空」。
    @POST("/v6/feed/updatePublishStatus")
    @FormUrlEncoded
    fun postPublishStatus(
        @FieldMap data: HashMap<String, String?>
    ): Call<LikeReplyResponse>

    @POST("/v6/product/changeFollowStatus")
    @FormUrlEncoded
    fun postFollow(
        @FieldMap data: HashMap<String, String>
    ): Call<LikeReplyResponse>

    // 个人主页置顶。nodeType=member + nodeId=自己的 uid + feedId=动态 id，
    // 三个都是表单字段，成功返回 data="个人主页置顶成功"。
    @POST("/v6/feed/addTopToNode")
    @FormUrlEncoded
    fun addTopToNode(
        @Field("nodeType") nodeType: String,
        @Field("nodeId") nodeId: String,
        @Field("feedId") feedId: String,
    ): Call<LikeReplyResponse>

    // 取消个人主页置顶，参数同 addTopToNode，成功返回 data="个人主页取消置顶成功"
    @POST("/v6/feed/cancelTopFromNode")
    @FormUrlEncoded
    fun cancelTopFromNode(
        @Field("nodeType") nodeType: String,
        @Field("nodeId") nodeId: String,
        @Field("feedId") feedId: String,
    ): Call<LikeReplyResponse>

    @GET
    fun getFollow(
        @Url url: String,
        @Query("tag") tag: String?,
        @Query("id") id: String?,
    ): Call<LikeFeedResponse>

    @POST("/v6/upload/ossUploadPrepare")
    @FormUrlEncoded
    fun postOSSUploadPrepare(
        @FieldMap data: HashMap<String, String>
    ): Call<OSSUploadPrepareResponse>

    @GET("/v6/feed/searchTag")
    fun getSearchTag(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("recentIds") recentIds: String?,
        @Query("firstItem") firstItem: String?,
        @Query("lastItem") lastItem: String?,
    ): Call<HomeFeedResponse>

    @GET("/v6/feed/loadShareUrl")
    fun loadShareUrl(
        @Query("url") url: String,
        @Query("packageName") packageName: String = "",
    ): Call<LoadUrlResponse>

    @GET("/v6/notification/checkCount")
    fun checkCount(): Call<CheckCountResponse>

    // ---------------- 编辑资料（个人主页） ----------------

    /**
     * 改资料（2026-09-24 抓包核对）。只提交改动的那一项：
     * `key=gender`/`value=1|0|-1`、`key=&value={"birthyear":..,"birthmonth":..,"birthday":..}`、
     * `key=&value={"province":"..","city":".."}`、`key=bio&value=..`
     *
     * 成功响应是 `{"data":{...完整资料...}}`，**不带 message**。
     */
    @POST("/v6/account/changeProfile")
    @FormUrlEncoded
    fun changeProfile(
        @Field("key") key: String,
        @Field("value") value: String,
    ): Call<ProfileEditResponse>

    /** 生日 / 地区回到「保密」：key=birth 或 key=location（抓包核对） */
    @POST("/v6/account/resetProfile")
    @FormUrlEncoded
    fun resetProfile(
        @Field("key") key: String,
    ): Call<ProfileEditResponse>

    /** 换头像：multipart，part 名固定 imgFile、filename 用图片 md5（无扩展名），返回 `{"data":"<新头像 url>"}` */
    @Multipart
    @POST("/v6/account/changeAvatar")
    fun changeAvatar(
        @Part file: MultipartBody.Part,
    ): Call<StringDataResponse>

    /**
     * 换主页背景图（抓包 + 实测核对 2026-09-24）：先 `ossUploadPrepare` 拿凭证上传到 OSS，
     * 再把地址回传。`url` = **完整 CDN 地址** `http://avatar.coolapk.com/<uploadFileName>`，
     * 成功返回 `{"data":"上传成功"}`（同样没有 message）。
     */
    @POST("/v6/account/changeAvatarCover")
    @FormUrlEncoded
    fun changeAvatarCover(
        @Field("url") url: String,
    ): Call<StringDataResponse>

}