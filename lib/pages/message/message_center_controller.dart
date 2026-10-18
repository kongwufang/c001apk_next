import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart' hide Response;

import '../../logic/model/check_count/check_count.dart';
import '../../logic/model/check_count/datum.dart' as checkcountdata;
import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';

/// 消息中心（`/message`）的控制器。
///
/// 移植自 Android `MessageCenterViewModel` + `MessageCenterAdapter`。
///
/// ## Android 的做法
/// 顶部是**分类宫格**（4 列、固定不滚，红点始终可见），下面是**混合未读列表** ——
/// 把五类未读混排在一起，卡片样式按分类复用三套布局：
/// - feed 卡片：`atMe` / `atCommentMe` / `feedLike`
/// - 用户条目：`contactsFollow`
/// - 通知条目：`commentMe`（评论回复，note 富文本）
///
/// 所以同一条通知在「消息中心汇总」和「点进去的分类页」里长得一模一样。
///
/// ## 这里的取舍
/// - **合并列表**：拿 4 个分类端点各取一页，按 `dateline` 倒序合并。
/// - **未做**：Android 的 `NotificationV18Kit`（`commentMe` / `feedLike` 走 V18 统一
///   通知流）和 `MessageCenterSeenStore`（「看过即已读」本机账本）。
///   没有这两样，`commentMe`（评论回复）这一类不会出现在汇总里，
///   红点也不会「看过就消」—— 记为待办，见台账。
class MessageCenterController extends CommonController {
  /// 七类未读的红点，顺序**必须**与 `message_page.dart` 的宫格一致 ✓
  /// 对齐 Android `UnreadCounter.gridCategories`：
  /// ```
  /// atMe / atCommentMe / feedLike / contactsFollow / commentMe / message / secretary
  /// ```
  final RxList<int?> badges = <int?>[].obs;

  /// 未读汇总的数据来源：分类 → 端点。
  ///
  /// 注意这里**没有** `/v6/notification/list` —— Android 的注释说它
  /// 「一页 20 条里 15 条是酷安小秘书刷屏、正文还是 HTML，而且一条
  /// 『回复了你的评论』都没有」，已被 V18 端点取代。
  static const List<String> sources = [
    '/v6/notification/atMeList',
    '/v6/notification/atCommentMeList',
    '/v6/notification/feedLikeList',
    '/v6/notification/contactsFollowList',
  ];

  /// 拉一次各类未读数（宫格红点用）
  Future<void> refreshBadges() async {
    try {
      Response response = await NetworkRepo.checkCount();
      checkcountdata.Datum? data = CheckCount.fromJson(response.data).data;
      badges.value = [
        data?.atme,
        data?.atcommentme,
        data?.feedlike,
        data?.contactsFollow,
        // ★ 2026-10-18 补齐最后两类（宫格从 5 项扩到 7 项）
        //   commentme = 「我的回复」的未读 ✓
        //   notification = 「酷安小秘书」的未读 ✓（Android 也是读这一个 ✓）
        data?.commentme,
        data?.message,
        data?.notification,
      ];
    } catch (e) {
      debugPrint('MessageCenterController.refreshBadges: $e');
    }
  }

  /// 混合未读列表：四类各取一页 → 按 `dateline` 倒序合并。
  @override
  Future<LoadingState> customGetData() async {
    final results = await Future.wait(
      sources.map(
        (url) => NetworkRepo.getDataListFromUrl(
          url: url,
          data: {'page': page},
        ),
      ),
    );

    final merged = <Datum>[];
    for (final r in results) {
      if (r is Success) {
        final list = r.response;
        if (list is List) {
          for (final item in list) {
            if (item is Datum) merged.add(item);
          }
        }
      }
    }
    // 混合列表要按时间倒序，否则四类会一段一段地堆着
    merged.sort(
      (a, b) => (b.dateline ?? 0).compareTo(a.dateline ?? 0),
    );
    return LoadingState.success(merged);
  }
}
