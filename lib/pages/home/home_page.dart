import 'dart:io';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/model/check_count/check_count.dart';
import '../../logic/network/network_repo.dart';
import '../../pages/home/app/app_list_page.dart';
import '../../pages/home/feed/home_feed_page.dart';
import '../../pages/home/return_top_controller.dart';
import '../../pages/home/topic/home_topic_page.dart';
import '../../pages/message/message_page.dart';
import '../../utils/storage_util.dart';

/// 首页 tab。
///
/// ★ [name]（枚举名）被当作 GetX 的 tag 用（见 `home_feed_page.dart` /
/// `home_topic_page.dart` 里的 `tag: widget.tabType.name`），**不能改枚举名**；
/// 界面上显示的中文走 [label]。
///
/// 文案与顺序对齐 Android 端 `HomeViewModel.defaultList`：
/// 关注 / 应用 / 头条 / 热榜 / 话题 / 数码 / 酷图
// ignore: constant_identifier_names
enum TabType {
  FOLLOW('关注'),
  APP('应用'),
  FEED('头条'),
  HOT('热榜'),
  TOPIC('话题'),
  PRODUCT('数码'),
  COOLPIC('酷图'),
  NONE('');

  const TabType(this.label);

  /// 界面显示用的中文名
  final String label;
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with TickerProviderStateMixin {
  late TabController _tabController;
  final ReturnTopController _pageScrollController =
      Get.find<ReturnTopController>(tag: 'home');
  // late final _config = Provider.of<AppConfigProvider>(context, listen: false);
  // late bool _showFab = true; //_config.isLogin;

  /// 首页 tab 的**顺序与启用状态**现在由设置决定
  /// （`设置 - 外观 - 布局 - 编辑Tab`，对齐 Android `HomeMenu` 表）。
  late final List<TabType> _tabs = _buildTabs();

  /// 按 `GStorage.homeMenu` 的顺序与启用状态构建。
  ///
  /// 对齐 Android `HomeViewModel.defaultList`：
  /// 默认 7 项「关注 / 应用 / 头条 / 热榜 / 话题 / 数码 / 酷图」。
  static List<TabType> _buildTabs() {
    final list = <TabType>[];
    for (final name in GStorage.homeMenu) {
      final type = TabType.values.firstWhere(
        (e) => e.name == name,
        orElse: () => TabType.NONE,
      );
      if (type == TabType.NONE) continue;
      // 「应用」只有 Android 有（桌面端没有 AppListPage）——
      // 与原来 `if (Platform.isAndroid)` 的规则一致
      if (type == TabType.APP && !Platform.isAndroid) continue;
      if (!list.contains(type)) list.add(type);
    }
    // 兜底：存储被清空 / 损坏 / 全被禁用时至少给一个 tab，
    // 否则 `TabController(length: 0)` 会直接崩。
    if (list.isEmpty) list.add(TabType.FEED);
    return list;
  }

  /// 每个 tab 对应的页面
  static Widget _pageOf(TabType type) => switch (type) {
        TabType.FOLLOW => const HomeFeedPage(tabType: TabType.FOLLOW),
        TabType.APP => const AppListPage(),
        TabType.FEED => const HomeFeedPage(tabType: TabType.FEED),
        TabType.HOT => const HomeFeedPage(tabType: TabType.HOT),
        TabType.TOPIC => const HomeTopicPage(tabType: TabType.TOPIC),
        TabType.PRODUCT => const HomeTopicPage(tabType: TabType.PRODUCT),
        TabType.COOLPIC => const HomeFeedPage(tabType: TabType.COOLPIC),
        TabType.NONE => const SizedBox.shrink(),
      };

  late final List<Widget> _pages = _tabs.map(_pageOf).toList();

  late final List<Tab> _tabList =
      _tabs.map((type) => Tab(text: type.label)).toList();

  /// 消息未读数（顶栏消息图标右上角红点）
  /// 对齐 Android 端 `HomeFragment`：口径与消息中心宫格同源（UnreadCounter），
  /// `count > 0` 才显示，`> 99` 显示 `99+`（Badge.count 自带这个行为）。
  int _unreadCount = 0;

  Future<void> _fetchUnreadCount() async {
    try {
      final response = await NetworkRepo.checkCount();
      if (response.statusCode == HttpStatus.ok && response.data != null) {
        final count = CheckCount.fromJson(
          Map<String, dynamic>.from(response.data as Map),
        );
        if (mounted) {
          setState(() => _unreadCount = count.data?.badge ?? 0);
        }
      }
    } catch (e) {
      debugPrint('checkCount failed: $e');
    }
  }

  void scrollToTop(int index) {
    // ★ 直接按 tab 下标找页面下标 —— 原来这里是
    //   `Platform.isAndroid ? index : (index == 0 ? 0 : index + 1)`，
    //   那是「桌面端少一个『应用』页、下标要 +1」的硬编码补偿。
    //   现在 tab 顺序/启用都由用户配置，页数不再固定，改成按 `_tabs` 算：
    //   tab 与页面是**一一对应**的（`_pages = _tabs.map(_pageOf)`），所以下标相同。
    if (index >= 0 && index < _tabs.length) {
      _pageScrollController.setIndex(index);
    }
  }

  @override
  void initState() {
    super.initState();

    // 默认落在「头条」（对齐 Android：进首页默认头条）。
    // 用户可能把它禁用了，那就退回第一个。
    final feedIndex = _tabs.indexOf(TabType.FEED);

    _tabController = TabController(
      vsync: this,
      initialIndex: feedIndex >= 0 ? feedIndex : 0,
      length: _tabs.length,
    );

    _pageScrollController.index.listen((index) {
      if (index == 998) {
        scrollToTop(_tabController.index);
      }
    });

    _fetchUnreadCount();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: TabBar(
          controller: _tabController,
          tabs: _tabList,
          isScrollable: true,
          onTap: (index) {
            if (!_tabController.indexIsChanging) {
              scrollToTop(index);
            }
          },
          // Android 端是 `app:tabMode="auto"` + `tabGravity="fill"`，
          // 实测 7 个 tab 在 853dp 横屏下每个 56dp 且**左对齐**（首 tab x=106dp
          // = 导航栏 96dp + 10dp），不是居中。所以统一用 startOffset。
          tabAlignment: TabAlignment.startOffset,
        ),
        // 顺序对齐 Android `fragment_home.xml`：
        //   TabLayout → ImageView#search（marginStart/End 10dp）→ FrameLayout#message（最右）
        actions: [
          IconButton(
            onPressed: () => Get.toNamed('/search'),
            icon: const Icon(Icons.search),
            tooltip: '搜索',
          ),
          // 消息入口 + 未读角标（对应 Android 的 FrameLayout#message + messageBadge）
          IconButton(
            onPressed: () => Get.toNamed('/message'),
            icon: Badge.count(
              count: _unreadCount,
              isLabelVisible: _unreadCount > 0,
              child: const Icon(Icons.message_outlined),
            ),
            tooltip: '消息中心',
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: TabBarView(controller: _tabController, children: _pages),
    );
  }
}
