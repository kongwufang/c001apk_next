import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';

class TopicContentController extends CommonController {
  TopicContentController({
    required this.url,
    required this.title,
    this.configRows,
  });

  late String url;
  late String title;

  /// 产品页「参数」tab 的版本配置列表（来自 `product/detail` 的顶层 `configRows`）。
  ///
  /// 只有产品页的「参数」tab 会传；话题页恒为 null。
  final List<Map<String, dynamic>>? configRows;

  @override
  Future<LoadingState> customGetData() async {
    final result = await NetworkRepo.getDataList(
      url: url,
      title: title,
      subTitle: '',
      firstItem: firstItem,
      lastItem: lastItem,
      page: page,
      // ★ 话题 / 产品内容列表要放行 `entityType == 'card'`。
      //
      //   对齐 Android `TopicContentViewModel`：
      //   > 「card」类型也要放行：产品页「参数」tab 的
      //   > productConfigList/listCard 都是 card，过滤掉会整页空白
      //
      //   不放行的话，参数 tab 的 4 条会被全局白名单全过滤掉，
      //   界面显示成「没有更多了」。
      includeCardEntities: true,
    );

    // ★ 产品页「参数」tab：把本地 `configRows` **注入**到服务端下发的
    //   `productConfigList` 卡片里。
    //
    //   Android `TopicContentViewModel` 的注释：
    //   > 参数 tab：把本地 configRows 合并进版本配置卡片
    //   > （**服务端下发的这张卡片 entities 恒为空**）
    //
    //   不注入的话这张卡就是**一片空白** —— 参数 tab 会显示成一张空卡片。
    //
    //   注入方式：`Datum` 没有 `configRows` 字段（加字段要重跑 build_runner
    //   生成 `.g.dart`），所以走 `raw` —— 与视频槽 / 附加信息槽同一套做法。
    if (result is Success && configRows != null && configRows!.isNotEmpty) {
      final list = result.response;
      if (list is List) {
        for (final item in list) {
          if (item is Datum &&
              item.entityTemplate == 'productConfigList' &&
              item.raw != null) {
            // 服务端真给了就不覆盖
            item.raw!.putIfAbsent('configRows', () => configRows);
          }
        }
      }
    }
    return result;
  }

  @override
  void onInit() {
    super.onInit();
    onGetData();
  }
}
