import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../constants/m3_tokens.dart';
import '../pages/app/app_page.dart';
import '../pages/blacklist/black_list_page.dart';
import '../pages/carousel/carousel_page.dart';
import '../pages/chat/chat_page.dart';
import '../pages/coolpic/coolpic_page.dart';
import '../pages/dyh/dyh_page.dart';
import '../pages/feed/feed_page.dart';
import '../pages/ffflist/ffflist_page.dart';
import '../pages/history/history_page.dart';
import '../pages/home/app/update/app_update_page.dart';
import '../pages/login/login_page.dart';
import '../pages/main/main_page.dart';
import '../pages/noitfication/notification_page.dart';
import '../pages/others/copy_page.dart';
import '../pages/others/imageview_page.dart';
import '../pages/search/search_page.dart';
import '../pages/search/search_result_page.dart';
import '../pages/settings/about_page.dart';
import '../pages/settings/params_page.dart';
import '../pages/offline/offline_cache_page.dart';
import '../pages/spam/spam_shield_page.dart';
import '../pages/szlm/szlm_id_web_page.dart';
import '../pages/topic/topic_page.dart';
import '../pages/user/user_page.dart';
import '../pages/webview/webview_page.dart';
import '../utils/m3_transition.dart';

class AppPages {
  /// 统一页面转场 —— 对齐用户 Android app 的二级页转场。
  ///
  /// 用户 2026-10-06 真机实测拍板：
  /// **曲线 M3 standard + 类型水平滑动 + 进入 400ms**（退出规则上应 350ms，
  /// 但 GetX 的 GetPage 不支持单独设反向时长，见 [M3SlideTransition] 注释）。
  ///
  /// 原来这里是 `Transition.native`（各平台系统默认，Windows 上是 Windows
  /// 自己的转场），与 Android 端不一致。
  static GetPage _getPage({
    required String name,
    required Widget Function() page,
  }) {
    return GetPage(
      name: name,
      page: page,
      transition: Transition.rightToLeft,
      transitionDuration: M3Transition.enterDuration,
      curve: M3Easing.standard,
      customTransition: M3SlideTransition(),
    );
  }

  static final List<GetPage> getPages = [
    _getPage(
      name: '/',
      page: () => const MainPage(),
    ),
    _getPage(
      name: '/params',
      page: () => const ParamsPage(),
    ),
    _getPage(
      name: '/about',
      page: () => const AboutPage(),
    ),
    _getPage(
      name: '/copy',
      page: () => const CopyPage(),
    ),
    _getPage(
      name: '/feed/:id',
      page: () => const FeedPage(),
    ),
    _getPage(
      name: '/u/:uid',
      page: () => const UserPage(),
    ),
    _getPage(
      name: '/apk/:packageName',
      page: () => const AppPage(),
    ),
    _getPage(
      name: '/search',
      page: () => const SearchPage(),
    ),
    _getPage(
      name: '/searchResult',
      page: () => const SearchResultPage(),
    ),
    _getPage(
      name: '/t/:tag',
      page: () => const TopicPage(),
    ),
    _getPage(
      name: '/product/:id',
      page: () => const TopicPage(),
    ),
    _getPage(
      name: '/webview',
      page: () => const WebviewPage(),
    ),
    _getPage(
      name: '/appUpdate',
      page: () => const AppUpdatePage(),
    ),
    _getPage(
      name: '/carousel',
      page: () => const CarouselPage(),
    ),
    _getPage(
      name: '/dyh',
      page: () => const DyhPage(),
    ),
    _getPage(
      name: '/coolpic',
      page: () => const CoolpicPage(),
    ),
    _getPage(
      name: '/imageview',
      page: () => const ImageViewPage(),
    ),
    _getPage(
      name: '/login',
      page: () => const LoginPage(),
    ),
    _getPage(
      name: '/notification',
      page: () => const NotificationPage(),
    ),
    _getPage(
      name: '/ffflist',
      page: () => const FFFListPage(),
    ),
    _getPage(
      name: '/history',
      page: () => const HistoryPage(),
    ),
    _getPage(
      name: '/blacklist',
      page: () => const BlackListPage(),
    ),
    _getPage(
      name: '/chat',
      page: () => const ChatPage(),
    ),
    // 「获取数字联盟 ID」：内置 WebView 换取 DUID
    // （Android 是 SzlmIdWebActivity，横屏下也是独立页）
    _getPage(
      name: '/szlmId',
      page: () => const SzlmIdWebPage(),
    ),
    // 其他屏蔽项（关键字 / 用户 / 节点）。
    // Android 是三个设置入口（spamWord / spamUser / spamNode）各起一个
    // SpamShieldActivity，靠 intent 里的 `type` 区分；这里同样用一个页面按 type 切。
    _getPage(
      name: '/spamShield/:type',
      page: () => SpamShieldPage(type: Get.parameters['type'] ?? 'word'),
    ),
    // 离线缓存列表（Android `OfflineCacheActivity`）
    _getPage(
      name: '/offlineCache',
      page: () => const OfflineCachePage(),
    ),
  ];
}
