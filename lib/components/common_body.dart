import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:get/get.dart';

import '../components/footer.dart';
import '../components/item_card.dart';
import '../logic/model/feed/datum.dart';
import '../logic/repo/spam_config_repo.dart';
import '../logic/state/loading_state.dart';
import '../pages/common/common_controller.dart';
import '../utils/utils.dart';
import 'm3e_progress.dart';

Widget _bodyState(String text, Function() onTap) {
  return GestureDetector(
    onTap: onTap,
    child: Container(
      alignment: Alignment.center,
      padding: const EdgeInsets.all(10.0),
      child: Text(text),
    ),
  );
}

Widget commonBody(
  CommonController commonController, {
  bool isHomeCard = false,
  bool isReply2Reply = false,
  /// `feedCover` 是否按完整动态卡排（见 `item_card.dart` 的同名参数）
  bool feedCoverAsFeed = false,
  dynamic uid,
  /// 列表**最上面**额外插入的一块（目前只有产品/话题「讨论」tab 的
  /// 三段式排序控件用；见 `components/sort_header.dart`）。
  Widget? header,
}) {
  return Obx(
    () => commonController.loadingState.value is Success
        ? RefreshIndicator(
            key: commonController.refreshKey,
            onRefresh: () async {
              commonController.onReset();
              await commonController.onGetData();
            },
            child: buildBody(
              commonController,
              isHomeCard: isHomeCard,
              isReply2Reply: isReply2Reply,
              feedCoverAsFeed: feedCoverAsFeed,
              uid: uid,
              header: header,
            ),
          )
        : Center(
            child: buildBody(
              commonController,
              isHomeCard: isHomeCard,
              isReply2Reply: isReply2Reply,
              feedCoverAsFeed: feedCoverAsFeed,
              uid: uid,
              header: header,
            ),
          ),
  );
}

Widget buildBody(
  CommonController commonController, {
  bool isHomeCard = false,
  bool isReply2Reply = false,
  /// `feedCover` 是否按完整动态卡排（见 `item_card.dart` 的同名参数）
  bool feedCoverAsFeed = false,
  dynamic uid,
  /// 插到列表最前面的一块（三段式排序控件）
  Widget? header,
  Function(
    dynamic id,
    dynamic uname,
    dynamic fid,
  )? onReply,
}) {
  switch (commonController.loadingState.value) {
    case Empty():
      return _bodyState(
        '暂无内容',
        () {
          commonController.isEnd = false;
          commonController.setLoadingState(LoadingState.loading());
          commonController.onGetData(true);
        },
      );
    case Error():
      return _bodyState(
        (commonController.loadingState.value as Error).errMsg,
        () {
          commonController.isEnd = false;
          commonController.setLoadingState(LoadingState.loading());
          commonController.onGetData(true);
        },
      );
    case Success():
      var dataList = (commonController.loadingState.value as Success).response
          as List<Datum>;

      // ★ 屏蔽项过滤（关键字 / 用户 / 节点）。
      //
      // 对应 Android `HomeFeedViewModel` 里逐条调 `SpamConfigRepo.isSpam` 的逻辑。
      // 命中就不显示 —— 这里在**列表出数据前**统一过滤，
      // 所以首页/话题/用户页等所有走 commonBody 的页面都生效。
      //
      // 注意：**不改动 `loadingState` 里的原始列表**，只过滤本地这一份视图，
      // 否则分页判断（`isEnd` / 去重）会被污染。
      if (SpamConfigRepo.current.isNotEmpty) {
        dataList = dataList.where((e) => !SpamConfigRepo.isSpam(e)).toList();
      }

      var bottomPadding = MediaQuery.of(Get.context!).padding.bottom;

      /// 列表底部（加载更多 / 到底提示）。与原来一样的触发时机：
      /// 一旦被构建就视作「翻到了底部」，去请求下一页。
      Widget footer() {
        if (!commonController.isEnd && !commonController.isLoading) {
          commonController.setFooterState(LoadingState.loading());
          commonController.onGetData(false);
        }
        return Obx(
            () => footerWidget(commonController.footerState.value, () {
                  commonController.isEnd = false;
                  commonController.setFooterState(LoadingState.loading());
                  commonController.onGetData(false);
                }));
      }

      Widget cell(int index) => itemCard(
            dataList[index],
            isHomeCard: isHomeCard,
            isReply2Reply: isReply2Reply,
            isTopReply: isReply2Reply && index == 0,
            feedCoverAsFeed: feedCoverAsFeed,
            uid: uid,
            onBlock: commonController.onBlock,
            onReply: onReply,
            onDelete: commonController.postLikeDeleteFollow,
            onStickTop: commonController.postStickTop,
            onStickTop: commonController.postStickTop,
            onLike: commonController.onLike,
            onPM: commonController.onPM,
          );

      // ── 横屏：双列瀑布流 ──
      //
      // 对齐 Android 端 `BaseViewFragment.initView()`：
      //   if (isPortrait) LinearLayoutManager
      //   else            StaggeredGridLayoutManager(2, VERTICAL)
      // 间距来自 `StaggerItemDecoration(10.dp)`：列间距 10dp、左右外边距 10dp、bottom 10dp。
      // footer 走 SliverToBoxAdapter 跨两列，对应 Android `FooterAdapter` 的 isFullSpan = true。
      //
      // 回复楼中楼（isReply2Reply）保持单列 —— 那一层的 Android 实现用的是
      // `ReplyStaggerItemDecoration`，是另一套排法，不在这里改。
      if (!isReply2Reply && !Utils.isPortrait(Get.context!)) {
        return CustomScrollView(
          controller: commonController.scrollController,
          physics: const AlwaysScrollableScrollPhysics(
            parent: BouncingScrollPhysics(),
          ),
          slivers: [
            if (header != null) SliverToBoxAdapter(child: header),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(10, 10, 10, 0),
              sliver: SliverMasonryGrid.count(
                crossAxisCount: 2,
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childCount: dataList.length,
                itemBuilder: (_, index) => cell(index),
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.only(left: 10, right: 10, top: 10, bottom: 10 + bottomPadding),
                child: footer(),
              ),
            ),
          ],
        );
      }

      return ListView.separated(
        controller: commonController.scrollController,
        physics: AlwaysScrollableScrollPhysics(
          parent: isReply2Reply
              ? const ClampingScrollPhysics()
              : const BouncingScrollPhysics(),
        ),
        padding: isReply2Reply
            ? EdgeInsets.only(bottom: bottomPadding)
            : EdgeInsets.only(
                left: 10, top: 10, right: 10, bottom: 10 + bottomPadding),
        // 有 header 时多一项（header 固定在最前，不吃 separator）
        itemCount: dataList.length + 1 + (header != null ? 1 : 0),
        itemBuilder: (_, index) {
          // 三段式排序控件（产品/话题「讨论」tab）
          if (header != null && index == 0) return header;
          final i = header != null ? index - 1 : index;
          if (i == dataList.length) {
            return footer();
          } else {
            return cell(i);
          }
        },
        separatorBuilder: (_, index) => isReply2Reply
            ? const Divider(height: 1)
            : const SizedBox(height: 10),
      );
  }
  return Container(
    height: 80,
    alignment: Alignment.center,
    padding: const EdgeInsets.all(10.0),
    child: const M3EWavyProgressIndicator(),
  );
}
