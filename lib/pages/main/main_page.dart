import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import '../../pages/home/return_top_controller.dart';
import '../../pages/home/home_page.dart';
import '../../pages/main/main_controller.dart';
import '../../pages/mine/mine_page.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';

class MainPage extends StatefulWidget {
  const MainPage({super.key});

  @override
  State<MainPage> createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  final ReturnTopController _pageScrollController =
      Get.put(ReturnTopController(), tag: 'home');
  int _selectedIndex = 0;
  final _indexSctream = StreamController<int>.broadcast();
  late final MainController _mainController = Get.put(MainController());
  final _contrller = PageController();

  @override
  void initState() {
    super.initState();
    // 先跟随官方最新版本号，再校验登录态（顺序见 MainController.initAndCheckLogin）
    _mainController.initAndCheckLogin();
  }

  @override
  void dispose() async {
    await GStorage.close();
    Get.delete<ReturnTopController>(tag: 'home');
    Get.delete<MainController>();
    super.dispose();
  }

  void onBackPressed() async {
    if (_selectedIndex != 0) {
      onDestinationSelected(0);
    } else {
      SystemNavigator.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    // 只有两项，对齐 Android `res/menu/nav_menu.xml`：
    //   navigation_home（首页） / navigation_mine（我的）
    // 原先的「消息」「设置」两项已按 Android 横屏的信息架构挪走：
    //   消息 → 首页顶栏右上角的消息图标（带未读角标）
    //   设置 → 「我的」页顶栏的齿轮图标
    const pages = [
      HomePage(),
      MinePage(),
    ];

    const barDestinations = <NavigationDestination>[
      NavigationDestination(
        selectedIcon: Icon(Icons.home),
        icon: Icon(Icons.home_outlined),
        label: '首页',
      ),
      NavigationDestination(
        selectedIcon: Icon(Icons.person),
        icon: Icon(Icons.person_outline),
        label: '我的',
      ),
    ];

    const railDestinations = <NavigationRailDestination>[
      NavigationRailDestination(
        selectedIcon: Icon(Icons.home),
        icon: Icon(Icons.home_outlined),
        label: Text('首页'),
      ),
      NavigationRailDestination(
        selectedIcon: Icon(Icons.person),
        icon: Icon(Icons.person_outline),
        label: Text('我的'),
      ),
    ];

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (_, obj) async {
        onBackPressed();
      },
      child: LayoutBuilder(
        builder: (context, _) {
          return Scaffold(
            body: Row(children: [
              if (!Utils.isPortrait(context))
                StreamBuilder(
                    initialData: _selectedIndex,
                    stream: _indexSctream.stream,
                    builder: (_, snapshot) => Padding(
                          padding: EdgeInsets.only(
                              top: Platform.isMacOS ? 25.0 : 10.0),
                          child: NavigationRail(
                            destinations: railDestinations,
                            selectedIndex: snapshot.data!,
                            onDestinationSelected: onDestinationSelected,
                            // 对齐 Android 横屏的 NavigationRailView：
                            //   labelVisibilityMode="selected" → 仅选中项显示文字
                            //   （见 activity_main.xml / MainActivity 的横屏分支）
                            // 实测导航栏宽 96dp（= 20 + 56 + 20）。
                            // Flutter 的 NavigationRail 有内部最小宽度，压不到 96dp，
                            // 用 extended:false 拿到最接近的形态。
                            labelType: NavigationRailLabelType.selected,
                            extended: false,
                          ),
                        )),
              if (!Utils.isPortrait(context)) const VerticalDivider(width: 1),
              // ★ 去掉宽屏时两侧的 Spacer（原来 flex 8 + 两个 flex 1 的 Spacer
              //   会把内容压到 80% 宽并居中）。Android 横屏是
              //   `ViewPager#viewPager` 紧贴导航栏铺满 x=96..853（实测），
              //   内容区不做居中收窄，所以这里让 PageView 直接占满剩余宽度。
              Expanded(
                child: PageView(
                  physics: const NeverScrollableScrollPhysics(),
                  controller: _contrller,
                  children: pages,
                ),
              ),
            ]),
            bottomNavigationBar: Utils.isPortrait(context)
                ? StreamBuilder(
                    initialData: _selectedIndex,
                    stream: _indexSctream.stream,
                    builder: (_, snapshot) => NavigationBar(
                      destinations: barDestinations,
                      selectedIndex: snapshot.data!,
                      onDestinationSelected: onDestinationSelected,
                      labelBehavior:
                          NavigationDestinationLabelBehavior.onlyShowSelected,
                    ),
                  )
                : null,
          );
        },
      ),
    );
  }

  void onDestinationSelected(int index) {
    if (index == 0 && _selectedIndex == 0) {
      _pageScrollController.setIndex(998);
    }
    _contrller.animateToPage(
      index,
      duration: const Duration(milliseconds: 300),
      curve: Curves.ease,
    );
    _selectedIndex = index;
    _indexSctream.add(index);
  }
}
