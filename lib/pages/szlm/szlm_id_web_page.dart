import 'dart:collection';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';

import '../../utils/szlm_id_params.dart';
import '../../utils/webview_env.dart';
import '../../utils/storage_util.dart';

/// 「获取数字联盟 ID」页 —— 内置 WebView 打开自建页面换取 DUID。
///
/// 对齐 Android `ui/others/SzlmIdWebActivity.kt`。
///
/// ## 为什么是网页而不是直连接口
/// 直连接口无法要求人机验证，取号端点一直被刷。改走网页后，签发要同时满足
/// ①App 签名是官方包 ②Cloudflare Turnstile 人机验证通过。
///
/// ## 参数怎么过去（与 Android 的差异，重要）
/// Android 用 `addJavascriptInterface(WebBridge(), "C001Bridge")` 暴露一个**对象**，
/// 网页调 `C001Bridge.getParams()` / `C001Bridge.onSuccess(duid)`。
/// 而 `flutter_inappwebview` 的通道是 `window.flutter_inappwebview.callHandler(...)`，
/// **调用约定不同**。所以在文档开始时注入一层 JS shim，把 `window.C001Bridge`
/// 按网页期望的形状补齐，内部转调 Flutter 侧。
///
/// `getParams()` 必须**同步**返回（Android 那边也是靠预生成 JSON + 同步返回），
/// 因此参数 JSON 直接内联进 shim。
class SzlmIdWebPage extends StatefulWidget {
  const SzlmIdWebPage({super.key});

  @override
  State<SzlmIdWebPage> createState() => _SzlmIdWebPageState();
}

class _SzlmIdWebPageState extends State<SzlmIdWebPage> {
  double _progress = 0;

  @override
  Widget build(BuildContext context) {
    // 预生成参数 JSON（JsBridge 回调时要同步返回）
    final paramsJson = SzlmIdParams.buildForWeb();

    // JS shim：把 window.C001Bridge 按 webgetv1/index.html 期望的形状补上。
    // getParams 内联返回 JSON —— jsonEncode 一下是为了让它成为合法的 JS 字符串字面量。
    final shim = '''
window.C001Bridge = {
  getParams: function () { return ${_jsString(paramsJson)}; },
  onSuccess: function (duid) {
    window.flutter_inappwebview.callHandler('szlmOnSuccess', duid);
  }
};
''';

    return Scaffold(
      appBar: AppBar(
        title: const Text('获取数字联盟 ID'),
        bottom: _progress > 0 && _progress < 1
            ? PreferredSize(
                preferredSize: const Size.fromHeight(2),
                child: LinearProgressIndicator(value: _progress, minHeight: 2),
              )
            : null,
      ),
      body: InAppWebView(
        // ★★ Windows 上**必须**传同一个 WebViewEnvironment（2026-10-18 修）
        //
        // 症状：一进「获取数字联盟 ID」页面就**打不开**（空白/失败）✗
        // 原因：main() 里已经 `WebViewEnv.init()` 建了全局环境 ✓
        //       但这里创建 WebView 时**没传**它 ✗
        //       ⇒ flutter_inappwebview 的 Windows 实现建不出 WebView ✗
        // 对照：`components/h5_tab.dart` 和 `pages/webview/webview_page.dart`
        //       都是传了的 ✓ 只有这个页面漏了 ✗
        webViewEnvironment: WebViewEnv.instance,
        initialSettings: InAppWebViewSettings(
          javaScriptEnabled: true,
          domStorageEnabled: true,
          // Turnstile 的 iframe 在 challenges.cloudflare.com 上，必须允许第三方 cookie
          thirdPartyCookiesEnabled: true,
          // 页面本体、JsBridge、Turnstile 都依赖 JS；与 Android 一样不缓存
          clearCache: true,
          // ★ 不用给酷安接口伪装的那个 UA —— Turnstile 要真实浏览器 UA。
          //   这里用带 `wv` 标记 + C001ApkWebView 标记的合成 UA，
          //   服务端据此确认请求来自 App 内置 WebView。
          userAgent: SzlmIdParams.webUserAgent(),
          useShouldOverrideUrlLoading: false,
        ),
        initialUserScripts: UnmodifiableListView<UserScript>([
          UserScript(
            source: shim,
            injectionTime: UserScriptInjectionTime.AT_DOCUMENT_START,
          ),
        ]),
        initialUrlRequest: URLRequest(url: WebUri(SzlmIdParams.WEB_ENTRY)),
        onWebViewCreated: (InAppWebViewController controller) {
          // 网页签发成功后回调：落盘并关页
          controller.addJavaScriptHandler(
            handlerName: 'szlmOnSuccess',
            callback: (args) async {
              final duid = (args.isNotEmpty ? args.first : '').toString().trim();
              if (duid.isEmpty) return null;
              // ★ 打一行日志：用户报「取号没反应」时能直接从日志判断有没有回调 ✓
              debugPrint('szlm: 取号成功 len=${duid.length}');
              await GStorage.saveSzlmId(duid);
              if (mounted) {
                Get.back(result: duid);
                Get.snackbar('获取成功', '数字联盟 ID：$duid');
              }
              return null;
            },
          );
        },
        onProgressChanged: (controller, progress) {
          if (mounted) setState(() => _progress = progress / 100);
        },
      ),
    );
  }

  /// 把一个字符串编码成合法的 JS 字符串字面量（含引号与转义）
  static String _jsString(String s) {
    final buf = StringBuffer('"');
    for (final code in s.codeUnits) {
      switch (code) {
        case 0x22:
          buf.write(r'\"');
          break;
        case 0x5C:
          buf.write(r'\\');
          break;
        case 0x0A:
          buf.write(r'\n');
          break;
        case 0x0D:
          buf.write(r'\r');
          break;
        case 0x09:
          buf.write(r'\t');
          break;
        default:
          if (code < 0x20) {
            buf.write('\\u${code.toRadixString(16).padLeft(4, '0')}');
          } else {
            buf.writeCharCode(code);
          }
      }
    }
    buf.write('"');
    return buf.toString();
  }
}
