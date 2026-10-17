import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import '../../components/cards/user_info_card.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/user/user_controller.dart';
import '../../pages/user/user_tab.dart';
import '../../utils/device_util.dart';
import '../../utils/extensions.dart';
import '../../utils/shortcuts.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';
import '../../components/m3e_progress.dart';

// ignore: constant_identifier_names
enum UserMenuItem {
  Copy('复制'),       // copyText
  Share('分享'),      // share
  Block('加入黑名单'), // block
  Report('举报'),     // report
  UserInfo('用户信息');

  const UserMenuItem(this.label);
  final String label;
}

class UserPage extends StatefulWidget {
  const UserPage({super.key});

  @override
  State<UserPage> createState() => _UserPageState();
}

class _UserPageState extends State<UserPage>
    with TickerProviderStateMixin {
  final String _uid = Get.parameters['uid'].orEmpty;

  late final String _random = DeviceUtil.randHexString(8);
  late final UserController _userController = Get.put(
    UserController(uid: _uid),
    tag: _uid + _random,
  );

  /// 个人主页的多标签（动态 / 点评 / 图文 / 问答 / 酷图）——
  /// 照抄 Android `UserPagerFragment` 的 tabType / tabTitle。
  late final TabController _tabController = TabController(
    length: UserTabType.values.length,
    vsync: this,
  );

  @override
  void dispose() {
    _tabController.dispose();
    _userController.scrollController?.dispose();
    Get.delete<UserController>(
      tag: _uid + _random,
    );
    super.dispose();
  }

  Widget _buildUserInfo(LoadingState userState) {
    switch (userState) {
      case Empty():
        return GestureDetector(
          onTap: () {
            _userController.setUserState(LoadingState.loading());
            _userController.onGetUserData();
          },
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: const Text('暂无内容'),
          ),
        );
      case Error():
        return GestureDetector(
          onTap: () {
            _userController.setUserState(LoadingState.loading());
            _userController.onGetUserData();
          },
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.all(10.0),
            child: Text(userState.errMsg),
          ),
        );
      case Success():
        // ★ 作者主页的快捷键（页面级）：`s` = 关注/取消、`m` = 私信
        //   注意**不注册** `share` —— `ShortcutScope` 有兜底：
        //   share 槽位为空时 `s` 自动落到 `follow` ✓
        return ShortcutPage(
          actions: {
            ShortcutBus.follow: () {
              if (GlobalData().isLogin) {
                _userController.onFollow(
                  userState.response!.uid,
                  userState.response!.isFollow,
                );
              }
            },
            ShortcutBus.message: () {
              if (!GlobalData().isLogin) return;
              final me = GlobalData().uid;
              final other = _userController.uid;
              final ukey =
                  (int.tryParse(me) ?? 0) < (int.tryParse(other) ?? 0)
                      ? '${me}_$other'
                      : '${other}_$me';
              Get.toNamed('/chat', parameters: {
                'ukey': ukey,
                'uid': other,
                'username': userState.response!.username ?? '',
              });
            },
          },
          child: UserInfoCard(
          data: userState.response!,
          onFollow: _userController.onFollow,
          // ★ 自己看自己 → 显示「编辑资料」而不是「关注」
          //   （对齐 Android：那个位置是 `EditProfileActivity` 入口）
          isMe: _uid == GStorage.uid,
          onEditProfile: () {
            // Android 是**原生**编辑资料页，Flutter 侧还没移植。
            // 先给明确提示，不假装能编辑（见台账待办）。
            SmartDialog.showToast('编辑资料页尚未移植');
          },
          ),
        );
    }
    return const M3EWavyProgressIndicator();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          Obx(
            () => _userController.userState.value is Success
                ? RefreshIndicator(
                    notificationPredicate: (notification) {
                      final double offset = notification.metrics.pixels;
                      if (offset >= 0) {
                        _userController.scrollRatio.value =
                            min(1.0, offset / 105);
                      }
                      return notification.depth == 0;
                    },
                    onRefresh: () async {
                      if (!_userController.isBlocked) {
                        _userController.onReset();
                        await _userController.onGetData();
                      }
                    },
                    // ★ 个人主页 = 「全屏封面头部 + 多标签」。
                    //
                    // 对应 Android `BasePagerFragment` 那套
                    // CollapsingToolbar + TabLayout + ViewPager 的嵌套滚动结构，
                    // Flutter 的等价物就是 `NestedScrollView` + 固定的 TabBar +
                    // `TabBarView`（头部随滚动收起，TabBar 钉在顶部）。
                    child: NestedScrollView(
                      headerSliverBuilder: (_, __) => [
                        SliverToBoxAdapter(
                          child: _buildUserInfo(
                              _userController.userState.value),
                        ),
                        SliverPersistentHeader(
                          pinned: true,
                          delegate: _UserTabBarHeader(
                            TabBar(
                              controller: _tabController,
                              isScrollable: true,
                              tabAlignment: TabAlignment.start,
                              tabs: UserTabType.values
                                  .map((t) => Tab(text: t.label))
                                  .toList(),
                            ),
                            Theme.of(context).colorScheme.surface,
                          ),
                        ),
                      ],
                      body: TabBarView(
                        controller: _tabController,
                        children: UserTabType.values
                            .map((t) => UserTabContent(uid: _uid, type: t))
                            .toList(),
                      ),
                    ),
                  )
                : Center(
                    child: _buildUserInfo(_userController.userState.value),
                  ),
          ),
          _header(),
        ],
      ),
    );
  }

  Widget _header() {
    return Positioned(
      left: 0,
      right: 0,
      top: 0,
      child: Obx(
        () => AppBar(
          backgroundColor: Theme.of(context)
              .colorScheme
              .surface
              .withValues(alpha: _userController.scrollRatio.value),
          title: _userController.scrollRatio.value == 1 &&
                  _userController.username != null
              ? Text(_userController.username!)
              : null,
          centerTitle: Platform.isIOS,
          actions: _userController.userState.value is Success
              ? [
                  if (!_userController.isBlocked)
                    IconButton(
                      onPressed: () => Get.toNamed('/search', parameters: {
                        'title': _userController.username!,
                        'pageType': 'user',
                        'pageParam': _userController.uid,
                      }),
                      icon: const Icon(Icons.search),
                      tooltip: '搜索',
                    ),
                  PopupMenuButton(
                    onSelected: (UserMenuItem item) {
                      switch (item) {
                        case UserMenuItem.Copy:
                          Utils.copyText(Utils.getShareUri(
                                  _userController.uid, ShareType.u)
                              .toString());
                          break;
                        case UserMenuItem.Share:
                          // ★ 分享一律**直接复制链接**
                          Utils.copyText(Utils.getShareUri(
                                  _userController.uid, ShareType.u)
                              .toString());
                          break;
                        case UserMenuItem.Block:
                          GStorage.onBlock(
                            _userController.uid,
                            isDelete: _userController.isBlocked,
                          );
                          if (!_userController.isBlocked) {
                            _userController.setBlockedLoaidngState();
                          }
                          _userController.isBlocked =
                              !_userController.isBlocked;
                          break;
                        case UserMenuItem.Report:
                          if (Utils.isSupportWebview()) {
                            Utils.report(_userController.uid, ReportType.User);
                          } else {
                            SmartDialog.showToast('当前平台不支持');
                          }
                          break;
                        case UserMenuItem.UserInfo:
                          showDialog<void>(
                            context: context,
                            builder: (BuildContext context) {
                              Datum user =
                                  (_userController.userState.value as Success)
                                      .response;
                              return AlertDialog(
                                title: Text(
                                  user.username ?? '',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                content: Text('UID: ${user.uid}\n'
                                    '等级: Lv.${user.level}\n'
                                    '性别: ${user.gender == 1 ? '男' : user.gender == 0 ? '女' : '未知'}\n'
                                    '注册时长: ${((DateTime.now().microsecondsSinceEpoch ~/ 10e5 - user.regdate!) ~/ 24 ~/ 3600)} 天\n'
                                    '注册时间: ${DateFormat('yyyy年MM月dd日 HH:mm:ss').format(DateTime.fromMillisecondsSinceEpoch(user.regdate * 1000))}'),
                              );
                            },
                          );
                          break;
                      }
                    },
                    itemBuilder: (BuildContext context) => UserMenuItem.values
                        .map((item) => PopupMenuItem<UserMenuItem>(
                              value: item,
                              child: item == UserMenuItem.Block
                                  ? Text(
                                      _userController.isBlocked
                                          ? '移出黑名单'
                                          : '加入黑名单',
                                    )
                                  : Text(item.label),
                            ))
                        .toList(),
                  ),
                ]
              : null,
        ),
      ),
    );
  }
}

/// 钉在顶部的 TabBar（`SliverPersistentHeader` 的 delegate）。
class _UserTabBarHeader extends SliverPersistentHeaderDelegate {
  _UserTabBarHeader(this.tabBar, this.background);

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
  bool shouldRebuild(_UserTabBarHeader oldDelegate) =>
      oldDelegate.tabBar != tabBar || oldDelegate.background != background;
}
