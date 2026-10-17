import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/common_body.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';

/// 个人主页的 tab。
///
/// 照抄 Android `UserPagerFragment`：
/// ```kotlin
/// tabType  = listOf("feed", "rating", "article", "question", "coolpic")
/// tabTitle = listOf("动态", "点评", "图文", "问答", "酷图")
/// ```
enum UserTabType {
  FEED('动态', 'feed'),
  RATING('点评', 'rating'),
  ARTICLE('图文', 'article'),
  QUESTION('问答', 'question'),
  COOLPIC('酷图', 'coolpic');

  const UserTabType(this.label, this.type);

  final String label;
  final String type;

  /// 这个 tab 是否要让 `feedCover` **整页按完整动态卡排**。
  ///
  /// 照抄 Android `UserTabViewModel`：
  /// > 个人主页「动态」「图文」两个 tab 整页统一按完整动态卡渲染。
  /// > 这两个接口都会在同一个列表里混下发两种模板（htmlFeedList 实测 17 条里
  /// > 6 条 entityTemplate=feedCover、11 条 feed），照模板分派会在一页里混出
  /// > 「紧凑封面卡 / 完整动态卡」两种排法。要求是这一页的文章跟同页普通动态长一样。
  ///
  /// ```kotlin
  /// override val feedCoverAsFeed get() = type == "article" || type == "feed"
  /// ```
  bool get feedCoverAsFeed => this == ARTICLE || this == FEED;

  /// 该 tab 的数据**是否要走 `/v6/page/dataList?url=...`**。
  ///
  /// ★ 这一点很容易搞错：Android `UserTabViewModel.request()` 里，
  /// 「动态 / 图文 / 问答」传的是**真实路径**（`/v6/user/feedList` 等），
  /// 直接 `@GET` 那几条接口；
  /// 而「点评 / 酷图」传的是
  /// `/feed/nodeRatingList?uid=..&targetType=all&parseRatingToFeed=1`、
  /// `/feed/userCoolPictureFeedList?fragmentTemplate=flex&uid=..` ——
  /// 这两个是**页面 URL**，要当 `url` **查询参数**塞进
  /// `GET /v6/page/dataList`（Android `ApiService.getDataList` 是
  /// `@GET("/v6/page/dataList")` + `@Query("url")`，不是 `@Url`）。
  ///
  /// 我之前直接把 `/feed/nodeRatingList` 当路径打，服务端回 `API unsupported`，
  /// 列表解析直接抛 `type 'String' is not a subtype of type 'Map<String, dynamic>'`。
  bool get viaPageDataList => this == RATING || this == COOLPIC;

  /// 该 tab 的数据接口。
  ///
  /// 照抄 Android `UserTabViewModel.request()` 的 when 分支：
  /// - 动态 `GET /v6/user/feedList?showAnonymous=0&isIncludeTop=1&showDoing=0`（支持 lastItem）
  /// - 图文 `GET /v6/user/htmlFeedList`（支持 lastItem）
  /// - 问答 `GET /v6/user/questionAndAnswerList`（支持 lastItem）
  /// - 点评 `/feed/nodeRatingList?uid=..&targetType=all&parseRatingToFeed=1`（**走 page/dataList**）
  /// - 酷图 `/feed/userCoolPictureFeedList?fragmentTemplate=flex&uid=..`（**走 page/dataList**）
  String url(String uid) => switch (this) {
        // ⚠️ 这三个必须带 `uid`！
        //
        // Android `ApiService.kt` 里 `uid` 是 **`@Query`** 参数：
        // ```kotlin
        // @GET("/v6/user/feedList?showAnonymous=0&isIncludeTop=1&showDoing=0")
        // fun getUserFeed(@Query("uid") uid: String, @Query("page") page: Int,
        //                 @Query("lastItem") lastItem: String?)
        // @GET("/v6/user/htmlFeedList")          … @Query("uid") uid: String
        // @GET("/v6/user/questionAndAnswerList") … @Query("uid") uid: String
        // ```
        //
        // 之前这里**漏了 `uid`** ⇒ 服务端按「当前登录用户」返回
        // ⇒ 进别人主页时，下面的动态 / 图文 / 问答显示的是**自己的内容** ✗
        UserTabType.FEED => '/v6/user/feedList'
            '?showAnonymous=0&isIncludeTop=1&showDoing=0&uid=$uid',
        UserTabType.RATING =>
          '/feed/nodeRatingList?uid=$uid&targetType=all&parseRatingToFeed=1',
        UserTabType.ARTICLE => '/v6/user/htmlFeedList?uid=$uid',
        UserTabType.QUESTION => '/v6/user/questionAndAnswerList?uid=$uid',
        UserTabType.COOLPIC =>
          '/feed/userCoolPictureFeedList?fragmentTemplate=flex&uid=$uid',
      };

  /// 「点评」独有的实体类型（Android：`type == "rating" && it.entityType == "nodeRating"`）
  String? get extraEntityType => this == RATING ? 'nodeRating' : null;
}

/// 个人主页某个 tab 的数据控制器。
///
/// 直接复用 [CommonController] 的分页 / 黑名单 / 点赞 / 关注那一整套 ——
/// 与 Android 让 `UserTabViewModel` 继承 `BaseAppViewModel` 是同一个思路。
class UserTabController extends CommonController {
  UserTabController({required this.uid, required this.type});

  final String uid;
  final UserTabType type;

  @override
  Future<LoadingState> customGetData() {
    // 「点评 / 酷图」走 `/v6/page/dataList?url=...`，其余三个是真实路径。
    // 详见 [UserTabType.viaPageDataList] 的注释（这里踩过坑）。
    if (type.viaPageDataList) {
      return NetworkRepo.getDataList(
        url: type.url(uid),
        title: '',
        subTitle: '',
        firstItem: firstItem,
        lastItem: lastItem,
        page: page,
      );
    }
    return NetworkRepo.getDataListFromUrl(
      url: type.url(uid),
      data: {
        'page': page,
        if (firstItem != null) 'firstItem': firstItem,
        if (lastItem != null) 'lastItem': lastItem,
      },
    );
  }

  @override
  void onInit() {
    super.onInit();
    onGetData();
  }
}

/// 一个 tab 的内容（列表）。
///
/// 直接把 [commonBody] 包一层 —— 分页、下拉刷新、黑名单过滤、双列瀑布流、
/// footer 状态全都是现成的，不需要为个人主页另写一套。
class UserTabContent extends StatefulWidget {
  const UserTabContent({super.key, required this.uid, required this.type});

  final String uid;
  final UserTabType type;

  @override
  State<UserTabContent> createState() => _UserTabContentState();
}

class _UserTabContentState extends State<UserTabContent> {
  late final _controller = Get.put(
    UserTabController(uid: widget.uid, type: widget.type),
    tag: 'userTab_${widget.type.type}_${widget.uid}',
  );

  @override
  void dispose() {
    Get.delete<UserTabController>(tag: 'userTab_${widget.type.type}_${widget.uid}');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return commonBody(
      _controller,
      // 「动态」「图文」整页统一走完整动态卡（见 UserTabType.feedCoverAsFeed）
      feedCoverAsFeed: widget.type.feedCoverAsFeed,
      uid: widget.uid,
    );
  }
}
