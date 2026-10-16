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

  final _tabList = TabType.values.map((type) => Tab(text: type.label)).toList();

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

  final _pages = [
    const HomeFeedPage(tabType: TabType.FOLLOW),
    if (Platform.isAndroid) const AppListPage(),
    const HomeFeedPage(tabType: TabType.FEED),
    const HomeFeedPage(tabType: TabType.HOT),
    const HomeTopicPage(tabType: TabType.TOPIC),
    const HomeTopicPage(tabType: TabType.PRODUCT),
    const HomeFeedPage(tabType: TabType.COOLPIC),
  ];

  void scrollToTop(int index) {
    _pageScrollController.setIndex(Platform.isAndroid
        ? index
        : index == 0
            ? 0
            : index + 1);
  }

  @override
  void initState() {
    super.initState();

    _tabList.removeLast();
    if (!Platform.isAndroid) {
      _tabList.removeAt(1);
    }

    _tabController = TabController(
      vsync: this,
      initialIndex: Platform.isAndroid ? 2 : 1,
      length: _tabList.length,
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
            onPressed: () => Get.to(() => const MessagePage()),
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
