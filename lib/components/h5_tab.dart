import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../constants/constants.dart';
import '../utils/storage_util.dart';
import '../utils/webview_env.dart';
import 'm3e_progress.dart';

/// 话题 / 产品页里**H5 类型的 tab** 的内联 WebView。
///
/// 对应 Android `TopicFragment.getFragment` 的这一段：
/// ```kotlin
/// // H5 类型的 tab（如「活动详情」tab 的 url = https://m.coolapk.com/activity/<name>）
/// // 用 WebView 渲染，否则走 dataList 接口会返回空导致页面空白
/// if (url.startsWith("http://") || url.startsWith("https://")) {
///     return WebViewFragment.newInstance(url)
/// }
/// ```
///
/// ## 为什么必须分流
/// 这些 tab 的 `url` 是**完整的 H5 地址**，不是 `/page?url=…` 那种数据页。
/// 拿它去调 `/v6/page/dataList` 只会得到空列表 ⇒ **整页空白**。
/// release-notes 里那条「专题页「说明」与 H5 活动页补全渲染，**修复空白**」说的就是它。
///
/// 服务端下发的 `tabList[].url` 形如：
/// - `https://m.coolapk.com/activity/<name>` —— 活动 H5 落地页
/// - 专题页的「说明」tab
///
/// ## 与 `WebviewPage` 的区别
/// `WebviewPage` 是一个**独立路由页**（带自己的 AppBar / 菜单 / 进度条）。
/// 这里要的是**嵌在 TabBarView 里**的裸 WebView，所以单独做一个轻量组件，
/// 但**沿用同一套环境与设置**（尤其 Windows 上必须传 `webViewEnvironment`）。
class H5Tab extends StatefulWidget {
  const H5Tab({super.key, required this.url});

  final String url;

  @override
  State<H5Tab> createState() => _H5TabState();
}

class _H5TabState extends State<H5Tab> {
  bool _loading = true;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        InAppWebView(
          // Windows 必须把同一个 `WebViewEnvironment` 也传给 WebView 本体，
          // 否则 `CookieManager` 拿到的环境跟它不一致，取不到 cookie。
          webViewEnvironment: WebViewEnv.instance,
          initialSettings: InAppWebViewSettings(
            useHybridComposition: false,
            mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
            useShouldOverrideUrlLoading: true,
            useOnDownloadStart: true,
            // 与 `WebviewPage` 一致：站内 WebView 打开 coolapk.com 时保持登录态，
            // UA 用酷安 App 自己的（`GStorage.userAgent`）
            userAgent: GStorage.userAgent,
            forceDark: ForceDark.AUTO,
            algorithmicDarkeningAllowed: true,
          ),
          initialUrlRequest: URLRequest(
            url: WebUri.uri(Uri.parse(widget.url)),
            headers: {'X-Requested-With': Constants.APP_ID},
          ),
          onLoadStart: (_, __) {
            if (mounted) setState(() => _loading = true);
          },
          onLoadStop: (_, __) {
            if (mounted) setState(() => _loading = false);
          },
          onReceivedError: (_, __, ___) {
            if (mounted) setState(() => _loading = false);
          },
        ),
        if (_loading)
          const Center(child: M3EWavyProgressIndicator()),
      ],
    );
  }
}
