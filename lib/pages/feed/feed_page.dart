import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:get/get_navigation/src/dialog/dialog_route.dart';
import 'package:share_plus/share_plus.dart';

import '../../components/cards/feed_reply_card.dart';
import '../../components/feed_article_body.dart';
import '../../components/footer.dart';
import '../../components/sliver_pinned_box_adapter.dart';
import '../../components/cards/feed_card.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/feed/feed_controller.dart';
import '../../pages/feed/reply/reply_dialog.dart' show ReplyType;
import '../../pages/feed/reply/reply_page.dart';
import '../../utils/device_util.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';
import '../../components/m3e_progress.dart';
import '../../pages/collection/collection_pick_sheet.dart';

// ignore: constant_identifier_names
enum FeedMenuItem {
  Copy('复制'),       // copyText
  Share('分享'),      // share
  Fav('收藏'),        // favorite
  Block('加入黑名单'), // block
  Report('举报');     // report

  const FeedMenuItem(this.label);
  final String label;
}

enum ReplySortType { def, dateline, hot, author }

class FeedPage extends StatefulWidget {
  const FeedPage({super.key});

  @override
  State<FeedPage> createState() => _FeedPageState();
}

class _FeedPageState extends State<FeedPage> with TickerProviderStateMixin {
  final String _id = Get.parameters['id'].orEmpty;
  final _refreshKey = GlobalKey<RefreshIndicatorState>();
  AnimationController? _fabAnimationCtr;
  late bool _isFabVisible = true;
  late final _scrollController = ScrollController();
  late final StreamController<bool> _titleStreamC = StreamController<bool>();
  late bool _visibleTitle = false;

  @override
  void initState() {
    super.initState();
    if (GlobalData().isLogin) {
      _fabAnimationCtr = AnimationController(
          vsync: this, duration: const Duration(milliseconds: 300));
      _fabAnimationCtr?.forward();
      _scrollController.addListener(() {
        final ScrollDirection direction =
            _scrollController.position.userScrollDirection;
        if (direction == ScrollDirection.forward) {
          _showFab();
        } else if (direction == ScrollDirection.reverse) {
          _hideFab();
        }
      });
    }
  }

  void _showFab() {
    if (!_isFabVisible) {
      _isFabVisible = true;
      _fabAnimationCtr?.forward();
    }
  }

  void _hideFab() {
    if (_isFabVisible) {
      _isFabVisible = false;
      _fabAnimationCtr?.reverse();
    }
  }

  @override
  void dispose() {
    _titleStreamC.close();
    _scrollController.removeListener(() {});
    _scrollController.dispose();
    _fabAnimationCtr?.dispose();
    Get.delete<FeedController>(tag: _id + _random);
    super.dispose();
  }

  late final String _random = DeviceUtil.randHexString(8);
  late final _feedController = Get.put(
    FeedController(
      id: _id,
      recordHistory: GStorage.recordHistory,
    ),
    tag: _id + _random,
  );

  static const List<(ReplySortType, String)> _shirtSizeOptions =
      <(ReplySortType, String)>[
    (ReplySortType.def, '默认'),
    (ReplySortType.dateline, '最新'),
    (ReplySortType.hot, '热门'),
    (ReplySortType.author, '楼主'),
  ];

  Widget _buildFeedContent(LoadingState feedState) {
    switch (feedState) {
      case Empty():
        return GestureDetector(
          onTap: () {
            _feedController.setFeedState(LoadingState.loading());
            _feedController.getFeedData();
          },
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: const Text('EMPTY'),
          ),
        );
      case Error():
        return GestureDetector(
          onTap: () {
            _feedController.setFeedState(LoadingState.loading());
            _feedController.getFeedData();
          },
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: Text(feedState.errMsg),
          ),
        );
      case Success():
        return _feedController.articleList.isNullOrEmpty
            ? FeedCard(
                data: feedState.response!,
                isFeedContent: true,
                onLike: (id, like) {
                  if (GlobalData().isLogin) {
                    _feedController.onLike(
                      id,
                      like,
                      isFeed: true,
                    );
                  }
                },
              )
            : SliverList.separated(
                itemCount: _feedController.articleList!.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return header(
                      context,
                      feedState.response!,
                      true,
                    );
                  } else if (index == _feedController.articleList!.length + 1) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: bottomInfo(
                        context,
                        feedState.response!,
                        true,
                        null,
                        isFeedArticle: true,
                        () {
                          if (GlobalData().isLogin) {
                            Datum data = feedState.response;
                            _feedController.onLike(
                              data.id,
                              data.userAction?.like,
                              isFeed: true,
                            );
                          }
                        },
                      ),
                    );
                  } else {
                    return LayoutBuilder(builder: (_, constraints) {
                      return feedArticleBody(
                        constraints.maxWidth,
                        _feedController.articleList![index - 1],
                        _feedController.articleImgList!,
                      );
                    });
                  }
                },
                separatorBuilder: (_, index) => const SizedBox(height: 12),
              );
    }
    return const M3EWavyProgressIndicator();
  }

  Widget _buildPinWidget() {
    return SliverPinnedBoxAdapter(
      child: Container(
        color: Theme.of(context).colorScheme.surface,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!Utils.isWideLandscape(context)) const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(
                children: [
                  const SizedBox(width: 16),
                  Expanded(
                    flex: 1,
                    child: Text('共 ${_feedController.replyNum} 回复'),
                  ),
                  Obx(
                    () => SegmentedButton<ReplySortType>(
                      style: TextButton.styleFrom(
                        padding: EdgeInsets.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        visualDensity: const VisualDensity(
                          horizontal: -4,
                          vertical: -4,
                        ),
                      ),
                      showSelectedIcon: false,
                      selected: <ReplySortType>{
                        _feedController.replySelection.value
                      },
                      segments: _shirtSizeOptions
                          .map<ButtonSegment<ReplySortType>>(
                              ((ReplySortType, String) shirt) {
                        return ButtonSegment<ReplySortType>(
                            value: shirt.$1, label: Text(shirt.$2));
                      }).toList(),
                      onSelectionChanged: (Set<ReplySortType> newSelection) {
                        _feedController.replySelection.value =
                            newSelection.first;
                        switch (newSelection.first) {
                          case ReplySortType.def:
                            _feedController.listType = 'lastupdate_desc';
                            _feedController.fromFeedAuthor = 0;
                            break;
                          case ReplySortType.dateline:
                            _feedController.listType = 'dateline_desc';
                            _feedController.fromFeedAuthor = 0;
                            break;
                          case ReplySortType.hot:
                            _feedController.listType = 'popular';
                            _feedController.fromFeedAuthor = 0;
                            break;
                          case ReplySortType.author:
                            _feedController.listType = '';
                            _feedController.fromFeedAuthor = 1;
                            break;
                        }
                        _refreshKey.currentState?.show();
                      },
                    ),
                  ),
                  const SizedBox(width: 16),
                ],
              ),
            ),
            const Divider(height: 1),
          ],
        ),
      ),
    );
  }

  Future<void> _onReply(
    ReplyType type,
    dynamic id,
    dynamic uname,
    dynamic fid,
  ) async {
    Navigator.of(context)
        .push(
      GetDialogRoute(
        pageBuilder: (buildContext, animation, secondaryAnimation) {
          return ReplyPage(
            type: type,
            username: uname,
            id: id,
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
    )
        .then(
      (result) {
        if (result != null && result['data'] != null) {
          _feedController.updateReply(
            type == ReplyType.reply,
            result['data'] as Datum,
            id,
            fid,
          );
        }
      },
    );

    // dynamic result = await showModalBottomSheet<dynamic>(
    //   context: context,
    //   isScrollControlled: true,
    //   builder: (context) => ReplyDialog(
    //     type: type,
    //     username: uname,
    //     id: id,
    //   ),
    // );
    // if (result != null && result['data'] != null) {
    //   _feedController.updateReply(
    //     type == ReplyType.reply,
    //     result['data'] as Datum,
    //     id,
    //     fid,
    //   );
    // }
  }

  Widget _buildFeedReply(LoadingState replyState) {
    switch (replyState) {
      case Empty():
        return SliverToBoxAdapter(
          child: Container(
            height: 80,
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: const Text('EMPTY'),
          ),
        );
      case Error():
        return SliverToBoxAdapter(
          child: GestureDetector(
            onTap: () {
              _feedController
                ..isEnd = false
                ..setLoadingState(LoadingState.loading())
                ..onGetData();
            },
            child: Container(
              height: 80,
              alignment: Alignment.center,
              padding: const EdgeInsets.all(10.0),
              child: Text(replyState.errMsg),
            ),
          ),
        );
      case Success():
        List<Datum> dataList = replyState.response;
        return SliverPadding(
          padding:
              EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
          sliver: SliverList.separated(
            itemCount: dataList.length + 1,
            itemBuilder: (_, index) {
              if (index == dataList.length) {
                if (!_feedController.isEnd && !_feedController.isLoading) {
                  _feedController.setFooterState(LoadingState.loading());
                  _feedController.onGetData(false);
                }
                return Obx(
                  () => footerWidget(
                    _feedController.footerState.value,
                    () {
                      _feedController
                        ..isEnd = false
                        ..setFooterState(LoadingState.loading())
                        ..onGetData(false);
                    },
                  ),
                );
              } else {
                return FeedReplyCard(
                  data: dataList[index],
                  isTopReply: _feedController.topReply != null &&
                      index == 0 &&
                      _feedController.listType == 'lastupdate_desc',
                  onBlock: _feedController.onBlockReply,
                  onReply: (id, uname, fid) {
                    if (GlobalData().isLogin) {
                      _onReply(ReplyType.reply, id, uname, fid);
                    }
                  },
                  onDelete: (id, fid) {
                    _feedController.postLikeDeleteFollow(id, fid,
                        isReply: true);
                  },
                  onLike: (id, like) {
                    _feedController.onLike(id, like, isReply: true);
                  },
                );
              }
            },
            separatorBuilder: (_, index) => const Divider(height: 1),
          ),
        );
    }
    return SliverToBoxAdapter(
      child: Container(
        height: 80,
        alignment: Alignment.center,
        padding: const EdgeInsets.all(10.0),
        child: const M3EWavyProgressIndicator(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: Obx(
        () => _feedController.feedState.value is Success && GlobalData().isLogin
            ? SlideTransition(
                position: Tween<Offset>(
                  begin: const Offset(0, 2),
                  end: const Offset(0, 0),
                ).animate(CurvedAnimation(
                  parent: _fabAnimationCtr!,
                  curve: Curves.easeInOut,
                )),
                child: FloatingActionButton(
                  heroTag: null,
                  tooltip: 'Reply',
                  onPressed: () {
                    _onReply(
                      ReplyType.feed,
                      _feedController.id,
                      _feedController.feedUsername,
                      null,
                    );
                  },
                  child: const Icon(Icons.reply),
                ),
              )
            : const SizedBox(),
      ),
      appBar: PreferredSize(
        preferredSize: const Size(double.infinity, 56),
        child: Obx(
          () => AppBar(
            titleSpacing: 0,
            title: StreamBuilder(
              stream: _titleStreamC.stream,
              builder: (context, snapshot) {
                return GestureDetector(
                  onTap: () {
                    if (_scrollController.offset != 0) {
                      _scrollController.animToTop();
                    }
                  },
                  child: Container(
                    color: Colors.transparent,
                    width: double.infinity,
                    child: snapshot.data == true
                        ? header(
                            context,
                            (_feedController.feedState.value as Success)
                                .response,
                            true,
                            isHeader: true,
                          )
                        : _feedController.feedState.value is Success
                            ? Text(
                                _feedController.feedTypeName!,
                                textAlign: Platform.isIOS
                                    ? TextAlign.center
                                    : TextAlign.start,
                              )
                            : null,
                  ),
                );
              },
            ),
            actions: _feedController.feedState.value is Success
                ? [
                    PopupMenuButton(
                      onSelected: (FeedMenuItem item) async {
                        switch (item) {
                          case FeedMenuItem.Copy:
                            Utils.copyText(
                                Utils.getShareUri(_id, ShareType.feed)
                                    .toString());
                            break;
                          case FeedMenuItem.Share:
                            SharePlus.instance.share(ShareParams(
                                uri: Utils.getShareUri(_id, ShareType.feed)));
                            break;
                          case FeedMenuItem.Fav:
                            // ★ 云端收藏夹（多收藏夹）。
                            //
                            // 对应 Android 详情页的「收藏到」面板
                            // （`CollectionPickBottomSheet`）。上游这里只往本地
                            // Hive `favFeed` 塞一份，没有多收藏夹的概念。
                            //
                            // 收藏数与星标**只认接口返回值**（面板通过 onAction
                            // 带回来），不做本地加减 —— 多收藏夹下同一个夹重复点、
                            // 别人同时收藏都会让本地算法偏。
                            await showCollectionPickSheet(
                              feedId: _id,
                              onAction: (action) =>
                                  _feedController.onCollectionAction(action),
                            );
                            break;
                          case FeedMenuItem.Block:
                            GStorage.onBlock(
                              _feedController.feedUid.toString(),
                              isDelete: _feedController.isBlocked,
                            );
                            _feedController.isBlocked =
                                !_feedController.isBlocked;
                            break;
                          case FeedMenuItem.Report:
                            if (Utils.isSupportWebview()) {
                              Utils.report(_id, ReportType.Feed);
                            } else {
                              SmartDialog.showToast('not supported');
                            }
                            break;
                        }
                      },
                      itemBuilder: (BuildContext context) => FeedMenuItem.values
                          .map((item) => PopupMenuItem<FeedMenuItem>(
                                value: item,
                                child: item == FeedMenuItem.Fav
                                    ? Text(
                                        _feedController.isFav ? '取消收藏' : '收藏',
                                      )                                    : item == FeedMenuItem.Block
                                        ? Text(
                                            _feedController.isBlocked
                                                ? '取消屏蔽'
                                                : '加入黑名单',
                                          )
                                        : Text(item.label),
                              ))
                          .toList(),
                    )
                  ]
                : null,
          ),
        ),
      ),
      body: Obx(
        () => _feedController.feedState.value is Success
            ? RefreshIndicator(
                notificationPredicate: (notification) {
                  final double offset = notification.metrics.pixels;
                  if (offset > 50 && !_visibleTitle) {
                    _visibleTitle = true;
                    _titleStreamC.add(true);
                  } else if (offset <= 50 && _visibleTitle) {
                    _visibleTitle = false;
                    _titleStreamC.add(false);
                  }
                  return notification.depth == 0;
                },
                key: _refreshKey,
                onRefresh: () async {
                  _feedController.onReset();
                  await _feedController.onGetData();
                },
                child: Utils.isWideLandscape(context)
                    ? Row(children: [
                        Expanded(
                            child: CustomScrollView(
                          controller: _scrollController,
                          physics: const AlwaysScrollableScrollPhysics(
                            parent: BouncingScrollPhysics(),
                          ),
                          slivers: [
                            _feedController.articleList.isNullOrEmpty
                                ? SliverToBoxAdapter(
                                    child: _buildFeedContent(
                                        _feedController.feedState.value))
                                : _buildFeedContent(
                                    _feedController.feedState.value),
                          ],
                        )),
                        const VerticalDivider(width: 1),
                        Expanded(
                            child: CustomScrollView(
                          controller: _scrollController,
                          physics: const AlwaysScrollableScrollPhysics(
                            parent: BouncingScrollPhysics(),
                          ),
                          slivers: [
                            _buildPinWidget(),
                            _buildFeedReply(_feedController.loadingState.value),
                          ],
                        )),
                      ])
                    : CustomScrollView(
                        controller: _scrollController,
                        physics: const AlwaysScrollableScrollPhysics(
                          parent: BouncingScrollPhysics(),
                        ),
                        slivers: [
                          _feedController.articleList.isNullOrEmpty
                              ? SliverToBoxAdapter(
                                  child: _buildFeedContent(
                                      _feedController.feedState.value))
                              : _buildFeedContent(
                                  _feedController.feedState.value),
                          _buildPinWidget(),
                          _buildFeedReply(_feedController.loadingState.value),
                        ],
                      ),
              )
            : Center(child: _buildFeedContent(_feedController.feedState.value)),
      ),
    );
  }
}
