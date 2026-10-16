import '../../constants/constants.dart';

class Api {
  static const String getHomeFeed =
      '${Constants.URL_API2_SERVICE}/v6/main/indexV8';

  static String getDataList = '${Constants.URL_API_SERVICE}/v6/page/dataList';

  static String getDataFromUrl(String url) {
    return '${Constants.URL_API_SERVICE}$url';
  }

  static String getFeedReply =
      '${Constants.URL_API2_SERVICE}/v6/feed/replyList';

  static String getUserFeed = '${Constants.URL_API_SERVICE}/v6/user/feedList';

  static String getAppInfo = '${Constants.URL_API_SERVICE}/v6/apk/detail';

  static String getSearch = '${Constants.URL_API_SERVICE}/v6/search';

  static String checkLoginInfo =
      '${Constants.URL_API_SERVICE}/v6/account/checkLoginInfo';

  static String getAppsUpdate =
      '${Constants.URL_API_SERVICE}/v6/apk/checkUpdate';

  static String getDyhDetail =
      '${Constants.URL_API_SERVICE}/v6/dyhArticle/list';

  static String getCoolPic = '${Constants.URL_API_SERVICE}/v6/picture/list';

  static String getReply2Reply =
      '${Constants.URL_API_SERVICE}/v6/feed/replyList';

  static String getAppDownloadUrl =
      '${Constants.URL_API_SERVICE}/v6/apk/download';

  static String getLoginParam(String url) {
    return '${Constants.URL_ACCOUNT_SERVICE}$url';
  }

  static String onLogin =
      '${Constants.URL_ACCOUNT_SERVICE}/auth/loginByCoolApk';

  static String getProfile = '${Constants.URL_API_SERVICE}/v6/user/profile';

  static String checkCount =
      '${Constants.URL_API_SERVICE}/v6/notification/checkCount';

  static String postReply = '${Constants.URL_API_SERVICE}/v6/feed/reply';

  static String postCreateFeed =
      '${Constants.URL_API_SERVICE}/v6/feed/createFeed';

  static String postRequestValidate =
      '${Constants.URL_API_SERVICE}/v6/account/requestValidate';

  // ===== 其他屏蔽项（关键字 / 用户 / 节点）：读 spamWordList，写 updateConfig =====
  // 对应 Android `ApiService.getSpamWordList` / `updateConfig`
  static String getSpamWordList =
      '${Constants.URL_API_SERVICE}/v6/user/spamWordList';

  static String postUpdateConfig =
      '${Constants.URL_API_SERVICE}/v6/account/updateConfig';

  // ===== 收藏夹（多收藏夹）=====
  // 对应 Android `ApiService` 的「===== 收藏夹（多收藏夹）=====」一段。
  //
  // 列表：带 `id=<动态id>&type=feed&showDefault=1` 时会下发 `isBeCollected`
  //       （1=该动态已在此夹），弹窗靠它画勾。
  static String getCollectionList =
      '${Constants.URL_API_SERVICE}/v6/collection/list';

  /// 收藏：`id=夹id`、`cancelId` 空；取消：`id` 空、`cancelId=夹id`
  static String postCollectionAddItem =
      '${Constants.URL_API_SERVICE}/v6/collection/addItem';

  /// 新建（**multipart**）
  static String postCollectionCreate =
      '${Constants.URL_API_SERVICE}/v6/collection/create';

  /// 修改（表单）：标题 / 简介 / 封面 / 公开或私密
  static String postCollectionUpdate =
      '${Constants.URL_API_SERVICE}/v6/collection/update';

  /// 封面图上传（multipart）
  static String postCollectionUploadImage =
      '${Constants.URL_API_SERVICE}/v6/collection/uploadImage';

  static String getCollectionDetail =
      '${Constants.URL_API_SERVICE}/v6/collection/detail';

  static String getCollectionCheckCount =
      '${Constants.URL_API_SERVICE}/v6/collection/checkCount';

  /// 清除收藏夹内无效内容（服务端异步执行）
  static String postCollectionRemoveUnUseItem =
      '${Constants.URL_API_SERVICE}/v6/collection/removeUnUseItem';

  static String postCollectionDelete =
      '${Constants.URL_API_SERVICE}/v6/collection/delete';

  // ===== 云端浏览历史 =====
  //
  // 对应 Android `ApiService.getHitHistoryList`。
  // ★ 浏览记录由**服务端**在打开详情（feed / topic / product / apk detail）时
  //   自动写入，客户端**没有**上报接口（Android 已实测验证）。
  static String getHitHistoryList =
      '${Constants.URL_API_SERVICE}/v6/user/hitHistoryList';

  // ===== 编辑资料 =====
  //
  // 对应 Android `ApiService` 的 changeProfile / resetProfile /
  // changeAvatar / changeAvatarCover。改动都是「**点一下存一下**」——
  // 和官方一样，每项单独走一次接口（见 EditProfileViewModel 的注释）。
  static String postChangeProfile =
      '${Constants.URL_API_SERVICE}/v6/account/changeProfile';

  /// 生日 / 地区回到「保密」：`key=birth` 或 `key=location`（Android 抓包核对）
  static String postResetProfile =
      '${Constants.URL_API_SERVICE}/v6/account/resetProfile';

  /// 换头像：multipart，part 名固定 **`imgFile`**、filename 用图片 **md5（无扩展名）**
  static String postChangeAvatar =
      '${Constants.URL_API_SERVICE}/v6/account/changeAvatar';

  /// 换主页背景图：先 OSS 上传再把**完整 CDN 地址**回传
  static String postChangeAvatarCover =
      '${Constants.URL_API_SERVICE}/v6/account/changeAvatarCover';
}
