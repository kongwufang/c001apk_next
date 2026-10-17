import 'package:flutter/material.dart' hide SearchController;
import 'package:get/get.dart';

import '../../components/common_body.dart';
import '../../components/sort_header.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/home/return_top_controller.dart';
import '../../pages/topic/topic_content_controller.dart';
import '../../pages/topic/topic_order_controller.dart';
import '../../pages/topic/topic_page.dart' show TopicSortType;

class TopicContent extends StatefulWidget {
  const TopicContent({
    super.key,
    required this.random,
    required this.tag,
    required this.id,
    required this.index,
    required this.entityType,
    required this.url,
    required this.title,
    this.configRows,
  });

  final String random;
  final String? tag;
  final String? id;
  final int index;
  final String entityType;
  final String url;
  final String title;

  /// 产品页「参数」tab 的版本配置列表（其余 tab 为 null）。
  ///
  /// 见 `TopicContentController` 里那段「服务端下发的 productConfigList
  /// 卡片 entities 恒为空」的说明。
  final List<Map<String, dynamic>>? configRows;

  @override
  State<TopicContent> createState() => _TopicContentState();
}

class _TopicContentState extends State<TopicContent>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  late final _topicController = Get.put(
    TopicContentController(
      url: widget.url,
      title: widget.title,
      configRows: widget.configRows,
    ),
    tag: widget.url + widget.title + widget.random,
  );

  late final TopicOrderController? _topicOrderController;

  @override
  void dispose() {
    _topicController.scrollController?.dispose();
    Get.delete<TopicContentController>(
      tag: widget.url + widget.title + widget.random,
    );
    super.dispose();
  }

  @override
  void initState() {
    super.initState();

    _topicController.refreshKey = GlobalKey<RefreshIndicatorState>();
    _topicController.scrollController = ScrollController();
    _topicController.returnTopController = Get.find<ReturnTopController>(
        tag: (widget.tag ?? widget.id!) + widget.random);

    _topicController.returnTopController?.index.listen((index) {
      if (index == widget.index) {
        _topicController.animateToTop();
      }
    });

    // 三段式排序（默认 / 最新 / 热度）覆盖两类栏目 ——
    // 照抄 Android `TopicContentFragment.initAdapter()`：
    // > 1) 产品讨论 tab：`product/feedList` + `type=feed`（排序参数 **listType**）
    // > 2) 话题讨论 tab：`topic/tagFeedList` + `withSortCard=1`（排序参数 **sortField**）
    //
    // ★ 判据用 **URL 解码后 contains**，不是 `entityType` / `title`。
    //   Android 那边专门写了这条注释：
    //   > `product/detail` 下发的 tab url 是 **URL 编码的**（如 `%2Fproduct%2FfeedList`、
    //   > `type%3Dfeed`），必须先解码再判断，否则 contains 匹配不到，排序开关就不显示。
    //
    //   原来这里用 `entityType == 'product' && title == '讨论'` 只覆盖了产品；
    //   **话题的讨论 tab 点了排序没有任何反应** —— 这次一并修掉。
    final decodedUrl = Uri.decodeComponent(widget.url);
    final isProductFeed = decodedUrl.contains('product/feedList') &&
        decodedUrl.contains('type=feed');
    final isTopicFeed = decodedUrl.contains('topic/tagFeedList') &&
        decodedUrl.contains('withSortCard=1');

    if (isProductFeed || isTopicFeed) {
      _topicOrderController = Get.find<TopicOrderController>(
          tag: (widget.tag ?? widget.id!) + widget.random);
      _topicOrderController?.topicSortType.listen((type) {
        if (isTopicFeed) {
          // 话题讨论 tab：排序走 `sortField` 参数
          // （Android：最新 → dateline_desc / 热度 → rank_score / 默认 → lastupdate_desc）
          final sortValue = switch (type) {
            TopicSortType.DATELINE => 'dateline_desc',
            TopicSortType.HOT => 'rank_score',
            _ => 'lastupdate_desc',
          };
          _topicController.url = decodedUrl.contains('sortField=')
              ? decodedUrl.replaceAll(
                  RegExp('sortField=[^&]*'), 'sortField=$sortValue')
              : '$decodedUrl&sortField=$sortValue';
          _topicController.title = type.label;
        } else {
          // 产品讨论 tab：排序走 `listType` 参数
          _topicController.url =
              '/page?url=/product/feedList?cacheExpires=60&type=feed&id=${widget.id}&';
          switch (type) {
            case TopicSortType.DEFAULT:
              _topicController.url =
                  '${_topicController.url}ignoreEntityById=1';
              _topicController.title = '默认';
              break;
            case TopicSortType.DATELINE:
              _topicController.url =
                  '${_topicController.url}ignoreEntityById=1&listType=dateline_desc';
              _topicController.title = '最新';
              break;
            case TopicSortType.HOT:
              _topicController.url =
                  '${_topicController.url}listType=rank_score';
              _topicController.title = '热度';
              break;
          }
        }
        if (_topicController.loadingState.value is Success) {
          _topicController.animateToTop();
        } else {
          _topicController.setLoadingState(LoadingState.loading());
          _topicController.onGetData();
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    // 产品 / 话题的「讨论」tab：列表最上面插一段**三段式排序**
    // （默认 / 最新 / 热度）—— 对齐 Android `ProductSortHeaderAdapter`。
    //
    // 只有 `_topicOrderController != null` 时才有排序（即 discussion tab），
    // 其余 tab 不插入任何东西。
    final orderController = _topicOrderController;
    if (orderController == null) return commonBody(_topicController);
    return Obx(
      () => commonBody(
        _topicController,
        header: SortHeader(
          current: orderController.topicSortType.value,
          onChanged: orderController.setTopicSortType,
        ),
      ),
    );
  }
}
