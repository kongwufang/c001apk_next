import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get/get_navigation/src/dialog/dialog_route.dart';
import 'package:share_plus/share_plus.dart';

import '../../logic/state/loading_state.dart';
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
  Sort('排序'),
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

  bool _shouldShowActions = false;

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
      setShouldShowActions(initialIndex);
      _tabController?.addListener(() {
        setShouldShowActions(_tabController!.index);
      });
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

  void setShouldShowActions(int index) {
    _shouldShowActions = _topicController.entityType == 'product' &&
        _topicController.tabList![index].title == '讨论';
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
                bottom: _topicController.isBlocked
                    ? const PreferredSize(
                        preferredSize: Size.zero, child: Divider(height: 1))
                    : TabBar(
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
                      tooltip: 'Search',
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
                          SharePlus.instance.share(ShareParams(
                              uri: Utils.getShareUri(
                            _topicController.entityType == 'topic'
                                ? _topicController.title!
                                : _topicController.id!,
                            _topicController.entityType == 'topic'
                                ? ShareType.t
                                : ShareType.product,
                          )));
                          break;
                        case TopicMenuItem.Sort:
                          _showPopupMenu();
                          break;
                        case TopicMenuItem.Follow:
                          if (GlobalData().isLogin) {
                            if (_topicController.entityType == 'topic') {
                              _topicController.onGetFollow(
                                _topicController.isFollow,
                                _topicController.isFollow
                                    ? "/v6/feed/unFollowTag"
                                    : "/v6/feed/followTag",
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
                      if (_shouldShowActions)
                        PopupMenuItem(
                          value: TopicMenuItem.Sort,
                          child: Row(
                            children: [
                              Expanded(
                                flex: 1,
                                child: Text(TopicMenuItem.Sort.label),
                              ),
                              const Icon(Icons.arrow_right)
                            ],
                          ),
                        ),
                      PopupMenuItem(
                        value: TopicMenuItem.Follow,
                        child: Text(
                            _topicController.isFollow ? 'UnFollow' : 'Follow'),
                      ),
                      PopupMenuItem(
                        value: TopicMenuItem.Block,
                        child: Text(
                            _topicController.isBlocked ? 'UnBlock' : 'Block'),
                      ),
                    ],
                  ),
                ],
              ),
              body: _topicController.isBlocked
                  ? Center(
                      child: Text('${_topicController.title} is Blocked'),
                    )
                  : TabBarView(
                      controller: _tabController,
                      children: _topicController.tabList!
                          .map((item) => TopicContent(
                                random: _random,
                                tag: _topicController.tag,
                                id: _topicController.id,
                                index: _topicController.tabList!.indexOf(item),
                                entityType: _topicController.entityType!,
                                url: item.url.toString(),
                                title: item.title.toString(),
                                // 产品页「参数」tab 的版本配置列表 ——
                                // 服务端下发的 productConfigList 卡片 entities 恒为空，
                                // 要由这里注入（见 TopicContentController 的注释）
                                configRows: _topicController.configRows,
                              ))
                          .toList(),
                    ),
            )
          : Scaffold(
              appBar: AppBar(),
              body:
                  Center(child: _buildBody(_topicController.topicState.value)),
            ),
    );
  }

  void _showPopupMenu() async {
    final screenSize = MediaQuery.of(context).size;
    await showMenu(
      initialValue: _topicOrderController.topicSortType.value,
      context: context,
      position:
          RelativeRect.fromLTRB(screenSize.width, 0, 0, screenSize.height),
      items: TopicSortType.values
          .map((type) => PopupMenuItem(value: type, child: Text(type.label)))
          .toList(),
      elevation: 8.0,
    ).then((value) {
      if (value != null) {
        _topicOrderController.setTopicSortType(value);
      }
    });
  }
}
