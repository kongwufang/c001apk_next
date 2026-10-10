package com.example.c001apk.logic.model

import android.os.Parcelable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.parcelize.Parcelize
import java.lang.reflect.Type

data class HomeFeedResponse(
    val status: Int?,
    val error: Int?,
    val message: String?,
    val messageStatus: Int?,
    val data: List<Data>?
) {

    @Parcelize
    data class Data(
        val rid: Long?,
        val forwardSourceFeed: MessageResponse.ForwardSourceFeed?,
        @SerializedName("comment_num") val commentNum: String?,
        @SerializedName("fans_num") val fansNum: String?,
        @SerializedName("target_type") val targetType: String?,
        @SerializedName("target_type_title") val targetTypeTitle: String?,
        val replyMeRows: List<TotalReplyResponse.Data>?,
        @SerializedName("cover_pic") val coverPic: String?,
        @SerializedName("is_open") val isOpen: Int?,
        @SerializedName("item_num") val itemNum: String?,
        @SerializedName("follow_num") val followNum: String?,
        var description: String?,
        val subTitle: String?,
        val likeTime: Long?,
        @SerializedName("extra_title") val extraTitle: String?,
        @SerializedName("extra_url") val extraUrl: String?,
        @SerializedName("extra_pic") val extraPic: String?,
        val feedTypeName: String?,
        val vote: Vote?,
        @SerializedName("message_cover") val messageCover: String?,
        @SerializedName("message_title") val messageTitle: String?,
        @SerializedName("message_raw_output") val messageRawOutput: String?,
        val relationRows: ArrayList<RelationRows>?,
        val targetRow: TargetRow?,
        @SerializedName("change_count") val changeCount: Int?,
        val isModified: Int?,
        @SerializedName("ip_location") val ipLocation: String?,
        val isFeedAuthor: Int?,
        val topReplyRows: List<TotalReplyResponse.Data>?,
        val extraDataArr: ExtraDataArr?,
        val intro: String?,
        @SerializedName("tag_pics") val tagPics: List<String>?,
        val tabList: List<TabList>?,
        val displayUsername: String?,
        val cover: String?,
        val selectedTab: String?,
        val homeTabCardRows: List<HomeTabCardRows>?,
        @SerializedName("be_like_num") val beLikeNum: String?,
        val version: String?,
        val apkversionname: String?,
        val apkversioncode: String?,
        val apksize: String?,
        val apkfile: String?,
        val lastupdate: Long?,
        val follow: String?,
        val level: String?,
        val fans: String?,
        val logintime: Long?,
        val experience: Int?,
        val regdate: String?,
        @SerializedName("next_level_experience") val nextLevelExperience: Int?,
        val bio: String?,
        val feed: Feed?,
        val gender: Int?,
        val city: String?,
        val downnum: String?,
        val downCount: String?,
        val apkname: String?,
        val entityType: String,
        val feedType: String?,
        val entityTemplate: String?,
        var entities: MutableList<Entities>?,
        val id: String?,
        val fid: String?,
        val url: String?,
        val uid: String?,
        val ruid: String?,
        val changelog: String?,
        val username: String?,
        val rusername: String?,
        val tpic: String?,
        val message: String?,
        val pic: String?,
        val tags: String?,
        val ttitle: String?,
        var likenum: String?,
        val commentnum: String?,
        val replynum: String?,
        val forwardnum: String?,
        // 可变：收藏夹弹窗操作后要把服务端回的新收藏数写回来
        var favnum: String?,
        val dateline: Long?,
        @SerializedName("create_time") val createTime: String?,
        @SerializedName("device_title") val deviceTitle: String?,
        @SerializedName("device_name") val deviceName: String?,
        @SerializedName("recent_reply_ids") val recentReplyIds: String?,
        @SerializedName("recent_like_list") val recentLikeList: String?,
        val entityId: String?,
        val userAvatar: String?,
        /**
         * 扁平用户实体（用户搜索返回的就是这种形状）自带的认证字段：
         * 跟 uid / username 平级，不在 userInfo 里面，所以这里也得留一份。
         */
        @SerializedName("verify_status") val verifyStatus: Int? = null,
        @SerializedName("verify_icon") val verifyIcon: String? = null,
        val infoHtml: String?,
        val title: String?,
        val commentStatusText: String?,
        val picArr: List<String>?,
        var replyRows: List<ReplyRows>?,
        val replyRowsMore: Int?,
        val logo: String?,
        @SerializedName("hot_num") val hotNum: String?,
        @SerializedName("feed_comment_num") val feedCommentNum: String?,
        @SerializedName("hot_num_txt") val hotNumTxt: String?,
        @SerializedName("feed_comment_num_txt") val feedCommentNumTxt: String?,
        @SerializedName("commentnum_txt") val commentnumTxt: String?,
        @SerializedName("follownum_txt") val follownumTxt: String? = null,
        @SerializedName("recent_follow_list") val recentFollowList: List<RecentFollow>? = null,
        val commentCount: String?,
        @SerializedName("alias_title") val aliasTitle: String?,
        val userAction: UserAction?,
        val userInfo: UserInfo?,
        val fUserInfo: UserInfo?,
        var isFollow: Int?,
        // 1 = 仅自己可见，0 = 公开（详情与列表接口都会下发）
        @SerializedName("publish_status") val publishStatus: Int?,
        // 1 = 该条已在自己的个人主页置顶。
        // 注意：只有「自己主页的 /v6/user/feedList」才下发，详情接口与他人主页都没有这个字段，
        // 所以不能靠它判断「当前登录用户之外」的置顶状态。
        @SerializedName("isStickTop") val isStickTop: Int? = null,
        // 产品页（/v6/product/detail）返回的各版本配置，url 指向官方 H5 参数页
        val configRows: List<ConfigRow>? = null,
        // 产品页（/v6/product/detail）返回的评分子项（续航/影像/性能/屏幕/外观质感/性价比），
        // 发表点评（type=rating）时随 v4_score_item_1..6 提交
        @SerializedName("rating_item_info") val ratingItemInfo: List<RatingItem>? = null,

        // 产品列表页（/product/productList，同价位/同SoC/同系列对比）的条目字段
        @SerializedName("price_min") val priceMin: String? = null,
        @SerializedName("price_max") val priceMax: String? = null,
        @SerializedName("star_average_score") val starAverageScore: String? = null,
        @SerializedName("star_total_count") val starTotalCount: String? = null,
        @SerializedName("config_name") val configName: String? = null,
        val productSpecs: List<String>? = null,
        // 服务端可能下发对象（有评分项）或空数组 []（没有评分项），见 ProductRatingSpecsAdapter
        @field:JsonAdapter(ProductRatingSpecsAdapter::class)
        val productRatingSpecs: Map<String, String>? = null,

        // ---- 活动页「线下」tab 的 pear_goods 实体字段 ----
        @SerializedName("goods_title") val goodsTitle: String? = null,
        @SerializedName("goods_promo_title") val goodsPromoTitle: String? = null,
        @SerializedName("goods_promo_price") val goodsPromoPrice: Int? = null,
        @SerializedName("goods_pic") val goodsPic: String? = null,
        @SerializedName("goods_tags") val goodsTags: String? = null,
        @SerializedName("goods_url") val goodsUrl: String? = null,
        @SerializedName("goods_buy_url") val goodsBuyUrl: String? = null,
        @SerializedName("goods_buy_text") val goodsBuyText: String? = null,

        // ---- 用户主页「点评」tab 的 nodeRating 实体 ----
        @SerializedName("target_info") val ratingTargetInfo: RatingTargetInfo? = null,
        val star: Int? = null,
        @SerializedName("rating_score_old") val ratingScoreOld: Int? = null,
        @SerializedName("comment_good") val commentGood: String? = null,
        @SerializedName("comment_bad") val commentBad: String? = null,
        @SerializedName("comment_general") val commentGeneral: String? = null,
        @SerializedName("device_info") val ratingDeviceInfo: String? = null,
        @SerializedName("buy_status") val buyStatus: Int? = null,

        // ---- 应用版本历史（/v6/apk/downloadVersionList）----
        // 这个接口的条目没有 entityType / entityId / id，只有下面这几个 version* 字段，
        // 所以 entityType 运行时是 null（非空声明挡不住 Gson），列表分类只能靠 versionId 判断。
        val versionId: Long? = null,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val versionSize: String? = null,
        val versionLength: Long? = null,
        val versionDate: String? = null,
        val downloadFrom: String? = null,
        val packageName: String? = null,
    ) : Parcelable {

        /**
         * 是否已收藏。详情接口把收藏状态放在 `userAction` 的 `collect` / `favorite` 上
         * （实测收藏/取消后两个键同步变化，列表项不下发），所以两个键要一起看，只看一个会漏。
         */
        val isFavorited: Boolean
            get() = userAction?.collect == 1 || userAction?.favorite == 1
    }

    /** 话题页头部「最近关注的人」（只要 uid + 头像） */
    @Parcelize
    data class RecentFollow(
        val uid: String? = null,
        val userAvatar: String? = null,
    ) : Parcelable

    /** 点评指向的产品 */
    @Parcelize
    data class RatingTargetInfo(
        val id: String? = null,
        val title: String? = null,
        val logo: String? = null,
        @SerializedName("star_average_score") val starAverageScore: String? = null,
    ) : Parcelable

    @Parcelize
    data class ConfigRow(
        val id: Int?,
        val title: String?,
        val price: Int?,
        val url: String?,
    ) : Parcelable

    @Parcelize
    data class RatingItem(
        val id: String?,
        val name: String?,
        // 5 档文案（很差/较差/一般/不错/很好），按 1..5 星对应
        @SerializedName("star_desc") val starDesc: List<String>? = null,
        @SerializedName("average_score") val averageScore: String? = null,
    ) : Parcelable

    @Parcelize
    data class Feed(
        val id: String?,
        val uid: String?,
        val username: String?,
        val message: String?,
        val pic: String?,
        val url: String?,
    ) : Parcelable

    @Parcelize
    data class Vote(
        val id: String?,
        val type: Int?,
        @SerializedName("start_time") val startTime: Long?,
        @SerializedName("end_time") val endTime: Long?,
        @SerializedName("total_vote_num") val totalVoteNum: Int?,
        @SerializedName("total_comment_num") val totalCommentNum: Int?,
        @SerializedName("total_option_num") val totalOptionNum: Int?,
        @SerializedName("max_select_num") val maxSelectNum: Int?,
        @SerializedName("min_select_num") val minSelectNum: Int?,
        @SerializedName("message_title") val messageTitle: String?,
        val options: List<Option>?,
    ) : Parcelable

    @Parcelize
    data class Option(
        @SerializedName("total_select_num") val totalSelectNum: Long?,
        val id: String?,
        @SerializedName("vote_id") val voteId: String?,
        val title: String?,
        val status: Int?,
        val order: Int?,
        val color: String?
    ) : Parcelable

    @Parcelize
    data class RelationRows(
        val id: String,
        val logo: String?,
        val title: String?,
        val url: String,
        val entityType: String,
    ) : Parcelable

    @Parcelize
    data class TargetRow(
        val id: String?,
        val logo: String?,
        val title: String?,
        val url: String,
        val entityType: String?,
        val targetType: String?
    ) : Parcelable

    @Parcelize
    data class ExtraDataArr(
        val pageTitle: String?,
        val cardPageName: String?
    ) : Parcelable

    @Parcelize
    data class UserInfo(
        val uid: String,
        val username: String?,
        val level: Int?,
        val logintime: Long,
        val regdate: String?,
        val entityType: String?,
        val displayUsername: String?,
        val userAvatar: String?,
        val cover: String?,
        val fans: String?,
        val follow: String?,
        val bio: String?,
        // ---- 认证：列表和详情都下发 ----
        // 1 = 已认证，头像右下角挂角标
        @SerializedName("verify_status") val verifyStatus: Int? = null,
        // 角标图标名，样本里只有 v_green / v_yellow
        @SerializedName("verify_icon") val verifyIcon: String? = null,
        // 完整认证名（如「酷安认证: 酷安员工」），只在个人主页展示
        @SerializedName("verify_title") val verifyTitle: String? = null
    ) : Parcelable

    @Parcelize
    data class TabList(
        val title: String?,
        val url: String?,
        @SerializedName("page_name") val pageName: String?,
        val entityType: String?,
        val entityId: Int?
    ) : Parcelable

    @Parcelize
    data class HomeTabCardRows(
        val entityType: String?,
        val entityTemplate: String?,
        val title: String?,
        val url: String?,
        val entities: List<Entities>?,
        val entityId: String?,
    ) : Parcelable

    @Parcelize
    data class UserAction(
        var like: Int?,
        // 收藏状态：详情接口下发；收藏夹弹窗操作后按 addItem 回的结果写回，所以是可变的
        var favorite: Int?,
        var follow: Int?,
        var collect: Int?,
        var followAuthor: Int?,
        val authorFollowYou: Int?
    ) : Parcelable

    @Parcelize
    data class ReplyRows(
        val id: String?,
        val uid: String?,
        val feedUid: String?,
        val username: String?,
        var message: String?,
        val ruid: String?,
        val rusername: String?,
        val picArr: List<String>?,
        val pic: String?,
        val userInfo: UserInfo?
    ) : Parcelable

    @Parcelize
    data class Entities(
        @SerializedName("device_title") val deviceTitle: String?,
        val dateline: String?,
        val username: String?,
        val url: String,
        val pic: String,
        val title: String,
        val message: String?,
        val logo: String?,
        val id: String?,
        val entityType: String?,
        @SerializedName("alias_title") val aliasTitle: String?,
        val userInfo: UserInfo,
        @SerializedName("target_type_title") val targetTypeTitle: String? = null,
        // ---- 产品页「参数」tab 里 listCard（同价位/同SoC/同系列）的 product 实体字段 ----
        @SerializedName("price_min") val priceMin: String? = null,
        @SerializedName("price_max") val priceMax: String? = null,
        @SerializedName("star_average_score") val starAverageScore: String? = null,
        @SerializedName("star_total_count") val starTotalCount: String? = null,
        @SerializedName("config_name") val configName: String? = null,
        val productSpecs: List<String>? = null,
        // 服务端可能下发对象（有评分项）或空数组 []（没有评分项），见 ProductRatingSpecsAdapter
        @field:JsonAdapter(ProductRatingSpecsAdapter::class)
        val productRatingSpecs: Map<String, String>? = null,
        val description: String? = null,
        // ---- 游戏频道（/v6/page/dataList?url=V15_YOUXI）下发的 topic 实体字段 ----
        // 讨论热度文本（热门新游卡片右下角那个数字）
        @SerializedName("hot_num_txt") val hotNumTxt: String? = null,
        // 发售日期原文，形如 "2026年11月19日"，也可能是 "未公布"
        @SerializedName("release_time") val releaseTime: String? = null,
        // 评分条数（游戏评分卡片显示「N 条」）
        @SerializedName("rating_total_num") val ratingTotalNum: String? = null,
        @SerializedName("commentnum_txt") val commentnumTxt: String? = null,
        // feedListCard（最新点评）下发的就是完整 feed 结构，这里补上点评用得到的几个
        // （message / username / id / url 主构造函数里已经有了，别再声明一遍）
        val ttitle: String? = null,
        // 点评对应的游戏图标
        val tpic: String? = null
    ) : Parcelable

}

/**
 * `product_rating_specs` 的容错解析。
 *
 * 机型对比列表（/v6/page/dataList?url=/product/productList?...）里，同一个字段有两种形式：
 *   - 有评分项的机型下发对象：{"外观质感": "8.5", "性价比": "9.0"}
 *   - 没评分项的新机 / 冷门机型下发**空数组** []
 * 直接声明成 Map<String, String> 时，Gson 读到 [] 会抛
 * `Expected BEGIN_OBJECT but was BEGIN_ARRAY`，整条响应解析失败，
 * 列表页就是一片空白（20 条里只要有 1 条是 [] 就够毁掉整页）。
 * 这里把「不是对象」的情况一律当作没有评分项，不让它影响其余条目。
 */
class ProductRatingSpecsAdapter : JsonDeserializer<Map<String, String>?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Map<String, String>? =
        if (json.isJsonObject) {
            context.deserialize(json, object : TypeToken<Map<String, String>>() {}.type)
        } else {
            null
        }
}

