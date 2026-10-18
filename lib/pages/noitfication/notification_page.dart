import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/common_body.dart';
import '../../pages/noitfication/notification_controller.dart';

// ignore: constant_identifier_names
/// 消息中心的分类 —— 顺序与 Android `MessageThirdAdapter.messTitle` **完全一致** ✓
/// @我的动态 / @我的评论 / 我收到的赞 / 好友关注 / 我的回复 / 私信 / 酷安小秘书
enum NotificationType {
  AT,
  COMMENT,
  LIKE,
  FOLLOW,
  /// 我的回复（别人回复我的评论）—— 走 V18 通知流 ✓
  COMMENT_ME,
  MESSAGE,
  /// 酷安小秘书 —— 登录提醒 / 站内信，`/v6/notification/list` ✓
  SECRETARY,
}

class NotificationPage extends StatefulWidget {
  const NotificationPage({super.key});

  @override
  State<NotificationPage> createState() => _NotificationPageState();
}

class _NotificationPageState extends State<NotificationPage> {
  final NotificationType type = Get.arguments['type'];
  late final String _title = switch (type) {
    NotificationType.AT => '@我的动态',
    NotificationType.COMMENT => '@我的评论',
    NotificationType.LIKE => '我收到的赞',
    NotificationType.FOLLOW => '好友关注',
    NotificationType.COMMENT_ME => '我的回复',
    NotificationType.MESSAGE => '私信',
    NotificationType.SECRETARY => '酷安小秘书',
  };
  late final _notificationController = Get.put(
    NotificationController(
      url: switch (type) {
        NotificationType.AT => '/v6/notification/atMeList',
        NotificationType.COMMENT => '/v6/notification/atCommentMeList',
        NotificationType.LIKE => '/v6/notification/feedLikeList',
        NotificationType.FOLLOW => '/v6/notification/contactsFollowList',
        NotificationType.MESSAGE => '/v6/message/list',
        // 我的回复：V18 通知流（对齐 Android `NotificationV18Kit.URL`）
        NotificationType.COMMENT_ME => '/v6/notificationV18/list',
        // 酷安小秘书：老通知接口（`/v6/message/list` 里没有它 ✗）
        NotificationType.SECRETARY => '/v6/notification/list',
      },
    ),
    tag: type.name,
  );

  @override
  void dispose() {
    _notificationController.scrollController?.dispose();
    Get.delete<NotificationController>(
      tag: type.name,
    );
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_title),
        bottom: const PreferredSize(
          preferredSize: Size.zero,
          child: Divider(height: 1),
        ),
      ),
      body: commonBody(_notificationController),
    );
  }
}
