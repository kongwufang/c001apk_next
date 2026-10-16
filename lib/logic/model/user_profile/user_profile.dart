/// 用户资料响应模型。
///
/// 对应 Android 端 `logic/model/UserProfileResponse.kt`，字段名与接口一致。
/// 接口：`GET /v6/user/profile?uid=<uid>`（见 `NetworkRepo.getProfile`）。
class UserProfile {
  int? status;
  int? error;
  String? message;
  UserProfileData? data;

  UserProfile({
    this.status,
    this.error,
    this.message,
    this.data,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      status: (json['status'] as num?)?.toInt(),
      error: (json['error'] as num?)?.toInt(),
      message: json['message'] as String?,
      data: json['data'] == null
          ? null
          : UserProfileData.fromJson(json['data'] as Map<String, dynamic>),
    );
  }
}

class UserProfileData {
  String uid;
  String username;
  String userAvatar;
  String cover;

  /// 等级（形如 "5"）
  String level;

  /// 当前经验值
  int experience;

  /// 升到下一级所需经验
  int nextLevelExperience;

  /// 动态数 / 关注数 / 粉丝数（接口给的是字符串）
  String feed;
  String follow;
  String fans;

  /// 获赞数
  String beLikeNum;

  /// 认证标题（如「酷安认证: 酷安员工」），`verifyStatus == 1` 时展示
  String? verifyTitle;
  int? verifyStatus;

  /// 认证角标图标名（`v_green` / `v_yellow`），决定角标颜色
  String? verifyIcon;

  String? bio;
  String? province;
  String? city;

  /// 性别：**1 男 / 0 女 / -1 保密**（编辑资料页用，对应 Android
  /// `UserProfileResponse.Data.gender`）
  int? gender;

  /// 生日三段。服务端不下发时是 null（页面上显示「保密」）
  int? birthyear;
  int? birthmonth;
  int? birthday;

  UserProfileData({
    required this.uid,
    required this.username,
    required this.userAvatar,
    this.cover = '',
    this.level = '0',
    this.experience = 0,
    this.nextLevelExperience = 0,
    this.feed = '0',
    this.follow = '0',
    this.fans = '0',
    this.beLikeNum = '0',
    this.verifyTitle,
    this.verifyStatus,
    this.verifyIcon,
    this.bio,
    this.province,
    this.city,
    this.gender,
    this.birthyear,
    this.birthmonth,
    this.birthday,
  });

  factory UserProfileData.fromJson(Map<String, dynamic> json) {
    String str(String key, [String fallback = '0']) {
      final v = json[key];
      if (v == null) return fallback;
      return v.toString();
    }

    return UserProfileData(
      uid: str('uid', ''),
      username: str('username', ''),
      userAvatar: str('userAvatar', ''),
      cover: str('cover', ''),
      level: str('level', '0'),
      experience: (json['experience'] as num?)?.toInt() ?? 0,
      nextLevelExperience:
          (json['next_level_experience'] as num?)?.toInt() ?? 0,
      feed: str('feed'),
      follow: str('follow'),
      fans: str('fans'),
      beLikeNum: str('be_like_num'),
      verifyTitle: json['verify_title'] as String?,
      verifyStatus: (json['verify_status'] as num?)?.toInt(),
      verifyIcon: json['verify_icon'] as String?,
      bio: json['bio'] as String?,
      province: json['province'] as String?,
      city: json['city'] as String?,
      gender: (json['gender'] as num?)?.toInt(),
      birthyear: (json['birthyear'] as num?)?.toInt(),
      birthmonth: (json['birthmonth'] as num?)?.toInt(),
      birthday: (json['birthday'] as num?)?.toInt(),
    );
  }
}
