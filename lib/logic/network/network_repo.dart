import 'package:dio/dio.dart';
import 'package:get/get.dart' hide Response, FormData;
import 'package:get/get_connect/http/src/status/http_status.dart';

import '../../constants/constants.dart';
import '../../logic/model/feed/data_list_model.dart';
import '../../logic/model/feed/data_model.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/network/api.dart';
import '../../logic/network/request.dart';
import '../../logic/state/loading_state.dart';
import '../../utils/extensions.dart';
import '../../utils/storage_util.dart';
import '../../utils/token_util.dart';
import '../../components/error_view.dart';
import 'api_error.dart';

class NetworkRepo {
  /// 其他屏蔽项（关键字 / 用户 / 节点）的配置。
  ///
  /// 对应 Android `ApiService.getSpamWordList`：`GET /v6/user/spamWordList`，
  /// 返回的 `data.spam_word_config` 是一个 **JSON 字符串**（还要再解一层）。
  static Future<Response> getSpamWordList() async {
    return Request().get(Api.getSpamWordList);
  }

  /// 写配置。对应 Android `ApiService.updateConfig`：
  /// `POST /v6/account/updateConfig`，**urlencoded 表单**，字段是 `key` + `value`。
  ///
  /// ⚠️ `value` 本身是一段 JSON 字符串；且**关键字类的写键是 `word`，
  /// 而读回来的键是 `custom`**（服务端读写不对称，见 [SpamConfigRepo]）。
  static Future<Response> postUpdateConfig({
    required String key,
    required String value,
  }) async {
    return Request().post(
      Api.postUpdateConfig,
      data: {'key': key, 'value': value},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  // ===== 收藏夹（多收藏夹）=====
  // 对应 Android `NetworkRepo` 的同名方法。

  /// 收藏夹列表。
  ///
  /// 传了 [id]（动态 id）+ `type=feed` + `showDefault=1` 时，
  /// 每个夹会带 `isBeCollected`（1=该动态已在此夹），弹窗靠它画勾。
  static Future<Response> getCollectionList({
    String uid = '',
    String id = '',
    String type = 'feed',
    int showDefault = 1,
    int page = 1,
  }) async {
    return Request().get(
      Api.getCollectionList,
      queryParameters: {
        'uid': uid,
        'id': id,
        'type': type,
        'showDefault': showDefault,
        'page': page,
      },
    );
  }

  /// 收藏 / 取消收藏。
  ///
  /// 收藏：`id=夹id`、`cancelId` 传空串；
  /// 取消：`id` 传空串、`cancelId=夹id`。
  static Future<Response> postCollectionAddItem({
    required String id,
    required String cancelId,
    required String targetId,
    String type = 'feed',
  }) async {
    return Request().post(
      Api.postCollectionAddItem,
      data: {
        'id': id,
        'cancelId': cancelId,
        'targetId': targetId,
        'type': type,
      },
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  /// 新建收藏夹（multipart）。
  ///
  /// [pic] 是**已上传的 OSS URL**（没传封面就传空串）。
  static Future<Response> postCollectionCreate({
    required String isOpen,
    required String pic,
    required String description,
    required String title,
    String sourceId = '',
  }) async {
    final data = FormData.fromMap({
      'isOpen': isOpen,
      'pic': pic,
      'description': description,
      'title': title,
      'sourceId': sourceId,
    });
    return Request().post(
      Api.postCollectionCreate,
      data: data,
      options: Options(contentType: Headers.multipartFormDataContentType),
    );
  }

  /// 修改收藏夹（表单）：标题 / 简介 / 封面 / 公开或私密
  static Future<Response> postCollectionUpdate({
    required String id,
    required String title,
    required String description,
    required String pic,
    required int isOpen,
  }) async {
    return Request().post(
      Api.postCollectionUpdate,
      data: {
        'id': id,
        'title': title,
        'description': description,
        'pic': pic,
        'isOpen': isOpen,
      },
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  /// 封面图上传（multipart）。
  ///
  /// `fieldName` / `uploadDir` 的取值抄自 Android
  /// `NetworkRepo.uploadCollectionImage`：**`picFile` / `feed_image`**。
  ///
  /// ⚠️ [data] 里的文件名**必须带扩展名**、part 的 Content-Type **必须具体**
  /// （`image/jpeg` 而非 `image/*`），否则服务端返回 103「请选择正确的文件类型」
  /// —— 这是 Android 端 curl 实测出来的，见 `CollectionPickViewModel.upload` 的注释。
  static Future<Response> postCollectionUploadImage({
    required String fileMd5,
    required FormData data,
  }) async {
    return Request().post(
      Api.postCollectionUploadImage,
      queryParameters: {
        'fieldName': 'picFile',
        'uploadDir': 'feed_image',
        'fileMd5': fileMd5,
      },
      data: data,
      options: Options(contentType: Headers.multipartFormDataContentType),
    );
  }

  static Future<Response> getCollectionCheckCount() async {
    return Request().get(Api.getCollectionCheckCount);
  }

  static Future<Response> getCollectionDetail({required String id}) async {
    return Request().get(
      Api.getCollectionDetail,
      queryParameters: {'id': id},
    );
  }

  /// 清除收藏夹内无效内容（服务端异步执行，约 5 分钟后生效）
  static Future<Response> postCollectionRemoveUnUseItem({
    required String colId,
  }) async {
    return Request().post(
      Api.postCollectionRemoveUnUseItem,
      data: {'colId': colId},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  static Future<Response> postCollectionDelete({required String id}) async {
    return Request().post(
      Api.postCollectionDelete,
      data: {'id': id},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  /// 酷安**云端**浏览历史。
  ///
  /// 对应 Android `NetworkRepo.getHitHistoryList`。
  ///
  /// ★ **分页参数照抄官方**（Android 注释）：
  /// 首页只带 `page`；后续页带**首页首条**的 id 当 `firstItem` +
  /// **上一页末条**的 id 当 `lastItem`。
  static Future<Response> getHitHistoryList({
    required int page,
    String? firstItem,
    String? lastItem,
  }) async {
    return Request().get(
      Api.getHitHistoryList,
      queryParameters: {
        'page': page,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
      },
    );
  }

  // ===== 编辑资料 =====
  // 对应 Android `NetworkRepo` 的 changeProfile / resetProfile /
  // changeAvatar / changeAvatarCover。

  /// 改资料（`key` + `value` 表单）。
  ///
  /// ⚠️ **生日和地区的 `key` 是空字符串**，值才是 JSON：
  /// - 生日 `value = {"birthyear":y,"birthmonth":m,"birthday":d}`
  /// - 地区 `value = {"province":..,"city":..}`
  ///
  /// 其余：性别 `gender`（1 男 / 0 女 / -1 保密）、签名 `bio`。
  static Future<Response> postChangeProfile({
    required String key,
    required String value,
  }) async {
    return Request().post(
      Api.postChangeProfile,
      data: {'key': key, 'value': value},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  /// 生日 / 地区回到「保密」（`key=birth` 或 `key=location`）
  static Future<Response> postResetProfile({required String key}) async {
    return Request().post(
      Api.postResetProfile,
      data: {'key': key},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  /// 换头像（multipart）。
  ///
  /// ⚠️ 与收藏夹封面上传的规则**不一样**（两边都是抓包实测的）：
  /// - 这里：part 名 **`imgFile`**、filename 用 **md5（无扩展名）**、Content-Type `image/jpeg`
  /// - 收藏封面：part 名 `picFile`、filename `$md5.$ext`、Content-Type 具体 `image/xxx`
  static Future<Response> postChangeAvatar({required FormData data}) async {
    return Request().post(
      Api.postChangeAvatar,
      data: data,
      options: Options(contentType: Headers.multipartFormDataContentType),
    );
  }

  /// 换主页背景图：`url` 传 **完整 CDN 地址**（`$prefix/$fileName`）
  static Future<Response> postChangeAvatarCover({required String url}) async {
    return Request().post(
      Api.postChangeAvatarCover,
      data: {'url': url},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  static Future<Response> postOSSUploadPrepare({
    required FormData data,
  }) async {
    return Request().post(Api.getDataFromUrl('/v6/upload/ossUploadPrepare'),
        data: data,
        options: Options(contentType: Headers.multipartFormDataContentType));
  }

  static Future<Response> sendMessage({
    required dynamic uid,
    required FormData data,
  }) async {
    return Request().post(
      Api.getDataFromUrl('/v6/message/send'),
      queryParameters: {
        'uid': uid,
      },
      data: data,
    );
  }

  static Future<Response> getImageUrl({
    required dynamic id,
  }) async {
    return Request().get(
      Api.getDataFromUrl('/v6/message/showImage'),
      queryParameters: {
        'id': id,
        'type': 's',
      },
      options: Options(followRedirects: false),
    );
  }

  static Future<Response> deleteMsg(
    String url, {
    dynamic ukey,
    dynamic id,
  }) async {
    return Request().get(
      Api.getDataFromUrl(url),
      queryParameters: {
        if (ukey != null) 'ukey': ukey,
        if (id != null) 'id': id,
      },
    );
  }

  static Future<LoadingState> messageOperation(
    String url, {
    String? ukey,
    String? uid,
    int? page,
    dynamic firstItem,
    dynamic lastItem,
  }) async {
    return getListData(
      () => Request().get(
        Api.getDataFromUrl(url),
        queryParameters: {
          if (ukey != null) 'ukey': ukey,
          if (uid != null) 'uid': uid,
          if (page != null) 'page': page,
          if (firstItem != null) 'firstItem': firstItem,
          if (lastItem != null) 'lastItem': lastItem,
        },
      ),
    );
  }

  static Future<Response> getFollow(
    String url, {
    String? tag,
    String? id,
  }) async {
    return Request().get(
      Api.getDataFromUrl(url),
      queryParameters: {
        if (tag != null) 'tag': tag,
        if (id != null) 'id': id,
      },
    );
  }

  static Future<Response> postLikeDeleteFollow(
    String url, {
    dynamic id,
    dynamic uid,
    FormData? data,
  }) async {
    return Request().post(
      Api.getDataFromUrl(url),
      queryParameters: {
        if (id != null) 'id': id,
        if (uid != null) 'uid': uid,
      },
      data: data,
    );
  }

  static Future<Response> postRequestValidate(
    FormData data,
  ) async {
    return Request().post(
      Api.postRequestValidate,
      data: data,
    );
  }

  /// 个人主页**置顶 / 取消置顶**一条动态。
  ///
  /// 对齐 Android `ApiService`：
  /// ```kotlin
  /// // 三个都是表单字段，成功返回 data="个人主页置顶成功"。
  /// @POST("/v6/feed/addTopToNode")
  /// @FormUrlEncoded
  /// fun addTopToNode(@Field("nodeType") nodeType: String,
  ///                  @Field("nodeId") nodeId: String,
  ///                  @Field("feedId") feedId: String): Call<LikeReplyResponse>
  ///
  /// // 取消个人主页置顶，参数同 addTopToNode，成功返回 data="个人主页取消置顶成功"
  /// @POST("/v6/feed/cancelTopFromNode")
  /// ```
  ///
  /// 调用方（`BaseAppViewModel.onPostStickTop`）传的是
  /// `nodeType = "member"`、`nodeId = PrefManager.uid`（**自己的 uid**）、`feedId = id`。
  ///
  /// [isStickTop] 是**当前**状态：已置顶就调取消接口，未置顶就调置顶接口
  /// （与 Android 的分支完全一致）。
  static Future<Response> postStickTop({
    required String feedId,
    required bool isStickTop,
    required String myUid,
  }) async {
    return Request().post(
      isStickTop
          ? '${Constants.URL_API_SERVICE}/v6/feed/cancelTopFromNode'
          : '${Constants.URL_API_SERVICE}/v6/feed/addTopToNode',
      // ★ 用**普通 Map** 而不是 `FormData` —— 这样 dio 发的是
      // `application/x-www-form-urlencoded`，与 Android 的 `@FormUrlEncoded` 一致。
      // （`postLikeDeleteFollow` 那边用 `FormData` 是因为它要传 multipart；
      //   置顶这三个字段是纯文本表单字段，跟 Android 对齐更稳。）
      data: {
        'nodeType': 'member',
        'nodeId': myUid,
        'feedId': feedId,
      },
    );
  }

  static Future<Response> getValidateCaptcha() async {
    return Request().get(
      '${Constants.URL_API_SERVICE}/v6/account/captchaImage?${DateTime.now().microsecondsSinceEpoch ~/ 1000}&w=270=&h=113',
      options: Options(responseType: ResponseType.bytes),
    );
  }

  static Future<Response> postCreateFeed(
    FormData data,
  ) async {
    return Request().post(
      Api.postCreateFeed,
      data: data,
    );
  }

  /// 酷安播放器解析：把 `requestParams` 换成能播的地址。
  ///
  /// 对应 Android `NetworkRepo.getVideoUrl` / `ApiService.getVideoUrl`。
  /// 返回 `{"data":{"urlList":[...],"audioList":[...]}}`，
  /// **失败时 `data` 是个数组 `[]`** —— 所以调用方要自己判形状
  /// （`FeedVideo.withResolved` 就是干这个的）。
  static Future<Response> postVideoUrl({required String params}) async {
    return Request().post(
      Api.postVideoUrl,
      data: {'params': params},
      options: Options(contentType: Headers.formUrlEncodedContentType),
    );
  }

  static Future<Response> postReply(
    FormData data,
    dynamic id,
    String type,
  ) async {
    return Request().post(
      Api.postReply,
      data: data,
      queryParameters: {'id': id, 'type': type},
    );
  }

  static Future<Response> checkCount() async {
    return Request().get(Api.checkCount);
  }

  static Future<Response> getProfile(String uid) async {
    return Request().get(Api.getProfile, queryParameters: {'uid': uid});
  }

  /// 官方酷安 App 的详情（用于**跟随官方最新版本号**）。
  ///
  /// 对应 Android `ApiService.getAppInfo(id, installed)`：
  /// `GET /v6/apk/detail?id=com.coolapk.market&installed=1`
  /// 返回体里的 `data.apkversionname` / `data.apkversioncode` 就是官方当前版本。
  static Future<Response> apkDetail(String id) async {
    return Request().get(Api.getAppInfo,
        queryParameters: {'id': id, 'installed': 1});
  }

  static Future<Response> onLogin(
    String requestHash,
    String account,
    String pwd,
    String captcha,
  ) async {
    return Request().post(Api.onLogin,
        data: FormData.fromMap({
          'submit': '1',
          'randomNumber': TokenUtils.createRandomNumber(),
          'requestHash': requestHash,
          'login': account,
          'password': pwd,
          'captcha': captcha,
          'code': '',
        }),
        options: Options(contentType: Headers.formUrlEncodedContentType));
  }

  static Future<Response> getLoginParam(String url, [Options? options]) async {
    return Request().get(Api.getLoginParam(url), options: options);
  }

  static Future<LoadingState> getReply2Reply({
    required String id,
    required int page,
    required String? firstItem,
    required String? lastItem,
  }) async {
    return getListData(
      () => Request().get(Api.getReply2Reply, queryParameters: {
        'id': id,
        'page': page,
        'listType': '',
        'discussMode': 0,
        'feedType': 'feed_reply',
        'blockStatus': 0,
        'fromFeedAuthor': 0,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
      }),
    );
  }

  static Future<LoadingState> getCoolPic({
    required String tag,
    required String type,
    required int page,
    required String? firstItem,
    required String? lastItem,
  }) async {
    return getListData(
      () => Request().get(Api.getCoolPic, queryParameters: {
        'tag': tag,
        'type': type,
        'page': page,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
      }),
    );
  }

  static Future<LoadingState> getDyhDetail({
    required String dyhId,
    required String type,
    required int page,
    required String? firstItem,
    required String? lastItem,
  }) async {
    return getListData(
      () => Request().get(Api.getDyhDetail, queryParameters: {
        'dyhId': dyhId,
        'type': type,
        'page': page,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
      }),
    );
  }

  static Future<Response> getAppsUpdate(
    String pkgs,
  ) async {
    return Request().post(
      Api.getAppsUpdate,
      queryParameters: {'coolmarket_beta': '0'},
      data: FormData.fromMap({
        'pkgs': pkgs,
      }),
    );
  }

  static Future<Response> checkLoginInfo() async {
    return Request().get(Api.checkLoginInfo);
  }

  static Future<LoadingState> getSearch({
    required String type,
    required String feedType,
    required String sort,
    required String searchValue,
    required int isStrict,
    required String? pageType,
    required String? pageParam,
    required int page,
    required String? firstItem,
    required String? lastItem,
  }) async {
    return getListData(
      () => Request().get(Api.getSearch, queryParameters: {
        'type': type,
        'feedType': feedType,
        'sort': sort,
        'searchValue': searchValue,
        'isStrict': isStrict,
        if (!pageType.isNullOrEmpty) 'pageType': pageType,
        if (!pageParam.isNullOrEmpty) 'pageParam': pageParam,
        'page': page,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
        'showAnonymous': -1,
      }),
    );
  }

  static Future<Response> getAppDownloadUrl({
    required String packageName,
    required int id,
    required int versionCode,
  }) async {
    return Request().get(
      Api.getAppDownloadUrl,
      queryParameters: {
        'pn': packageName,
        'aid': id,
        'vc': versionCode,
        'extra': '',
      },
      options: Options(followRedirects: false),
    );
  }

  static Future<LoadingState> getAppInfo({
    required String id,
  }) async {
    return getData(
      () => Request().get(Api.getAppInfo, queryParameters: {
        'id': id,
        'installed': 1,
      }),
    );
  }

  static Future<LoadingState> getUserFeed({
    required String uid,
    required int page,
    required String? firstItem,
    required String? lastItem,
  }) async {
    return getListData(
      () => Request().get(Api.getUserFeed, queryParameters: {
        'uid': uid,
        'page': page,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
        'showAnonymous': 0,
        'isIncludeTop': 1,
        'showDoing': 0,
      }),
    );
  }

  static Future<LoadingState> getFeedReply({
    required String id,
    required String listType,
    required int page,
    required String? firstItem,
    required String? lastItem,
    required int discussMode,
    required String feedType,
    required int blockStatus,
    required int fromFeedAuthor,
  }) async {
    return getListData(
      () => Request().get(Api.getFeedReply, queryParameters: {
        'id': id,
        'listType': listType,
        'page': page,
        if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
        if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
        'discussMode': discussMode,
        'feedType': feedType,
        'blockStatus': blockStatus,
        'fromFeedAuthor': fromFeedAuthor,
      }),
    );
  }

  /// 搜索联想。
  ///
  /// 不走 `getData`（那个会包成 `LoadingState` 并要求 `data` 是 Datum 列表 ✗）
  /// —— 联想的响应形状不一样 ✓ 这里只取 `data` 数组 ✓
  static Future<List<SearchSuggestData>> getSuggestSearchWords(
      String keyword) async {
    final response = await Request().get(
      Api.getSuggestSearchWords,
      queryParameters: {'searchValue': keyword, 'type': 'app'},
    );
    final body = response.data;
    if (body is! Map) return [];
    final list = body['data'];
    if (list is! List) return [];
    return list
        .whereType<Map>()
        .map((e) => SearchSuggestData.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  static Future<LoadingState> getDataFromUrl({
    required String url,
    Map<String, dynamic>? data,
  }) async {
    return getData(
      () => Request().get(Api.getDataFromUrl(url), queryParameters: data),
    );
  }

  static Future<LoadingState> getDataListFromUrl({
    required String url,
    Map<String, dynamic>? data,
  }) async {
    return getListData(
      () => Request().get(
        Api.getDataFromUrl(url),
        queryParameters: data,
      ),
    );
  }

  static Future<LoadingState> getHomeFeed({
    required int page,
    required int firstLaunch,
    required String installTime,
    required String? firstItem,
    required String? lastItem,
  }) async {
    return getListData(
      () => Request().get(
        Api.getHomeFeed,
        queryParameters: {
          'page': page,
          'firstLaunch': firstLaunch,
          'installTime': installTime,
          if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
          if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
          'ids': '',
        },
      ),
    );
  }

  static Future<LoadingState> getDataList({
    required String url,
    required String title,
    required String subTitle,
    required String? firstItem,
    required String? lastItem,
    required int page,
    bool inCluldeConfigCard = false,
    /// 是否放行 `entityType == 'card'` 的条目（`sortSelectCard` 除外）。
    ///
    /// 只有**话题 / 产品内容列表**需要（产品页「参数」tab 的
    /// `productConfigList` / `listCard` 都是 card）。
    /// 对齐 Android `TopicContentViewModel` 的过滤条件。
    bool includeCardEntities = false,
  }) async {
    return getListData(
      () => Request().get(
        Api.getDataList,
        queryParameters: {
          'url': url,
          'title': title,
          'subTitle': subTitle,
          if (!firstItem.isNullOrEmpty) 'firstItem': firstItem,
          if (!lastItem.isNullOrEmpty) 'lastItem': lastItem,
          'page': page,
        },
      ),
      inCluldeConfigCard: inCluldeConfigCard,
      includeCardEntities: includeCardEntities,
    );
  }

  static Future<LoadingState> getData(
      Future<Response<dynamic>> Function() get) async {
    try {
      var response = await get();
      // ★ 必须 `await`：`handleDataResponse` 本身是 async，
      //   不加 await 的话它内部抛的异常会**逃出 try**，
      //   `catch` 接不到 ⇒ 调用方拿不到 `LoadingState.error`，
      //   而是变成一个未处理的 Future 错误。
      //   （下面 `getListData` 一直是对的：它写的是 `return await handleListResponse(...)`。）
      return await handleDataResponse(response);
    } catch (err) {
      // ★ 统一走 ApiError ✓ —— 从响应体的 message 取人话 ✓
      return LoadingState.error(_errText(err));
    }
  }

  static Future<LoadingState> getListData(
    Future<Response<dynamic>> Function() get, {
    bool inCluldeConfigCard = false,
    bool includeCardEntities = false,
  }) async {
    try {
      var response = await get();
      return await handleListResponse(
        response,
        inCluldeConfigCard: inCluldeConfigCard,
        includeCardEntities: includeCardEntities,
      );
    } catch (err) {
      return LoadingState.error(_errText(err));
    }
  }

  /// 把任意异常转成一句人话 ✓
  ///
  /// **这是整个 App 错误文案的唯一出口** —— UI 层不再自己猜 ✓
  /// - `DioException` ⇒ 优先用它响应体里的 `message` ✓ 没有才按类型分类 ✓
  /// - 其它异常   ⇒ `toString()` 但去掉 `Exception: ` 前缀 ✓
  static String _errText(Object err) {
    if (err is DioException) {
      return apiErrorToMessage(ApiError.fromDio(err)); // 标题 + 状态码/路径 ✓
    }
    final s = err.toString();
    return s.startsWith('Exception: ') ? s.substring(11) : s;
  }

  static Future<LoadingState> handleDataResponse(
      Response<dynamic> response) async {
    if (response.statusCode == HttpStatus.ok) {
      DataModel responseData = DataModel.fromJson(response.data);
      if (!responseData.message.isNullOrEmpty) {
        return LoadingState.error(responseData.message!);
      } else {
        if (responseData.data != null) {
          return LoadingState.success(responseData.data);
        } else {
          return LoadingState.empty();
        }
      }
    } else {
      // ★ 非 200 时**也要**从响应体里找 message ✓（原来只给状态码 ✗ 用户看不懂 ✗）
      return LoadingState.error(
          apiErrorToMessage(ApiError.fromResponse(response)));
    }
  }

  static Future<LoadingState> handleListResponse(
    Response<dynamic> response, {
    bool inCluldeConfigCard = false,
    bool includeCardEntities = false,
  }) async {
    if (response.statusCode == HttpStatus.ok) {
      DataListModel responseData = DataListModel.fromJson(response.data);
      if (!responseData.message.isNullOrEmpty) {
        return LoadingState.error(responseData.message!);
      } else {
        if (!responseData.data.isNullOrEmpty) {
          List<String> userBlackList = GStorage.blackList
              .get(BlackListBoxKey.userBlackList, defaultValue: <String>[]);
          List<String> topicBlackList = GStorage.blackList
              .get(BlackListBoxKey.topicBlackList, defaultValue: <String>[]);
          List<Datum> filterList = responseData.data!.where((item) {
            return item.entityTemplate == 'refreshCard' &&
                    item.title.orEmpty.contains('没有')
                ? true
                : (Constants.entityTypeList.contains(item.entityType) ||
                        // ★ 话题 / 产品内容列表要放行 `entityType == 'card'`
                        //   （`sortSelectCard` 除外）。
                        //
                        //   对齐 Android `TopicContentViewModel`：
                        //   ```kotlin
                        //   // 「card」类型也要放行：产品页「参数」tab 的
                        //   // productConfigList/listCard 都是 card，过滤掉会整页空白
                        //   if (it.entityType in listOf("feed","topic","product","user","card")
                        //       && it.entityTemplate != "sortSelectCard")
                        //   ```
                        //
                        //   不加这条的话，产品页「参数」tab 的 4 条
                        //   （productConfigList + 2 个 listCard）会被全部过滤掉，
                        //   列表显示成「没有更多了」。
                        (includeCardEntities &&
                                item.entityType == 'card' &&
                                item.entityTemplate != 'sortSelectCard') ||
                        (Constants.entityTemplateList +
                                (inCluldeConfigCard ? ['configCard'] : []))
                            .contains(item.entityTemplate)) &&
                    !userBlackList.contains(item.uid.toString()) &&
                    topicBlackList.firstWhereOrNull((keyword) =>
                            '${item.tags},${item.ttitle},${item.relationRows?.getOrNull(0)?.title}'
                                .contains(keyword)) ==
                        null;
          }).toList();
          return LoadingState.success(filterList);
        } else {
          return LoadingState.empty();
        }
      }
    } else {
      return LoadingState.error(
          apiErrorToMessage(ApiError.fromResponse(response)));
    }
  }
}
