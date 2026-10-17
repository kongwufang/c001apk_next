import 'package:flutter/material.dart';
import 'package:get/get.dart';

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

  /// 一个宫格入口。尺寸严格照 `item_message_mess.xml`：
  /// 42dp 圆形彩色底 + 24dp 白色图标 + 12sp 标题（marginTop 8）+ 上下 padding 10。
  Widget _entryCell(int i, int? badge) {
    return InkWell(
      onTap: () {
        Get.toNamed('/notification', arguments: {'type': _types[i]});
        // 点过就把红点清掉（Android 是「离开本页时落账」，这里简化为点即清）
        if ((badge ?? 0) > 0) {
          _controller.badges[i] = null;
        }
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            SizedBox(
              width: 42,
              height: 42,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  // 圆形彩色底
                  Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: Color(_colors[i]),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(_icons[i], size: 24, color: Colors.white),
                  ),
                  // 未读红点（Android 由 MessageBadgeDecoration 画在顶层；
                  // 这里用 Badge 挂在圆形右上角，效果等价）
                  if ((badge ?? 0) > 0)
                    Positioned(
                      right: -4,
                      top: -4,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        constraints:
                            const BoxConstraints(minWidth: 16, minHeight: 16),
                        decoration: const BoxDecoration(
                          color: Colors.red,
                          shape: BoxShape.circle,
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          badge! > 99 ? '99+' : '$badge',
                          style: const TextStyle(
                            fontSize: 10,
                            color: Colors.white,
                            height: 1,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Text(
              _titles[i],
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12),
            ),
          ],
        ),
      ),
    );
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
                // 宫格：**竖直小格子**，尺寸照抄 Android `item_message_mess.xml`
                //   圆形彩色底 42dp + 白色图标 24dp + 标题 12sp(marginTop 8) + 上下 padding 10
                //   总高 ≈ 87dp；4 列**紧挨着**（crossAxisSpacing 0），左右 padding 8
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 4,
                      crossAxisSpacing: 0,
                      mainAxisSpacing: 0,
                      // ★ 用固定格高而不是 childAspectRatio ——
                      //   aspectRatio 会让格高随窗口宽度变化（宽屏下被拉得很大）
                      mainAxisExtent: 87,
                    ),
                    itemCount: _titles.length,
                    itemBuilder: (context, i) => Obx(
                      () => _entryCell(
                        i,
                        _badgeAt(i),
                      ),
                    ),
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
