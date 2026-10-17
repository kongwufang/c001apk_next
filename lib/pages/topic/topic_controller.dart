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

  /// 产品页「参数」tab 的**版本配置列表**（`product/detail` 的顶层 `configRows`）。
  ///
  /// 对齐 Android `TopicContentViewModel.configRows`。真源注释：
  /// > 产品页「参数」tab：由 Fragment 从 product/detail 的 configRows 传入，
  /// > 合并进 dataList 下发的 productConfigList 卡片
  /// > （**服务端下发的这张卡片 entities 恒为空**）
  ///
  /// 也就是说：不注入这份数据，参数 tab 就是一张**空白卡片**。
  List<Map<String, dynamic>>? configRows;
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
