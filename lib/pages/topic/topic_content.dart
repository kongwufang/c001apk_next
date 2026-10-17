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
    this.headerTop,
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

  /// 列表**最上面**那块（话题 / 产品页的头部卡片）。
  ///
  /// 放进 `commonBody(header:)` 而不是固定在页面上 —— 这样它会**随列表滚动移出**，
  /// TabBar 仍钉在 `AppBar.bottom` 上，对齐 release-notes 的
  /// 「头部卡片跟随标题栏折叠」（与 Android `CollapsingToolbarLayout` 的
  /// **淡出 + 视差**也都补上了（见 `_headerFade` 与 `Transform.translate` 两处）。
  /// 做法不同：这里是「sliver 内平移 + 透明度 + 半速视差」，
  /// 那边是 `CollapsingToolbarLayout` 内部自己算的。
  final Widget? headerTop;

  @override
  State<TopicContent> createState() => _TopicContentState();
}

class _TopicContentState extends State<TopicContent>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  /// 头部折叠进度：0 = 完全展开（全亮），1 = 完全收起（全透明）。
  ///
  /// 由 `commonBody` 内部列表冒上来的 `ScrollNotification` 驱动，
  /// 只重建头部那一个 `Opacity` —— 不触发整页重建。
  ///
  /// 对齐 Android `BasePagerFragment.applyHeaderFade`
  /// （那边是折叠进度越高头部越透明，最后只剩折叠标题）。
  final ValueNotifier<double> _headerFade = ValueNotifier<double>(0);

  /// 头部大约这么高；滚过它就完全淡出（含上下 padding 的粗估）
  static const double _headerFadeDistance = 120;

  @override
  void dispose() {
    _headerFade.dispose();
    super.dispose();
  }

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
    // 列表最上面那块，两种情况：
    // - 每个 tab 都有**头部卡片**（`headerTop`），它会随列表滚动移出；
    // - 「讨论」tab 还有**三段式排序**（默认 / 最新 / 热度），
    //   对齐 Android `ProductSortHeaderAdapter`。
    //
    // 两者都通过 `commonBody(header:)` 作为**第一个 sliver** 插进列表 ——
    // 于是头部卡自然随滚动走掉，TabBar 仍钉在 `AppBar.bottom` 上。
    //
    // Android 是 `CollapsingToolbarLayout` + `COLLAPSE_MODE_PARALLAX`
    //    （**淡出 + 视差**）。这里用 `Opacity` + `Transform.translate`
    //    手工做出同样的效果，好处是**不需要动 `commonBody` 的
    //    Sliver / 滚动控制器结构**。
    // ★ 用 `NotificationListener` 包住 `commonBody` 收滚动通知 ——
    //   列表在 `commonBody` 内部，通知会往上冒，所以监听器挂在外层能收到。
    //   这里只更新折叠进度，不 setState（避免每帧重建整页）。
    Widget withScroll(Widget child) => NotificationListener<ScrollNotification>(
          onNotification: (n) {
            final t =
                (n.metrics.pixels / _headerFadeDistance).clamp(0.0, 1.0);
            // 变化小于 2% 就不通知，省掉大量无意义重建
            if ((_headerFade.value - t).abs() > 0.02) _headerFade.value = t;
            return false;
          },
          child: child,
        );
    final orderController = _topicOrderController;
    if (orderController == null && widget.headerTop == null) {
      return withScroll(commonBody(_topicController));
    }
    Widget buildHeader() => Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (widget.headerTop != null)
              ValueListenableBuilder<double>(
                valueListenable: _headerFade,
                // 只重建这一个 Opacity，child 复用不重建
                builder: (_, fade, child) => Opacity(
                  opacity: 1 - fade,
                  // ★ 视差：把头部**往下**偏移「折叠进度 × 半程」，
                  //   于是它上移的速度只有列表的一半 —— 对齐 Android
                  //   CollapsingToolbarLayout 的 COLLAPSE_MODE_PARALLAX。
                  //   只偏移、不改 sliver 高度：列表照常收上去，头部像「拖在后面」。
                  child: Transform.translate(
                    offset: Offset(0, fade * _headerFadeDistance * 0.5),
                    child: child,
                  ),
                ),
                child: widget.headerTop,
              ),
            if (orderController != null)
              SortHeader(
                current: orderController.topicSortType.value,
                onChanged: orderController.setTopicSortType,
              ),
          ],
        );
    if (orderController == null) {
      return withScroll(commonBody(_topicController, header: buildHeader()));
    }
    return withScroll(
      Obx(() => commonBody(_topicController, header: buildHeader())),
    );
  }
}
