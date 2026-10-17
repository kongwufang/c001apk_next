import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/cards/message_third_card.dart';
import '../../components/common_body.dart';
import '../../pages/noitfication/notification_page.dart';
import '../../utils/global_data.dart';
import 'message_center_controller.dart';

/// 消息中心。
///
/// **布局照抄 Android `activity_message_center.xml`**（那个文件的注释写得很清楚）：
/// > 消息中心：工具栏 + 消息入口宫格（4 列，固定在顶部不滚动）+ 通知列表。
/// > 入口宫格不进列表，是为了让「未读红点」始终可见，不用滚回顶部才能看。
///
/// ```xml
/// AppBarLayout
/// ├── MaterialToolbar           title="消息中心" navigationIcon=ic_back
/// ├── RecyclerView entryList    GridLayoutManager(4)，不滚动，左右 padding 8dp
/// └── View 1px 分隔线
/// SwipeRefreshLayout → RecyclerView   通知列表（混合未读）
/// Button clickToLogin                未登录时居中
/// ```
///
/// ## 与我旧版的区别
/// 旧版是「我的 + 消息」混在一起：用户信息卡（头像/等级/经验条）、
/// 动态-关注-粉丝统计卡、5 个分类各占一行、再加底部一个按分类分块的列表 ✗
///
/// Android 2026-10-05 把这页重排了（见 `fragment_mine.xml` 的注释）：
/// 消息入口搬去首页工具栏、通知列表独立成 `MessageCenterActivity`，
/// 「我的」只剩个人中心。所以这里也只剩「宫格 + 混合未读列表」。
class MessagePage extends StatefulWidget {
  const MessagePage({super.key});

  @override
  State<MessagePage> createState() => _MessagePageState();
}

class _MessagePageState extends State<MessagePage>
    with AutomaticKeepAliveClientMixin {
  final _controller = Get.put(MessageCenterController(), tag: 'messageCenter');

  /// 宫格五个入口 —— 对齐 Android `MessageThirdAdapter` 的 5 项，
  /// 顺序与 `NotificationType` 一致（AT / COMMENT / LIKE / FOLLOW / MESSAGE）。
  static const _titles = ['@我的动态', '@我的评论', '我收到的赞', '好友关注', '私信'];
  static const _types = [
    NotificationType.AT,
    NotificationType.COMMENT,
    NotificationType.LIKE,
    NotificationType.FOLLOW,
    NotificationType.MESSAGE,
  ];
  static const _icons = [
    Icons.alternate_email,
    Icons.chat_bubble_outline,
    Icons.thumb_up_alt_outlined,
    Icons.person_add_alt_1_outlined,
    Icons.mail_outline,
  ];
  static const _colors = [
    0xFF3B82F6,
    0xFF10B981,
    0xFFEF4444,
    0xFF8B5CF6,
    0xFFF59E0B,
  ];

  @override
  bool get wantKeepAlive => true;

  /// 安全取红点：`RxList` 没有 `getOrNull`（那是 `extensions.dart` 的扩展，
  /// 这个文件没 import 它）。直接用下标 + 长度判断，零依赖。
  int? _badgeAt(int i) {
    final list = _controller.badges;
    return i < list.length ? list[i] : null;
  }

  @override
  void initState() {
    super.initState();
    _controller.onGetData();
    _controller.refreshBadges();
  }

  Future<void> _onRefresh() async {
    await _controller.refreshBadges();
    await _controller.onGetData();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('消息中心'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Get.back(),
        ),
      ),
      body: !GlobalData().isLogin
          // 未登录：居中「点击登录」（对齐 Android 的 `clickToLogin` Button）
          ? Center(
              child: FilledButton(
                onPressed: () async {
                  if ((await Get.toNamed('/login')) == true) {
                    _onRefresh();
                  }
                },
                child: const Text('点击登录'),
              ),
            )
          : Column(
              children: [
                // ★ 宫格**固定在顶部，不随列表滚动** ——
                //   Android 原话：「入口宫格不进列表，是为了让『未读红点』
                //   始终可见，不用滚回顶部才能看。」
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: GridView.count(
                    crossAxisCount: 4,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    children: List.generate(_titles.length, (i) {
                      return Obx(
                        () => messageThirdCard(
                          context,
                          _colors[i],
                          _icons[i],
                          _titles[i],
                          _badgeAt(i),
                          () {
                            Get.toNamed('/notification',
                                arguments: {'type': _types[i]});
                            // 点过就把红点清掉
                            // （Android 是「离开本页时落账」，这里简化为点即清）
                            if ((_badgeAt(i) ?? 0) > 0) {
                              _controller.badges[i] = null;
                            }
                          },
                        ),
                      );
                    }),
                  ),
                ),
                const Divider(height: 1),
                // 混合未读列表：分页 / 下拉刷新 / footer / 空态全是 `commonBody` 现成的
                Expanded(child: commonBody(_controller)),
              ],
            ),
    );
  }
}
