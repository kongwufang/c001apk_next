import 'dart:io';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';

/// 全局 `WebViewEnvironment`（**仅 Windows 需要**）。
///
/// ## ★ 为什么需要它
/// `flutter_inappwebview` 在 Windows 上有个**已知缺陷**：
/// `CookieManager` 取不到 cookie（返回空列表），因为它的实现**没有关联到
/// 当前 WebView 实例**，取不到「带分区键的 cookie」。
///
/// 作者在 [issue #2314](https://github.com/pichillilorenzo/flutter_inappwebview/issues/2314)
/// 里给了修法（`6.2.0-beta.2` 起）：
/// > it seems the problem is related to cookies with partition key.
/// > The actual CookieManager implementation doesn't use the current WebView
/// > to get the cookies
///
/// 具体要**两件事一起做**：
/// 1. `WebViewEnvironment.create(...)` 建一个环境，
///    并把它**同时**传给 `InAppWebView` 与 `CookieManager.instance(...)`
/// 2. 取 cookie 时**再传 `webViewController:`**
///
/// 只做其中一件都不行 —— 所以这里把 environment 存成全局单例，
/// 让 `webview_page` 的 8 处 `CookieManager` 调用都能拿到。
///
/// 这直接决定了**网页登录能不能成**：取不到 cookie 就永远检测不到登录态
/// （实测：`settings.hive` 里 uid/username/token 全是空串、isLogin 恒为 false）。
class WebViewEnv {
  const WebViewEnv._();

  static WebViewEnvironment? _instance;

  /// 传给 `InAppWebView(webViewEnvironment: ...)`。
  /// 非 Windows 平台恒为 null（那时传 null 就是对的）。
  static WebViewEnvironment? get instance => _instance;

  /// 在 `main()` 里、`runApp` 之前调用。
  ///
  /// 非 Windows 平台直接返回 —— 只有 Windows 需要显式建环境。
  static Future<void> init() async {
    if (!Platform.isWindows) return;
    try {
      // 没有 WebView2 运行时会返回 null；此时不建环境，
      // 让 WebView 用默认的（cookie 取不到，但页面还能显示）
      final available = await WebViewEnvironment.getAvailableVersion();
      if (available == null) return;
      _instance = await WebViewEnvironment.create(
        settings: WebViewEnvironmentSettings(
          // 固定用户数据目录，保证登录态在重启后还在
          // （否则每次可能落到不同的临时目录）
          userDataFolder: 'c001apk_webview',
        ),
      );
    } catch (_) {
      // 建环境失败不该让整个 App 起不来
      _instance = null;
    }
  }
}
