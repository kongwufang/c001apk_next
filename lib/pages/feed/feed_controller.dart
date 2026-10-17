import 'dart:convert';

import 'package:get/get.dart';

import '../../logic/model/fav_history/fav_history.dart';
import '../../logic/model/collection/collection.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/model/feed/user_action.dart';
import '../../logic/model/feed_article/feed_article.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/repo/offline_cache_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';
import '../../pages/feed/feed_page.dart' show ReplySortType;
import '../../utils/extensions.dart';
import '../../utils/storage_util.dart';

class FeedController extends CommonController {
  FeedController({
    required this.id,
    required this.recordHistory,
    this.offline = false,
  });
  final String id;
  final bool recordHistory;

  /// **离线模式**：只读本机存档，不发任何网络请求。
  ///
  /// 对应 Android `FeedActivity` 的 `offline=true` extra +
  /// `FeedViewModel.loadOffline`。离线存档解回 `Datum` 走的是**同一条渲染路径**，
  /// 所以这里只需要换掉「数据从哪来」。
  final bool offline;

  String listType = 'lastupdate_desc';
  final int _discussMode = 1;
  final String _feedType = 'feed';
  final int _blockStatus = 0;
  int fromFeedAuthor = 0;

  String? feedTypeName;
  int? feedUid;
  String? feedUsername;
  int? replyNum;

  List<FeedArticle>? articleList;
  List<String>? articleImgList;

  Datum? topReply;
  Datum? _replyMe;

  Rx<LoadingState> feedState = LoadingState.loading().obs;
  bool isFav = false;
  bool isBlocked = false;

  Rx<ReplySortType> replySelection = ReplySortType.def.obs;

  void setFeedState(LoadingState feedState) {
    this.feedState.value = feedState;
  }

  @override
  Future<LoadingState> customGetData() async {
    if (offline) {
      final entity = OfflineCacheRepo.get(id);
      if (entity == null) return LoadingState.error('这条动态没有离线存档');
      // ★ 存档就这么多：第二页直接给 Empty，让 `onGetData` 收敛到 `isEnd = true`
      //   （Success 会被当成还有下一页，于是无限翻页）
      if (page > 1) return LoadingState.empty();
      return Success(OfflineCacheRepo.replyListOf(entity));
    }
    return NetworkRepo.getFeedReply(
      id: id,
      listType: listType,
      page: page,
      firstItem: firstItem,
      lastItem: lastItem,
      discussMode: _discussMode,
      feedType: _feedType,
      blockStatus: _blockStatus,
      fromFeedAuthor: fromFeedAuthor,
    );
  }

  Future<void> getFeedData() async {
    LoadingState<dynamic> response = offline
        ? _offlineFeedState()
        : await NetworkRepo.getDataFromUrl(url: '/v6/feed/detail?id=$id');
    if (response is Success) {
      Datum data = (response.response as Datum);

      // ★★ 正文解析：只有**真的解析出可渲染的块**才走「图文」布局。
      //
      // 原来的写法是 `if (messageRawOutput != 'null')` —— 但实测服务端对
      // **普通动态**给的是字面量 `"[]"`（空数组，**不是** `"null"`），
      // 于是：
      //   1. 条件成立 → `jsonDecode("[]")` → 一个块都没有；
      //   2. 但紧接着「title 非空就 insert 一个标题块」——
      //      而普通动态的 title 几乎都非空（如「xxx的点评」），
      //      `articleList` 就从 `[]` 变成 `[标题块]`，**非空了**；
      //   3. `feed_page` 里据 `articleList.isNullOrEmpty` 二选一布局，
      //      非空 → 走图文分支 → **只渲染标题 + 回复/赞，
      //      `message` 里的 HTML 正文永远不渲染**。
      //
      // 表现出来就是「详情页只有作者、标题、回复/赞，正文一片空白」，
      // 而且只有**title 非空**的动态才会中招（原标题为空的动态反而正常）。
      //
      // 正确规则：**标题只是正文的抬头，不能单独构成「图文」。**
      final rawBlocks = data.messageRawOutput;
      List<FeedArticle> blocks = const [];
      if (rawBlocks != null && rawBlocks != 'null') {
        try {
          final decoded = jsonDecode(rawBlocks);
          if (decoded is List) {
            blocks = decoded
                .map((json) => FeedArticle.fromJson(json))
                .where(
                    (item) => ['text', 'image', 'shareUrl'].contains(item.type))
                .toList();
          }
        } catch (_) {
          // 形状不对就当没有块 —— 退回渲染 `message`，绝不因此丢正文
          blocks = const [];
        }
      }
      if (blocks.isNotEmpty) {
        articleList = [
          if (!data.messageCover.isNullOrEmpty)
            FeedArticle(type: 'image', url: data.messageCover),
          if (!data.title.isNullOrEmpty)
            FeedArticle(type: 'title', title: data.title),
          ...blocks,
        ];
        articleImgList = articleList!
            .where((item) => item.type == 'image')
            .map((item) => item.url.orEmpty)
            .toList();
      } else {
        // 没有块 → `articleList` 保持 null/空，`feed_page` 会落到
        // `FeedCard(isFeedContent: true)` 那条分支，由它渲染 `message`。
        articleList = null;
        articleImgList = null;
      }
      if (!data.topReplyRows.isNullOrEmpty) {
        topReply = data.topReplyRows![0];
      }
      if (!data.replyMeRows.isNullOrEmpty) {
        _replyMe = data.replyMeRows![0];
      }
      feedUsername = data.userInfo?.username;
      feedUid = data.uid;
      feedTypeName = data.feedTypeName;
      replyNum = data.replynum;
      onGetData();

      isFav = GStorage.checkFav(id);
      isBlocked = GStorage.checkUser(feedUid.toString());
      // todo: check
      if (recordHistory && !GStorage.checkHistory(id)) {
        GStorage.historyFeed.put(id, getFeed(data));
      }
    }
    feedState.value = response;
  }

  void onFav() {
    Datum data = (feedState.value as Success).response;
    GStorage.favFeed.put(id, getFeed(data));
  }

  /// 离线存档 → `Success(Datum)`。存档解回 `Datum` 后，
  /// 详情页走的渲染路径与在线完全一致（图片已经是本地 `file://` 地址）。
  LoadingState<dynamic> _offlineFeedState() {
    final entity = OfflineCacheRepo.get(id);
    if (entity == null) return LoadingState.error('这条动态没有离线存档');
    final data = OfflineCacheRepo.feedDataOf(entity);
    if (data == null) return LoadingState.error('离线存档已损坏');
    return Success(data);
  }

  /// 收藏夹面板（「收藏到」）回传的净结果。
  ///
  /// ★ **只认接口返回值**，不做本地加减 —— 多收藏夹下同一个夹重复点、
  /// 别人同时收藏都会让本地算法偏；详情那份 `favnum` 还有服务端缓存滞后，
  /// 重拉反而更不准。所以面板把 `addItem` 的响应直接带过来，这里照单收下。
  ///
  /// 顺带仍往本地 `favFeed` 留/删一份：本工程的「浏览历史 / 本地收藏」页
  /// 读的还是这个 Hive 盒子，先保持它们能用。
  void onCollectionAction(CollectionAction action) {
    isFav = action.collect == 1;
    if (isFav) {
      try {
        final data = (feedState.value as Success).response;
        GStorage.favFeed.put(id, getFeed(data));
      } catch (_) {
        // 详情还没加载好就收藏：跳过本地缓存，不影响云端状态
      }
    } else {
      GStorage.onDeleteFeed(id, isHistory: false);
    }
  }

  FavHistoryItem getFeed(Datum data) {
    return FavHistoryItem(
      id: data.id.toString(),
      uid: data.uid.toString(),
      username: data.userInfo?.username,
      userAvatar: data.userInfo?.userAvatar,
      message: (data.message ?? '').length <= 150
          ? data.message
          : data.message!.substring(0, 150),
      device: data.deviceTitle,
      dateline: data.dateline.toString(),
      time: DateTime.now().microsecondsSinceEpoch,
    );
  }

  @override
  void onInit() {
    super.onInit();
    getFeedData();
  }

  void onBlockReply(dynamic uid, dynamic id) {
    List<Datum> replyList = (loadingState.value as Success).response;
    if (id != null) {
      replyList = replyList.map((reply) {
        if (reply.id == id) {
          return reply
            ..replyRows =
                reply.replyRows!.where((reply) => reply.uid != uid).toList();
        } else {
          return reply;
        }
      }).toList();
    } else {
      replyList = replyList.where((reply) => reply.uid != uid).toList();
    }
    loadingState.value = LoadingState.success(replyList);
  }

  @override
  List<Datum>? handleResponse(List<Datum> dataList) {
    List<Datum> filterList = dataList;
    if (page == 1 && listType == 'lastupdate_desc') {
      filterList = [
            if (topReply != null) topReply!,
            if (_replyMe != null) _replyMe!,
          ] +
          filterList;
    }
    List<String> userBlackList = GStorage.blackList
        .get(BlackListBoxKey.userBlackList, defaultValue: <String>[]);
    return filterList
        .map((data) => data
          ..replyRows = !data.replyRows.isNullOrEmpty
              ? data.replyRows!
                  .where(
                      (reply) => !userBlackList.contains(reply.uid.toString()))
                  .toList()
              : null)
        .toList();
  }

  void updateReply(bool isReply, Datum data, dynamic id, dynamic fid) {
    List<Datum> replyList = loadingState.value is Success
        ? (loadingState.value as Success).response
        : [];
    if (isReply) {
      replyList = replyList.map((reply) {
        if (reply.id == (fid ?? id)) {
          return reply..replyRows = (reply.replyRows ?? []) + [data];
        } else {
          return reply;
        }
      }).toList();
    } else {
      replyList.insert(topReply == null ? 0 : 1, data);
    }
    loadingState.value = LoadingState.success(replyList);
  }

  @override
  bool handleLike(dynamic like, dynamic likenum) {
    Datum data = (feedState.value as Success).response;
    feedState.value = LoadingState.success(data
      ..likenum = likenum
      ..userAction = UserAction(like: like == 1 ? 0 : 1));
    return true;
  }
}
