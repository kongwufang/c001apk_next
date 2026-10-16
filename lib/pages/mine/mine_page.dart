import 'dart:io';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/network_image.dart';
import '../../logic/model/user_profile/user_profile.dart';
import '../../logic/network/network_repo.dart';
import '../../pages/ffflist/ffflist_page.dart';
import '../../pages/settings/settings_page.dart';
import '../../pages/user/edit_profile_page.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';

/// 「我的」页。
///
/// 对齐 Android 端 `ui/message/MineFragment.kt` + `fragment_mine.xml`：
/// ```
/// toolBar            标题「我的」；右上角菜单 = 退出登录（登录时才显示）+ 设置
/// ├ 头部用户卡        avatar 60dp 圆形(marginStart 20dp) + name 18sp
/// │                   + level 12sp + exp 10sp + 等级进度条
/// ├ 统计卡（仅登录）   动态 / 关注 / 粉丝   —— 未登录整卡不占位
/// └ 功能宫格卡        8 格 4 列 × 2 行，图标 24dp + 文字 12sp(marginTop 8dp)
///                     卡片 12dp 圆角；其中 5 格需登录
/// ```
/// Android 用 `ConcatAdapter(HeaderAdapter, MessageFirstAdapter, MineMenuAdapter)`，
/// 横屏下走 `StaggeredGridLayoutManager(2)` 且这三块都是 `isFullSpan = true`，
/// 所以视觉上就是三张通栏卡纵向排列 —— 这里用 ListView 等价实现。
class MinePage extends StatefulWidget {
  const MinePage({super.key});

  @override
  State<MinePage> createState() => _MinePageState();
}

class _MinePageState extends State<MinePage> {
  UserProfileData? _profile;

  bool get _isLogin => GlobalData().isLogin;

  @override
  void initState() {
    super.initState();
    if (_isLogin) _fetchProfile();
  }

  Future<void> _fetchProfile() async {
    final uid = GStorage.uid;
    if (uid.isEmpty) return;
    try {
      final response = await NetworkRepo.getProfile(uid);
      if (response.statusCode == HttpStatus.ok && response.data != null) {
        final profile = UserProfile.fromJson(
          Map<String, dynamic>.from(response.data as Map),
        );
        final data = profile.data;
        if (data != null) {
          // 与 Android MineViewModel.fetchProfile() 一样，顺手把本地缓存刷一遍
          GStorage.setUsername(Uri.encodeComponent(data.username));
          GStorage.setUserAvatar(data.userAvatar);
          GStorage.setLevel(int.tryParse(data.level) ?? 0);
          GStorage.setExp(data.experience);
          GStorage.setNextExp(data.nextLevelExperience);
        }
        if (mounted) setState(() => _profile = data);
      }
    } catch (e) {
      debugPrint('getProfile failed: $e');
    }
  }

  Future<void> _doLogout() async {
    final ok = await Get.dialog<bool>(
      AlertDialog(
        title: const Text('退出登录'),
        content: const Text('确定要退出当前账号吗？'),
        actions: [
          TextButton(onPressed: () => Get.back(result: false), child: const Text('取消')),
          TextButton(onPressed: () => Get.back(result: true), child: const Text('确定')),
        ],
      ),
    );
    if (ok != true) return;
    GStorage.setIsLogin(false);
    GStorage.setUid('');
    GStorage.setUsername('');
    GStorage.setToken('');
    GStorage.setUserAvatar('');
    GStorage.setLevel(0);
    GStorage.setExp(0);
    GStorage.setNextExp(0);
    GlobalData().isLogin = false;
    GlobalData().uid = '';
    GlobalData().username = '';
    GlobalData().token = '';
    setState(() => _profile = null);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的'),
        actions: [
          // 编辑资料（对应 Android 个人主页的 EditProfileActivity；需登录）
          if (_isLogin)
            IconButton(
              onPressed: () async {
                final changed = await Get.to(() => const EditProfilePage());
                // 改过就重拉资料
                if (changed == true) _fetchProfile();
              },
              icon: const Icon(Icons.edit_outlined),
              tooltip: '编辑资料',
            ),
          // 退出登录在设置左边（对齐 Android message_menu：logout 在 settings 左侧）
          if (_isLogin)
            IconButton(
              onPressed: _doLogout,
              icon: const Icon(Icons.logout),
              tooltip: '退出登录',
            ),
          // 未登录也能进设置，只是没有「退出登录」可按
          IconButton(
            onPressed: () => Get.to(() => const SettingsPage()),
            icon: const Icon(Icons.settings_outlined),
            tooltip: '设置',
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _fetchProfile,
        child: ListView(
          padding: const EdgeInsets.all(10),
          children: [
            _profileCard(),
            const SizedBox(height: 10),
            // 未登录时本地没有任何个人数据，统计卡整块不占位
            // （对齐 Android MessageFirstAdapter.getItemCount() = if (isLogin) 1 else 0）
            if (_isLogin) ...[
              _statsCard(),
              const SizedBox(height: 10),
            ],
            _menuCard(),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  /// 头部用户卡：avatar 60dp + 昵称/等级/经验 + 进度条
  Widget _profileCard() {
    final data = _profile;
    final name = data?.username.isNotEmpty == true
        ? data!.username
        : (GStorage.username.isNotEmpty
            ? Uri.decodeComponent(GStorage.username)
            : '');
    final avatar = data?.userAvatar.isNotEmpty == true
        ? data!.userAvatar
        : GStorage.userAvatar;
    final level = data?.level ?? GStorage.level.toString();
    final exp = data?.experience ?? GStorage.exp;
    final nextExp = data?.nextLevelExperience ?? GStorage.nextExp;

    return _card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () {
          if (!_isLogin) {
            Get.toNamed('/login');
          } else if (GStorage.uid.isNotEmpty) {
            // 头部整块点击进入自己的主页
            Get.toNamed('/u/${GStorage.uid}');
          }
        },
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 16, 16),
          child: Row(
            children: [
              // avatar 60dp 圆形
              SizedBox(
                width: 60,
                height: 60,
                child: avatar.isEmpty
                    ? _avatarPlaceholder()
                    : networkImage(
                        avatar,
                        width: 60,
                        height: 60,
                        isAvatar: true,
                        borderRadius: null,
                      ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (!_isLogin)
                      const Text(
                        '点击登录',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                      )
                    else ...[
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          if (data?.verifyStatus == 1) ...[
                            const SizedBox(width: 4),
                            Icon(
                              Icons.verified,
                              size: 14,
                              color: data?.verifyIcon == 'v_yellow'
                                  ? Colors.amber
                                  : Colors.lightBlue,
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Text('Lv.$level', style: const TextStyle(fontSize: 12)),
                          const SizedBox(width: 8),
                          Text(
                            '$exp/$nextExp',
                            style: TextStyle(
                              fontSize: 10,
                              color: Theme.of(context).colorScheme.outline,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: nextExp > 0 ? (exp / nextExp).clamp(0.0, 1.0) : 0.0,
                          minHeight: 4,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _avatarPlaceholder() {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.25),
        shape: BoxShape.circle,
      ),
      child: Icon(
        Icons.person_outline,
        color: Theme.of(context).colorScheme.outline,
      ),
    );
  }

  /// 统计卡：动态 / 关注 / 粉丝（对齐 Android MessageFirstAdapter）
  Widget _statsCard() {
    final list = [
      ('动态', _profile?.feed ?? '0', FFFListType.FEED),
      ('关注', _profile?.follow ?? '0', FFFListType.FOLLOW),
      ('粉丝', _profile?.fans ?? '0', FFFListType.FAN),
    ];
    return _card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14),
        child: Row(
          children: [
            for (final (title, count, type) in list)
              Expanded(
                child: InkWell(
                  onTap: () => Get.toNamed(
                    '/ffflist',
                    arguments: {'uid': GStorage.uid, 'type': type},
                  ),
                  child: Column(
                    children: [
                      Text(
                        count,
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        title,
                        style: const TextStyle(fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// 功能宫格：8 格 × 4 列（对齐 Android MineMenuAdapter + item_mine_menu.xml）
  ///
  /// 「离线缓存 / 我的装备 / 浏览历史」不需要登录；其余 5 格登录后才可点。
  Widget _menuCard() {
    final items = <_MenuItem>[
      _MenuItem('离线缓存', Icons.archive_outlined,
          requiresLogin: false, onTap: () => Get.toNamed('/offlineCache')),
      _MenuItem('我的关注', Icons.person_add_alt,
          requiresLogin: true,
          onTap: () => _openFFF('我的关注', FFFListType.FOLLOW)),
      _MenuItem('我的收藏', Icons.star_outline,
          requiresLogin: true,
          // 标题用「我的收藏单」—— 对齐 Android `CollectionFragment`
          // 的 `title.ifEmpty { "我的收藏单" }`
          onTap: () => _openFFF('我的收藏单', FFFListType.COLLECTION)),
      _MenuItem('我的赞', Icons.thumb_up_outlined,
          requiresLogin: true,
          onTap: () => _openFFF('我的赞', FFFListType.LIKE)),
      _MenuItem('我的回复', Icons.forum_outlined,
          requiresLogin: true,
          onTap: () => _openFFF('我的回复', FFFListType.REPLY)),
      _MenuItem('我的常去', Icons.place_outlined,
          requiresLogin: true,
          onTap: () => _openFFF('我的常去', FFFListType.RECENT)),
      // 浏览历史走**云端**（Android 2026-09-19 由本地 Room 改为
      // `GET /v6/user/hitHistoryList`；浏览记录由服务端自动写入，客户端无上报接口）
      _MenuItem('浏览历史', Icons.history,
          requiresLogin: false, onTap: () => Get.toNamed('/cloudHistory')),
      _MenuItem('我的装备', Icons.devices_other,
          requiresLogin: false, onTap: _openEquipment),
    ];

    return _card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
        child: GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          // ★ 用**固定格高**而不是 childAspectRatio。
          // item_mine_menu.xml 里每格是 wrap_content：图标 24dp + 文字
          // (marginTop 8dp、12sp) ≈ 48dp。用 aspectRatio 的话格高会随窗口宽度
          // 变化（实测在宽屏下每格被拉到约 100dp，两行之间空一大片），与真机不符。
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 4,
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            mainAxisExtent: 56,
          ),
          itemCount: items.length,
          itemBuilder: (_, i) => _menuCell(items[i]),
        ),
      ),
    );
  }

  Widget _menuCell(_MenuItem item) {
    // 需登录但未登录 → 置灰且点击引导登录（对齐 Android：不放点击监听）
    final enabled = !item.requiresLogin || _isLogin;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: enabled ? item.onTap : () => Get.toNamed('/login'),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            item.icon,
            size: 24,
            color: enabled
                ? Theme.of(context).colorScheme.onSurface
                : Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(height: 8),
          Text(
            item.label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 12,
              color: enabled
                  ? Theme.of(context).colorScheme.onSurface
                  : Theme.of(context).colorScheme.outline,
            ),
          ),
        ],
      ),
    );
  }

  void _openFFF(String title, FFFListType type) {
    Get.toNamed(
      '/ffflist',
      arguments: {'uid': GStorage.uid, 'type': type, 'title': title},
    );
  }

  void _openEquipment() {
    if (!_isLogin) {
      Get.toNamed('/login');
      return;
    }
    Get.toNamed('/webview', parameters: {
      'url': 'https://m.coolapk.com/myDevice/${GStorage.uid}',
      'isLogin': '1',
    });
  }

  void _notImplemented() {
    Get.snackbar('提示', '该功能尚未移植到桌面端');
  }

  /// 卡片外壳：12dp 圆角（Android 端 item_mine_menu / item_message_fff 都是 12dp）
  Widget _card({required Widget child}) {
    return Material(
      clipBehavior: Clip.hardEdge,
      borderRadius: const BorderRadius.all(Radius.circular(12)),
      color: Theme.of(context).colorScheme.surfaceContainer,
      child: child,
    );
  }
}

class _MenuItem {
  const _MenuItem(this.label, this.icon, {required this.requiresLogin, required this.onTap});

  final String label;
  final IconData icon;
  final bool requiresLogin;
  final VoidCallback onTap;
}
