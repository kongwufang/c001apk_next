import 'package:flutter/foundation.dart' show debugPrint;
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../logic/model/feed/tab_list.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';
import '../../utils/extensions.dart';
import '../../utils/storage_util.dart';

class TopicController extends CommonController {
  TopicController({required this.tag, required this.id});

  final String? tag;
  String? id;

  String? title;
  String? entityType;
  List<TabList>? tabList;

  /// 产品页「参数」tab 的**版本配置列表**（`product/detail` 的顶层 `configRows`）。  ///
  /// 对齐 Android `TopicContentViewModel.configRows`。真源注释：
  /// > 产品页「参数」tab：由 Fragment 从 product/detail 的 configRows 传入，
  /// > 合并进 dataList 下发的 productConfigList 卡片
  /// > （**服务端下发的这张卡片 entities 恒为空**）
  ///
  /// 也就是说：不注入这份数据，参数 tab 就是一张**空白卡片**。
  List<Map<String, dynamic>>? configRows;

  /// 点评的**评分子项**（`product/detail` 的 `rating_item_info`）。
  ///
  /// 实测形态：
  /// ```
  /// [{id: 'v4_score_item_1', name: '续航',
  ///   star_desc: ['很差','较差','一般','较好','非常好'], v4_score: 0}, …]
  /// ```
  /// 提交时子项分数字段名就是 `v4_score_item_1..n`（**直接取 `id`** 即可）。
  List<Map<String, dynamic>>? ratingItems;

  /// 这个产品**允许点评**（`allow_rating == 1`）
  bool allowRating = false;

  // ── 头部卡片（`item_topic_header.xml`）──
  //
  // 字段口径照抄 Android `TopicViewModel.publishHeader`：
  // ```
  // logo       ← data.logo
  // title      ← data.title
  // hotNum     ← hot_num_txt
  // commentNum ← commentnum_txt
  // intro      ← intro
  // followNum  ← follownum_txt
  // avatars    ← recent_follow_list[].userAvatar
  // ```
  String headerLogo = '';
  String headerHotNum = '';
  String headerCommentNum = '';
  String headerIntro = '';
  String headerFollowNum = '';
  List<String> headerAvatars = const [];
  RxInt initialIndex = 0.obs;
  Rx<LoadingState> topicState = LoadingState.loading().obs;

  bool isBlocked = false;
  bool isFollow = false;

  Future<void> _getTopicData() async {
    LoadingState<dynamic> response = await NetworkRepo.getDataFromUrl(
      url: !tag.isNullOrEmpty
          ? '/v6/topic/newTagDetail'
          : !id.isNullOrEmpty
              ? '/v6/product/detail'
              : '',
      data: {
        if (!tag.isNullOrEmpty) 'tag': tag,
        if (!id.isNullOrEmpty) 'id': id,
      },
    );
    if (response is Success) {
      Datum data = response.response;
      id = data.id.toString();
      title = data.title;
      entityType = data.entityType;
      tabList = data.tabList;
      String selectedTab = data.selectedTab!;
      initialIndex.value =
          tabList!.map((item) => item.pageName).toList().indexOf(selectedTab);

      isBlocked = GStorage.checkTopic(title!);
      isFollow = data.userAction?.follow == 1;

      // 产品页「参数」tab 的版本配置列表。
      //
      // 键名是 **`configRows`（驼峰）** —— 实测 `/v6/product/detail` 的返回：
      // ```
      // configRows: [{id, product_id, title, price, url, is_add_compare, ...}, ...]
      // ```
      // （`Datum` 没声明这个字段，所以只能从 `raw` 取。）
      final rawConfig = data.raw?['configRows'];
      if (rawConfig is List) {
        configRows = rawConfig
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
      // 点评：子项定义 + 是否允许点评
      //
      // `product/detail` 实测字段：
      //   rating_item_info: [{id: 'v4_score_item_1', name: '续航',
      //                       star_desc: [...], v4_score: 0}, …]   ← 6 个子项
      //   allow_rating: '1'
      final rawRatingItems = data.raw?['rating_item_info'];
      if (rawRatingItems is List) {
        ratingItems = rawRatingItems
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
      allowRating = data.raw?['allow_rating']?.toString() == '1';

      // 头部卡片（字段口径见字段声明的注释）
      String s(String key) => data.raw?[key]?.toString() ?? '';
      headerLogo = s('logo');
      headerHotNum = s('hot_num_txt');
      headerCommentNum = s('commentnum_txt');
      headerIntro = s('intro');
      headerFollowNum = s('follownum_txt');
      final recentFollow = data.raw?['recent_follow_list'];
      if (recentFollow is List) {
        headerAvatars = recentFollow
            .whereType<Map>()
            .map((e) => e['userAvatar']?.toString() ?? '')
            .where((e) => e.isNotEmpty)
            .toList();
      }

      // 诊断：参数 tab 的版本配置到底有没有取到
      debugPrint('TopicController: id=$id entityType=$entityType '
          'raw有configRows=${data.raw?.containsKey('configRows')} '
          'configRows=${configRows?.length} '
          'ratingItems=${ratingItems?.length} allowRating=$allowRating '
          'tabList=${tabList?.map((e) => e.title).toList()}');

      topicState.value = LoadingState.success(response.response);
    } else {
      topicState.value = response;
    }
  }

  void onReGetTopicData() {
    topicState.value = LoadingState.loading();
    _getTopicData();
  }

  @override
  void onInit() {
    super.onInit();
    _getTopicData();
  }

  @override
  void handleGetFollow() {
    isFollow = !isFollow;
  }

  @override
  Future<LoadingState> customGetData() {
    // TODO: implement customGetData
    throw UnimplementedError();
  }
}
