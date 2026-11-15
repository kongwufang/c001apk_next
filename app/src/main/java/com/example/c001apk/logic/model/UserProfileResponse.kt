package com.example.c001apk.logic.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class UserProfileResponse(
    val status: Int?,
    val error: Int?,
    val message: String?,
    val messageStatus: Int?,
    val data: Data?
) {
    @Parcelize
    data class Data(
        val uid: String,
        val gender: Int,
        val regdate: Long,
        val cover: String,
        var isFollow: Int?,
        val bio: String?,
        @SerializedName("be_like_num") val beLikeNum: String,
        val logintime: Long,
        val feed: String,
        val follow: String,
        val fans: String,
        val username: String,
        val userAvatar: String,
        val level: String,
        val experience: Int,
        @SerializedName("next_level_experience") val nextLevelExperience: Int,

        // ---- 个人主页头部展示字段 ----
        // 认证标题，如「酷安认证: 酷安员工」；verify_status == 1 时展示
        @SerializedName("verify_title") val verifyTitle: String? = null,
        @SerializedName("verify_status") val verifyStatus: Int? = null,
        // 认证角标图标名（v_green / v_yellow），用来定头部那枚角标的颜色
        @SerializedName("verify_icon") val verifyIcon: String? = null,
        val province: String? = null,
        val city: String? = null,
        // 生日（编辑资料用）；0 表示未设置 / 保密
        val birthyear: Int? = null,
        val birthmonth: Int? = null,
        val birthday: Int? = null,
        // 全部动态数（列表头「全部动态（6164）」）
        val apkRatingNum: Int? = null,
        // 拥有装备数（「他的装备 58个装备」）
        @SerializedName("product_owner_count") val productOwnerCount: Int? = null,
        // Official profile header uses exactly disallowPost == 1 for its silenced badge.
        @SerializedName("disallowPost") val disallowPost: Int? = null,
    ) : Parcelable {
        val isSilenced: Boolean
            get() = disallowPost == 1
    }
}

