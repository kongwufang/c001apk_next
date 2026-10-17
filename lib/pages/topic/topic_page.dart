import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get/get_navigation/src/dialog/dialog_route.dart';

import '../../logic/state/loading_state.dart';
import '../../components/h5_tab.dart';
import '../../components/topic_header_card.dart';
import '../../pages/home/return_top_controller.dart';
import '../../pages/feed/reply/reply_dialog.dart' show ReplyType;
import '../../pages/feed/reply/reply_page.dart';
import '../../pages/topic/topic_content.dart';
import '../../pages/topic/topic_controller.dart';
import '../../pages/topic/topic_order_controller.dart';
import '../../utils/device_util.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';
import '../../components/m3e_progress.dart';

// ignore: constant_identifier_names
enum TopicMenuItem {
  Copy('复制'),       // copyText
  Share('分享'),      // share
  // ★ 没有 `Sort` 了 —— 产品/话题「讨论」tab 的排序改成**内联三段式控件**
  //   （列表顶部，见 `components/sort_header.dart`），对齐 Android
  //   `ProductSortHeaderAdapter`；那边菜单里也没有排序入口。
  Follow('关注'),     // follow
  Block('加入黑名单'); // block

  const TopicMenuItem(this.label);
  final String label;
}

// ignore: constant_identifier_names
/// 话题内排序。
///
/// label 会显示在排序菜单里（原来用 `.name`，界面上是英文）。
/// 与 `feed_page.dart` 的 `ReplySortType` 标签保持一致：默认 / 最新 / 热门。
// ignore: constant_identifier_names
enum TopicSortType {
  DEFAULT('默认'),
  DATELINE('最新'),
  HOT('热门');

  const TopicSortType(this.label);
  final String label;
}

class TopicPage extends StatefulWidget {
  const TopicPage({super.key});

  @override
  State<TopicPage> createState() => _TopicPageState();
}

class _TopicPageState extends State<TopicPage> with TickerProviderStateMixin {
  String? _tag = Get.parameters['tag'];
  final String? _id = Get.parameters['id'];

  TabController? _tabController;
  late final ReturnTopController _pageScrollController;
  late final TopicOrderController _topicOrderController;
  late final TopicController _topicController;

  late final String _random = DeviceUtil.randHexString(8);

  @override
  void initState() {
    super.initState();

    if (!_tag.isNullOrEmpty) {
      try {
        _tag = Uri.decodeComponent(_tag!);
      } catch (e) {
        debugPrint('topic: failed to decode tag: $_tag');
      }
    }

    _pageScrollController =
        Get.put(ReturnTopController(), tag: (_tag ?? _id!) + _random);
    _topicOrderController =
        Get.put(TopicOrderController(), tag: (_tag ?? _id!) + _random);
    _topicController =
        Get.put(TopicController(tag: _tag, id: _id), tag: '$_tag$_id$_random');
    _topicController.initialIndex.listen((initialIndex) {
      _tabController = TabController(
        vsync: this,
        initialIndex: initialIndex < 0 ? 0 : initialIndex,
        length: _topicController.tabList!.length,
      );
    });
  }

  @override
  void dispose() {
    _tabController?.removeListener(() {});
    _tabController?.dispose();
    Get.delete<ReturnTopController>(tag: (_tag ?? _id!) + _random);
    Get.delete<TopicOrderController>(tag: (_tag ?? _id!) + _random);
    Get.delete<TopicController>(tag: '$_tag$_id$_random');
    super.dispose();
  }

  /// 发布入口。
  ///
  /// 对齐 Android `TopicFragment.startReply`：
  /// ```kotlin
  /// MaterialAlertDialogBuilder(...)
  ///   .setItems(arrayOf(publishFeed, publishRating))
  ///   { _, which -> startReply(if (which == 0) "createFeed" else "rating") }
  /// ```
  /// —— 产品页是**二选一**（发动态 / 发点评），话题页只有一个（发动态）。
  ///
  /// 「发点评」只在**产品页**、且服务端 `allow_rating == 1`、
  /// 且下发了 `rating_item_info` 时才给。
  /// 头部卡片（logo / 标题 / 统计 / 简介 / 关注行）+ 分隔线。
  ///
  /// ★ 它**不再固定在页面上**，而是交给每个 tab 的 TopicContent.headerTop，
  ///   由 commonBody(header:) 作为列表的**第一个 sliver** 渲染 ⇒
  ///   随列表滚动自然移出屏幕，TabBar 仍钉在 AppBar.bottom 上。
  ///
  /// 对齐 release-notes 的「头部卡片跟随标题栏折叠」。
  /// 与 Android CollapsingToolbarLayout + COLLAPSE_MODE_PARALLAX 的
  /// **淡出 + 视差**不同，这里是**平移移出** —— 效果等价（头部让位、TabBar 吸顶），
  /// 但不需要动 commonBody 的 Sliver / 滚动控制器结构。
  Widget _headerTop() => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TopicHeaderCard(
            logo: _topicController.headerLogo,
            title: _topicController.title ?? '',
            hotNum: _topicController.headerHotNum,
            commentNum: _topicController.headerCommentNum,
            intro: _topicController.headerIntro,
            followNum: _topicController.headerFollowNum,
            avatars: _topicController.headerAvatars,
            isFollow: _topicController.isFollow,
            isLogin: GlobalData().isLogin,
            onFollow: _onFollow,
          ),
          const Divider(height: 1),
        ],
      );

  /// 关注 / 取消关注（话题走 tag 接口、产品走 follow 接口）。
  ///
  /// 抽成方法是为了让**菜单项**和**头部卡片上的关注按钮**共用一套逻辑
  /// （Android 那边也是两处都调 `onSubscribeClick()`）。
  void _onFollow() {
    if (!GlobalData().isLogin) return;
    if (_topicController.entityType == 'topic') {
      _topicController.onGetFollow(
        _topicController.isFollow,
        _topicController.isFollow
            ? '/v6/feed/unFollowTag'
            : '/v6/feed/followTag',
        tag: _topicController.title,
      );
    } else {
      _topicController.postLikeDeleteFollow(
        _topicController.id,
        null,
        isProduct: true,
        isFollow: _topicController.isFollow,
      );
    }
  }

  Future<void> _onPublish() async {
    final isProduct = _topicController.entityType == 'product';
    final canRating = isProduct &&
        _topicController.allowRating &&
        (_topicController.ratingItems?.isNotEmpty ?? false);

    var rating = false;
    if (canRating) {
      final which = await showDialog<int>(
        context: context,
        builder: (ctx) => SimpleDialog(
          title: const Text('发布'),
          children: [
            SimpleDialogOption(
              onPressed: () => Get.back(result: 0),
              child: const Text('发动态'),
            ),
            SimpleDialogOption(
              onPressed: () => Get.back(result: 1),
              child: const Text('发点评'),
            ),
          ],
        ),
      );
      if (which == null || !mounted) return;
      rating = which == 1;
    }

    Navigator.of(context).push(
      GetDialogRoute(
        pageBuilder: (buildContext, animation, secondaryAnimation) {
          return ReplyPage(
            type: rating ? ReplyType.rating : null,
            ratingItems: rating ? _topicController.ratingItems : null,
            targetType:
                _topicController.entityType == 'topic' ? 'tag' : 'product_phone',
            targetId: _topicController.id,
            title: _topicController.title,
          );
        },
        transitionDuration: const Duration(milliseconds: 500),
        transitionBuilder: (context, animation, secondaryAnimation, child) {
          const begin = Offset(0.0, 1.0);
          const end = Offset.zero;
          const curve = Curves.linear;

          var tween =
              Tween(begin: begin, end: end).chain(CurveTween(curve: curve));

          return SlideTransition(
            position: animation.drive(tween),
            child: child,
          );
        },
      ),
    );
  }

  Widget _buildBody(LoadingState topicState) {
    switch (topicState) {
      case Empty():
        return GestureDetector(
          onTap: _topicController.onReGetTopicData,
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: const Text('暂无内容'),
          ),
        );
      case Error():
        return GestureDetector(
          onTap: _topicController.onReGetTopicData,
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: Text(topicState.errMsg),
          ),
        );
    }
    return const M3EWavyProgressIndicator();
  }

  @override
  Widget build(BuildContext context) {
    return Obx(
      () => _topicController.topicState.value is Success
          ? Scaffold(
              floatingActionButton: GlobalData().isLogin &&
                      !_topicController.isBlocked
                  ? FloatingActionButton(
                      heroTag: null,
                      onPressed: _onPublish,
                      tooltip: '发布',
                      child: const Icon(Icons.add),
                    )
                  : null,
              appBar: AppBar(
                title: Text(
                  _topicController.title!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                actions: [
                  if (!_topicController.isBlocked)
                    IconButton(
                      onPressed: () => Get.toNamed('/search', parameters: {
                        'title': _topicController.title!,
                        'pageType': _topicController.entityType == 'topic'
                            ? 'tag'
                            : 'product_phone',
                        'pageParam': _topicController.entityType == 'topic'
                            ? _topicController.title!
                            : _topicController.id!,
                      }),
                      icon: const Icon(Icons.search),
                      tooltip: '搜索',
                    ),
                  PopupMenuButton(
                    onSelected: (TopicMenuItem item) {
                      switch (item) {
                        case TopicMenuItem.Copy:
                          Utils.copyText(Utils.getShareUri(
                            _topicController.entityType == 'topic'
                                ? _topicController.title!
                                : _topicController.id!,
                            _topicController.entityType == 'topic'
                                ? ShareType.t
                                : ShareType.product,
                          ).toString());
                          break;
                        case TopicMenuItem.Share:
                          // ★ 分享一律**直接复制链接**
                          Utils.copyText(Utils.getShareUri(
                            _topicController.entityType == 'topic'
                                ? _topicController.title!
                                : _topicController.id!,
                            _topicController.entityType == 'topic'
                                ? ShareType.t
                                : ShareType.product,
                          ).toString());
                          break;
                        case TopicMenuItem.Follow:
                          _onFollow();
                          break;
                        case TopicMenuItem.Block:
                          GStorage.onBlock(
                            _topicController.title!,
                            isUser: false,
                            isDelete: _topicController.isBlocked,
                          );
                          _topicController.isBlocked =
                              !_topicController.isBlocked;
                          break;
                      }
                    },
                    itemBuilder: (context) => [
                      PopupMenuItem(
                        value: TopicMenuItem.Copy,
                        child: Text(TopicMenuItem.Copy.label),
                      ),
                      PopupMenuItem(
                        value: TopicMenuItem.Share,
                        child: Text(TopicMenuItem.Share.label),
                      ),
                      // ★ 菜单里**不再**放「排序」项。
                      //
                      // 产品 / 话题的「讨论」tab 现在用**内联三段式控件**
                      // （列表顶部的 默认/最新/热度，见 `components/sort_header.dart`），
                      // 对齐 Android `ProductSortHeaderAdapter` ——
                      // 那边**只有**内联控件，菜单里没有排序入口。
                      PopupMenuItem(
                        value: TopicMenuItem.Follow,
                        // 文案本地化：与 Android `strings.xml` 对齐
                        // （关注 / 取消关注）。原来这里是英文 'Follow' / 'UnFollow'。
                        child: Text(
                            _topicController.isFollow ? '取消关注' : '关注'),
                      ),
                      PopupMenuItem(
                        value: TopicMenuItem.Block,
                        // 屏蔽 / 取消屏蔽（原来也是英文）
                        child: Text(
                            _topicController.isBlocked ? '取消屏蔽' : '屏蔽'),
                      ),
                    ],
                  ),
                ],
              ),
              body: _topicController.isBlocked
                  ? Center(
                      child: Text('${_topicController.title} is Blocked'),
                    )
                  : NestedScrollView(
                            // ★ 布局顺序对齐 Android `base_tablayout_viewpager.xml`：
                            //   AppBarLayout = [CollapsingToolbarLayout(头部) → TabLayout(横条)]
                            //   ⇒ **头部在 TabBar 上面**，TabBar 钉住，头部随滚动移出。
                            //   之前把头部塞进每个 tab 的列表 ⇒ 它跑到 TabBar **下面** ✗
                            headerSliverBuilder: (_, __) => [
                              // 1) 头部卡片（随滚动自然移出 = Android 的
                              //    `layout_scrollFlags="scroll|exitUntilCollapsed"`）
                              // ★ 头部卡改成**带视差**的 sliver。
                              //
                              //   对齐 Android `CollapsingToolbarLayout` 的
                              //   `CollapseMode.PARALLAX`（内容按一半速度上移）。
                              //
                              //   为什么不能直接 `Transform.translate` 包
                              //   `SliverToBoxAdapter`：那样只改绘制、不改布局，
                              //   下移的部分会**半透明压在下面的卡片上** ✗（踩过两次）。
                              //   用 `SliverPersistentHeader` + `ClipRect` 才是布局层解决的。
                              SliverPersistentHeader(
                                pinned: false,
                                delegate: _TopicHeaderParallax(
                                  child: _headerTop(),
                                ),
                              ),
                              // 2) TabBar 钉住（= TabLayout 不参与 collapse）
                              SliverPersistentHeader(
                                pinned: true,
                                delegate: _TopicTabBarHeader(
                                  TabBar(
                                    controller: _tabController,
                                    isScrollable: true,
                                    tabs: _topicController.tabList!
                                        .map((item) => Tab(text: item.title.toString()))
                                        .toList(),
                                    onTap: (value) {
                                      if (!_tabController!.indexIsChanging) {
                                        _pageScrollController.setIndex(value);
                                      }
                                    },
                                  ),
                                  Theme.of(context).colorScheme.surface,
                                ),
                              ),
                            ],
                            body: TabBarView(
                            controller: _tabController,
                            children: _topicController.tabList!
                                .map((item) {
                              final tabUrl = item.url.toString();
                              // ★ H5 类型的 tab（活动详情 / 专题页「说明」）必须用 WebView 渲染。
                              //
                              // 对齐 Android `TopicFragment.getFragment`：
                              // > H5 类型的 tab（如「活动详情」tab 的 url =
                              // > https://m.coolapk.com/activity/<name>）
                              // > 用 WebView 渲染，否则走 dataList 接口会返回空导致页面空白
                              //
                              // 这些 tab 的 `url` 是**完整 H5 地址**，不是 `/page?url=…`
                              // 那种数据页；拿去调 `/v6/page/dataList` 只会得到空列表。
                              // release-notes 那条「专题页「说明」与 H5 活动页补全渲染，
                              // 修复空白」说的就是它。
                              if (tabUrl.startsWith('http://') ||
                                  tabUrl.startsWith('https://')) {
                                return H5Tab(url: tabUrl);
                              }
                              return TopicContent(
                                // ★ 不再传 `headerTop` —— 头部卡已经移到 `NestedScrollView` 的
                                //   sliver 里、位于 TabBar **上面**（对齐 Android
                                //   `base_tablayout_viewpager.xml`）。这里只留「讨论」tab 自己的三段式排序。
                                random: _random,
                                tag: _topicController.tag,
                                id: _topicController.id,
                                index: _topicController.tabList!.indexOf(item),
                                entityType: _topicController.entityType!,
                                url: tabUrl,
                                title: item.title.toString(),
                                // 产品页「参数」tab 的版本配置列表 ——
                                // 服务端下发的 productConfigList 卡片 entities 恒为空，
                                // 要由这里注入（见 TopicContentController 的注释）
                                configRows: _topicController.configRows,
                              );
                            }).toList(),
                  ),
            )
            )
          : Scaffold(
              appBar: AppBar(),
              body:
                  Center(child: _buildBody(_topicController.topicState.value)),
            ),
    );
  }
}

/// 把 TabBar 钉在头部卡片下面（= Android `TabLayout` 不参与 collapse）。
///
/// 照搬 `user_page.dart` 的 `_UserTabBarHeader` —— 用户主页那个布局已验收通过。
class _TopicTabBarHeader extends SliverPersistentHeaderDelegate {
  _TopicTabBarHeader(this.tabBar, this.background);

  final TabBar tabBar;
  final Color background;

  @override
  double get minExtent => tabBar.preferredSize.height;

  @override
  double get maxExtent => tabBar.preferredSize.height;

  @override
  Widget build(
          BuildContext context, double shrinkOffset, bool overlapsContent) =>
      ColoredBox(color: background, child: tabBar);

  @override
  bool shouldRebuild(_TopicTabBarHeader oldDelegate) =>
      oldDelegate.tabBar != tabBar || oldDelegate.background != background;
}

/// 带**视差**的头部卡（对齐 Android `CollapseMode.PARALLAX`）。
///
/// ## 为什么需要它
/// `Transform.translate` 只改绘制、不改布局 ⇒ 平移出去的部分会**压在下面的卡片上** ✗
/// `SliverToBoxAdapter` 又完全不裁剪。
///
/// 这个 delegate 用「固定高度 + `ClipRect` + 按 `shrinkOffset` 平移」解决：
/// - 布局高度固定 [_height]，滚过去就真的滚过去（不会把下面的内容顶掉）
/// - 内容按 [_factor] 的**一半速度**上移 = 视差
/// - `ClipRect` 保证平移出界的那部分**不画** ⇒ 不会压到卡片上 ✓
class _TopicHeaderParallax extends SliverPersistentHeaderDelegate {
  _TopicHeaderParallax({required this.child});

  final Widget child;

  /// 固定高度。`TopicHeaderCard` 自然高度约 110–130，
  /// 这里留 160 的余量，避免内容稍多就被裁。
  static const double _height = 160;

  /// 视差系数：0.5 = 内容以一半速度上移（同 Android PARALLAX）
  static const double _factor = 0.5;

  @override
  double get minExtent => _height;

  @override
  double get maxExtent => _height;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    return SizedBox(
      height: _height,
      child: ClipRect(
        child: OverflowBox(
          alignment: Alignment.topCenter,
          maxHeight: double.infinity,
          child: Transform.translate(
            offset: Offset(0, -shrinkOffset * _factor),
            child: child,
          ),
        ),
      ),
    );
  }

  @override
  bool shouldRebuild(_TopicHeaderParallax old) => old.child != child;
}
