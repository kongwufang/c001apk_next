// ignore_for_file: constant_identifier_names

import 'package:flutter/material.dart';

class Constants {
  static const APP_NAME = 'c001apk-flutter';

  static const REQUEST_WITH = 'XMLHttpRequest';
  static const LOCALE = 'zh-CN';
  static const APP_ID = 'com.coolapk.market';
  static const DARK_MODE = '0';
  static const CHANNEL = 'coolapk';
  static const MODE = 'universal';
  static const APP_LABEL =
      'token://com.coolapk.market/dcf01e569c1e3db93a3d0fcf191a622c';

  // ── 客户端版本（服务端会下发官方最新版，这里只是兜底默认值）──
  //
  // ★ 上游 Flutter 工程写的是 13.4.1 / 2312121 / API 13（2024 年），
  //   已严重落后 —— v3 token 与 X-App-Code 必须自洽，版本太旧服务端会拒。
  //   这里改成 `_rev/win_ref/coolapk_protocol.py`（**实测跑通 11/11 登录态接口**）
  //   所用的这一组；运行时按 Android 端 `MainViewModel.fetchAppInfo()` 的做法，
  //   用 `/v6/apk/detail?id=com.coolapk.market` 取官方最新版本号覆盖。
  static const VERSION_NAME = '16.6.4';
  static const VERSION_CODE = '2609291';
  static const API_VERSION = '16';

  // ── 默认设备指纹（与 DEFAULT_DEVICE_CODE 配套，缺一不可）──
  // 对应 Android `Constants.DEFAULT_*`，UA 由这几项拼出。
  static const DEFAULT_MANUFACTURER = 'OnePlus';
  static const DEFAULT_BRAND = 'OnePlus';
  static const DEFAULT_MODEL = 'PJZ110';
  static const DEFAULT_BUILDNUMBER = 'PJZ110_16.0.5.703(CN01)';
  static const DEFAULT_ANDROID_VERSION = '16';
  static const SDK_INT = '36';

  /// 默认 `X-App-Device` 设备串。
  ///
  /// 结构与 `_rev/win_ref/README.md` §2.2 一致（分号分隔 9 段）：
  /// `szlmId; ; ; ; 厂商; 品牌; 型号; 版本号; 尾部hex`
  ///
  /// ⚠️ 首字段 szlmId 必须是**服务端已注册**的值（随机假值会拿到
  /// `-419 检测到当前版本存在异常…请使用官方APP访问`）。
  /// Android 端从 `PrefManager.SZLMID` 注入，桌面端沿用这份已验证可用的设备串。
  static const DEFAULT_DEVICE_CODE =
      'lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0QxYjNEJzM4Ay'
      'OpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbPByOgsDI7AyOidGRoJ0ZiZk'
      'YrFWbmlkWLF2dJZ3QZhlQidXT6Fjd0NFOsVFR';


  static const PREFIX_COOLMARKET = 'coolmarket://';
  static const PREFIX_HTTP = 'http';
  static const PREFIX_APP = '/apk/';
  static const PREFIX_GAME = '/game/';
  static const PREFIX_FEED = '/feed/';
  static const PREFIX_PRODUCT = '/product/';
  static const PREFIX_TOPIC = '/t/';
  static const PREFIX_USER = '/u/';
  static const PREFIX_CAROUSEL = '/page?url=';
  static const PREFIX_CAROUSEL1 = '#/';
  static const PREFIX_USER_LIST = '/user/';
  static const PREFIX_DYH = '/dyh/';
  static const PREFIX_COLLECTION = '/collection/';

  /// 众测 / 活动详情：/event/<id>（走原生页，不是 H5）
  static const PREFIX_EVENT = '/event/';
  static const SUFFIX_THUMBNAIL = '.s.jpg';
  static const SUFFIX_GIF = '.gif';

  static const UTF8 = 'UTF-8';
  static const EMPTY_STRING = '';
  static const LOADING_FAILED = 'FAILED';
  static const URL_COOLAPK = 'https://www.coolapk.com/';
  static const URL_LOGIN = 'https://account.coolapk.com/auth/login?type=mobile';
  static const URL_SOURCE_CODE =
      'https://github.com/RzdPai/c001apk';
  static const URL_API_SERVICE = 'https://api.coolapk.com';
  static const URL_API2_SERVICE = 'https://api2.coolapk.com';
  static const URL_ACCOUNT_SERVICE = 'https://account.coolapk.com';

  static const entityTypeList = [
    'feed',
    'apk',
    'product',
    'user',
    'topic',
    'notification',
    'productBrand',
    'contacts',
    'recentHistory',
    'feed_reply',
    'message',
    'collection',
    // ★ 活动页「线下」tab 的实体商品卡。
    //
    // Android `CarouselViewModel` 的白名单里明确有它：
    // ```kotlin
    // if (it.entityType in listOf("feed","topic","product","user","card","pear_goods"))
    // ```
    // 不加这一条，线下 tab 会整页「没有更多了」（实测确认过）。
    'pear_goods',
  ];

  static const entityTemplateList = [
    'imageCarouselCard_1',
    'iconLinkGridCard',
    'iconMiniScrollCard',
    'iconMiniGridCard',
    'imageSquareScrollCard',
    'titleCard',
    'iconScrollCard',
    'imageTextScrollCard',
    'iconTabLinkGridCard',
    'verticalColumnsFullPageCard',
    'noMoreDataCard',
    'time',
    'messageCard',
    // 'configCard',
    // 'refreshCard',
  ];

  static Map<int, Color> getSwatch(int r, int g, int b) {
    return {
      50: Color.fromRGBO(r, g, b, .1),
      100: Color.fromRGBO(r, g, b, .2),
      200: Color.fromRGBO(r, g, b, .3),
      300: Color.fromRGBO(r, g, b, .4),
      400: Color.fromRGBO(r, g, b, .5),
      500: Color.fromRGBO(r, g, b, .6),
      600: Color.fromRGBO(r, g, b, .7),
      700: Color.fromRGBO(r, g, b, .8),
      800: Color.fromRGBO(r, g, b, .9),
      900: Color.fromRGBO(r, g, b, 1),
    };
  }

  /// 20 套配色的种子色。
  ///
  /// ⚠️ **顺序与内容必须与 Android 端逐项一致**，因为 `staticColor` 是这张表的下标。
  /// 真源是 Android `app/build.gradle.kts` 的 `materialThemeBuilder { themes { ... } }`
  /// 配置（调色板由 `rikka.tools.materialthemebuilder` 在构建期生成），
  /// UI 顺序则来自 `res/values/arrays.xml` 的 `color_values` / `color_texts`。
  ///
  /// 实测核对结论（这份表原来有 19 项）：
  /// - **缺 `DeepPurple` `#673AB7`** —— 已补
  /// - **`樱花` 原本在最后，Android 里排第 2** —— 已按 Android 调序
  /// - 文案 `酸橙` → Android 是 **`黄绿`**；`蓝格` → Android 是 **`灰蓝`**
  /// - 色值原本就是对的：`绿色 #4FAF50` / `浅绿 #8BC3A4` / `灰蓝 #607D8F`
  ///   看起来像 Material 官方色（#4CAF50 / #8BC34A / #607D8B）的笔误，
  ///   但**确实是 app 自己定的值**，不是笔误 —— 已逐项与 build.gradle.kts 核对
  static final List<MaterialColor> seedColors = [
    MaterialColor(0xFF6750A4, getSwatch(103, 80, 164)), // 默认
    MaterialColor(0xFFFF9CA8, getSwatch(255, 156, 168)), // 樱花
    MaterialColor(0xFFF44336, getSwatch(244, 67, 54)), // 红色
    MaterialColor(0xFFE91E63, getSwatch(233, 30, 99)), // 粉色
    MaterialColor(0xFF9C27B0, getSwatch(156, 39, 176)), // 紫色
    MaterialColor(0xFF673AB7, getSwatch(103, 58, 183)), // 深紫
    MaterialColor(0xFF3F51B5, getSwatch(63, 81, 181)), // 靛青
    MaterialColor(0xFF2196F3, getSwatch(33, 150, 243)), // 蓝色
    MaterialColor(0xFF03A9F4, getSwatch(3, 169, 244)), // 浅蓝
    MaterialColor(0xFF00BCD4, getSwatch(0, 188, 212)), // 青色
    MaterialColor(0xFF009688, getSwatch(0, 150, 136)), // 青绿
    MaterialColor(0xFF4FAF50, getSwatch(79, 175, 80)), // 绿色
    MaterialColor(0xFF8BC3A4, getSwatch(139, 195, 164)), // 浅绿
    MaterialColor(0xFFCDDC39, getSwatch(205, 220, 57)), // 黄绿
    MaterialColor(0xFFFFEB3B, getSwatch(255, 235, 59)), // 黄色
    MaterialColor(0xFFFFC107, getSwatch(255, 193, 7)), // 琥珀
    MaterialColor(0xFFFF9800, getSwatch(255, 152, 0)), // 橙色
    MaterialColor(0xFFFF5722, getSwatch(255, 87, 34)), // 深橙
    MaterialColor(0xFF795548, getSwatch(121, 85, 72)), // 棕色
    MaterialColor(0xFF607D8F, getSwatch(96, 125, 143)), // 灰蓝
  ];

  /// 配色显示名（顺序与 [seedColors] 一一对应，也与 Android `color_texts` 一致）
  static const List<String> themeType = [
    '默认',
    '樱花',
    '红色',
    '粉色',
    '紫色',
    '深紫',
    '靛青',
    '蓝色',
    '浅蓝',
    '青色',
    '青绿',
    '绿色',
    '浅绿',
    '黄绿',
    '黄色',
    '琥珀',
    '橙色',
    '深橙',
    '棕色',
    '灰蓝',
  ];

  /// 旧配色顺序 → 新顺序的下标映射。
  ///
  /// 上游 Flutter 的表是 19 项（缺深紫、樱花在末位），而 `staticColor` 存的是**下标**。
  /// 直接换表会让老用户已选的配色**变成另一个颜色**，所以按「颜色身份」重映射。
  ///
  /// 下标含义：`old[旧下标] = 新下标`
  static const List<int> colorIndexRemap = [
    0, // 默认   → 默认
    2, // 红色   → 红色
    3, // 粉色   → 粉色
    4, // 紫色   → 紫色
    6, // 靛青   → 靛青
    7, // 蓝色   → 蓝色
    8, // 浅蓝   → 浅蓝
    9, // 青色   → 青色
    10, // 青绿  → 蓝绿
    11, // 绿色  → 绿色
    12, // 浅绿  → 浅绿
    13, // 酸橙  → 黄绿
    14, // 黄色  → 黄色
    15, // 琥珀  → 琥珀
    16, // 橙色  → 橙色
    17, // 深橙  → 深橙
    18, // 棕色  → 棕色
    19, // 蓝格  → 灰蓝
    1, // 樱花   → 樱花
  ];
}
