import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../constants/constants.dart';
import '../../utils/cache_util.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';
import '../../utils/webview_env.dart';

// ignore: constant_identifier_names
enum WebviewMenuItem {
  Refresh('刷新'),              // refresh
  Copy('复制'),                 // copyText
  Open_In_Browser('在浏览器打开'),
  Clear_Cache('清除缓存'),
  Go_Back('返回');

  const WebviewMenuItem(this.label);
  final String label;
}

class WebviewPage extends StatefulWidget {
  const WebviewPage({super.key});

  @override
  State<WebviewPage> createState() => _WebviewPageState();
}

class _WebviewPageState extends State<WebviewPage> {
  final String _url = Get.parameters['url'] ?? '';
  final bool _isLogin = Get.parameters['isLogin'] == '1';
  final _titleStream = StreamController<String?>();
  final _progressStream = StreamController<double>();

  InAppWebViewController? _webViewController;

  /// 登录成功只处理一次
  bool _loginHandled = false;
  Timer? _cookieTimer;

  /// 已轮询的秒数（最多等 [_loginTimeoutSeconds]）
  int _elapsed = 0;

  static const int _loginTimeoutSeconds = 600;
  static const Duration _cookieCheckInterval = Duration(seconds: 1);

  /// 登录页用的**移动浏览器** UA（见 `userAgent` 处的说明）。
  static const String _mobileBrowserUA =
      'Mozilla/5.0 (Linux; Android 13; PJZ110) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

  /// 查 cookie 的域名 —— 照抄 Android `WebLoginActivity.COOKIE_URLS`。
  /// 登录成功后服务端可能只把登录态写在其中某一个域上。
  static const List<String> _cookieUrls = [
    'https://www.coolapk.com',
    'https://coolapk.com',
    'https://account.coolapk.com',
    'https://m.coolapk.com',
  ];

  /// ★ Windows 上**必须**把 `webViewEnvironment` 传给 `CookieManager`，
  /// 否则取 cookie 会返回空（插件已知缺陷，详见 `utils/webview_env.dart`）。
  CookieManager get _cookieManager =>
      CookieManager.instance(webViewEnvironment: WebViewEnv.instance);

  @override
  void initState() {
    super.initState();
    _cookieManager.deleteAllCookies();
    if (GlobalData().isLogin) {
      _cookieManager.setCookie(
        url: WebUri.uri(Uri.parse('.coolapk.com')),
        name: 'DID',
        value: GStorage.szlmId,
      );
      _cookieManager.setCookie(
        url: WebUri.uri(Uri.parse('.coolapk.com')),
        name: 'forward',
        value: 'https://www.coolapk.com',
      );
      _cookieManager.setCookie(
        url: WebUri.uri(Uri.parse('.coolapk.com')),
        name: 'displayVersion',
        value: 'v14',
      );
      _cookieManager.setCookie(
        url: WebUri.uri(Uri.parse('.coolapk.com')),
        name: 'uid',
        value: GlobalData().uid,
      );
      _cookieManager.setCookie(
        url: WebUri.uri(Uri.parse('.coolapk.com')),
        name: 'username',
        value: GlobalData().username,
      );
      _cookieManager.setCookie(
        url: WebUri.uri(Uri.parse('.coolapk.com')),
        name: 'token',
        value: GlobalData().token,
      );
    }

    // ★ 登录页要**主动轮询 cookie**，不能只靠 URL 跳转事件。
    //
    // 上游原来只在 `shouldOverrideUrlLoading` 且 url **精确等于**
    // `https://www.coolapk.com/` 时才查 —— 登录成功后酷安通常跳到
    // `https://www.coolapk.com`（无尾斜杠）、带 `#/` 的路径、
    // 或干脆停在 account.coolapk.com，于是**永远查不到登录态**。
    //
    // Android `WebLoginActivity` 是「每秒轮询 + onPageFinished 也查一遍，
    // 最多等 10 分钟」，这里照做。
    if (_isLogin) {
      _cookieTimer = Timer.periodic(_cookieCheckInterval, (_) async {
        if (_loginHandled) return;
        if (await _checkLoginCookie()) return;
        if (++_elapsed > _loginTimeoutSeconds) {
          _cookieTimer?.cancel();
        }
      });
    }
  }

  /// 从 WebView 的 cookie 里取登录态；拿到就完成登录并返回 true。
  ///
  /// 对应 Android `WebLoginActivity.checkLoginCookie`：
  /// - 遍历 [_cookieUrls] 四个域（登录态可能只写在其中一个上）
  /// - **只要求 `uid` + `token`**（`username` 可能没有，不该因为它缺失就判失败）
  /// - `username` 是 **URL 编码**的：规范化成「编码一次」的形式，
  ///   与 `checkLoginInfo` 的写法保持一致，避免二次编码
  Future<bool> _checkLoginCookie() async {
    if (_loginHandled) return true;
    String? uid;
    String? token;
    String? username;

    for (final url in _cookieUrls) {
      if (uid != null && token != null && username != null) break;
      List<Cookie> list;
      try {
        // ★★★ 关键：**必须传 `webViewController`**。
        //
        // Windows 上不传它就取不到「带分区键的 cookie」，返回空列表 ——
        // 这正是登录一直检测不到的原因（插件已知缺陷，作者在
        // issue #2314 里明确给出这个修法，6.2.0-beta.2 起支持）。
        // 同时还要求 `CookieManager.instance(webViewEnvironment: ...)`
        // 与 `InAppWebView(webViewEnvironment: ...)` 传的是同一个环境。
        list = await _cookieManager.getCookies(
          url: WebUri.uri(Uri.parse(url)),
          webViewController: _webViewController,
        );
      } catch (_) {
        // 某个域取不到就跳过，别让一个异常打断整轮检查
        continue;
      }
      for (final c in list) {
        switch (c.name) {
          case 'uid':
            uid ??= c.value;
            break;
          case 'token':
            token ??= c.value;
            break;
          case 'username':
            username ??= c.value;
            break;
        }
      }
      // ★ 诊断：cookie 到底取到没有（Windows 上插件有取不到 cookie 的已知缺陷，
      //   修好后这里应该能看到 cookie 数量 > 0）
      debugPrint('webLogin: $url 取到 ${list.length} 个 cookie '
          '${list.map((e) => e.name).toList()}');
    }

    // 兜底：document.cookie
    debugPrint('webLogin: getCookies 得到 uid=${uid ?? "(无)"} '
        'token=${(token ?? "").isEmpty ? "(无)" : "有"}');

    // ★ 兜底：直接读 `document.cookie`。
    //
    // 不依赖插件的 cookie API，所以在插件仍有问题的平台上也可能拿到。
    // 局限：**读不到 HttpOnly 的 cookie** —— 所以它只是补充，
    // 主路径仍然是上面带 `webViewController` 的 `getCookies`。
    if (uid.isNullOrEmpty || token.isNullOrEmpty) {
      try {
        final raw = await _webViewController?.evaluateJavascript(
          source: 'document.cookie',
        );
        if (raw is String && raw.isNotEmpty) {
          for (final part in raw.split(';')) {
            final kv = part.trim().split('=');
            if (kv.length < 2) continue;
            final name = kv[0].trim();
            final value = kv.sublist(1).join('=').trim();
            if (value.isEmpty) continue;
            switch (name) {
              case 'uid':
                uid ??= value;
                break;
              case 'token':
                token ??= value;
                break;
              case 'username':
                username ??= value;
                break;
            }
          }
        }
      } catch (_) {
        // 兜底失败无所谓，主路径的结果才算数
      }
    }

    // ★ 只要 uid + token（Android 同此）
    if (uid.isNullOrEmpty || token.isNullOrEmpty) return false;

    _loginHandled = true;
    _cookieTimer?.cancel();

    GStorage.setUid(uid!);
    GStorage.setToken(token!);
    if (!username.isNullOrEmpty) {
      GStorage.setUsername(_normalizeUsername(username!));
    }
    GStorage.setIsLogin(true);

    SmartDialog.showToast('登录成功');
    Get.back(result: true);
    return true;
  }

  /// cookie 里的用户名是 URL 编码的；统一成「编码一次」的形式
  /// （对应 Android `saveLogin` 里的 `URLEncoder.encode(URLDecoder.decode(...))`）。
  String _normalizeUsername(String raw) {
    try {
      return Uri.encodeComponent(Uri.decodeComponent(raw));
    } catch (_) {
      return raw;
    }
  }

  @override
  void dispose() {
    _cookieTimer?.cancel();
    _titleStream.close();
    _progressStream.close();
    _webViewController = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: StreamBuilder(
          initialData: null,
          stream: _titleStream.stream,
          builder: (_, snapshot) => snapshot.data != null
              ? Text(
                  snapshot.data!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                )
              : const SizedBox.shrink(),
        ),
        bottom: PreferredSize(
          preferredSize: Size.zero,
          child: StreamBuilder(
            initialData: 0.0,
            stream: _progressStream.stream,
            builder: (_, snapshot) => snapshot.data as double < 1
                ? LinearProgressIndicator(
                    value: snapshot.data as double,
                  )
                : const SizedBox.shrink(),
          ),
        ),
        actions: [
          PopupMenuButton(
            onSelected: (item) async {
              switch (item) {
                case WebviewMenuItem.Refresh:
                  _webViewController?.reload();
                  break;
                case WebviewMenuItem.Copy:
                  WebUri? uri = await _webViewController?.getUrl();
                  if (uri != null) {
                    Utils.copyText(uri.toString());
                  }
                  break;
                case WebviewMenuItem.Open_In_Browser:
                  WebUri? uri = await _webViewController?.getUrl();
                  if (uri != null) {
                    Utils.launchURL(uri.toString());
                  }
                  break;
                case WebviewMenuItem.Clear_Cache:
                  try {
                    await InAppWebViewController.clearAllCache();
                    await _webViewController?.clearHistory();
                    SmartDialog.showToast('已清理');
                  } catch (e) {
                    SmartDialog.showToast(e.toString());
                  }
                  break;
                case WebviewMenuItem.Go_Back:
                  if (await _webViewController?.canGoBack() == true) {
                    _webViewController?.goBack();
                  }
                  break;
              }
            },
            itemBuilder: (context) => <PopupMenuEntry<WebviewMenuItem>>[
              ...WebviewMenuItem.values.sublist(0, 4).map(
                  (item) => PopupMenuItem(value: item, child: Text(item.label))),
              const PopupMenuDivider(),
              PopupMenuItem(
                  value: WebviewMenuItem.Go_Back,
                  child: Text(
                    WebviewMenuItem.Go_Back.name,
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error),
                  )),
            ],
          )
        ],
      ),
      body: InAppWebView(
        // ★ Windows 上要把同一个 `WebViewEnvironment` 也传给 WebView 本体，
        // 否则 `CookieManager` 拿到的环境跟它不一致，照样取不到 cookie。
        webViewEnvironment: WebViewEnv.instance,
        initialSettings: InAppWebViewSettings(
          useHybridComposition: false,
          mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
          useShouldOverrideUrlLoading: true,
          useOnDownloadStart: true,
          clearCache: true,
          // ★ 登录页要用**移动浏览器的 UA**。
          //
          // Android `WebLoginActivity` 的原注释是「保留系统 WebView 的移动 UA，
          // 让酷安网页按移动浏览器渲染，才能拿到完整的登录表单」——
          // 在 Android 上系统 WebView 的默认 UA **本来就是移动的**，所以它不用设。
          //
          // 但桌面端默认 UA 是**桌面**的：实测（2026-10-17）酷安会返回
          // 「微信扫码登录」那一版，只能扫码；换了移动 UA 才会给
          // 账号 / 密码 / 短信验证码那套表单，选择更多。
          //
          // 也不能用 `GStorage.userAgent`（酷安 App 自己的 UA）——
          // 那样可能被引导去「打开 App」而不是登录表单。
          userAgent: _isLogin ? _mobileBrowserUA : GStorage.userAgent,
          forceDark: ForceDark.AUTO,
          algorithmicDarkeningAllowed: true,
        ),
        initialUrlRequest:
            URLRequest(url: WebUri.uri(Uri.parse(_url)), headers: {
          'X-Requested-With': Constants.APP_ID,
        }),
        onWebViewCreated: (InAppWebViewController controller) {
          _webViewController = controller;
        },
        onProgressChanged: (controller, progress) {
          _progressStream.add(progress / 100);
        },
        // 页面加载完也查一次 cookie —— 对应 Android `WebLoginActivity` 的
        // `onPageFinished { checkLoginCookie() }`。
        // （轮询是兜底，这个更及时。）
        onLoadStop: (controller, url) async {
          if (_isLogin) await _checkLoginCookie();
        },
        onTitleChanged: (controller, title) {
          _titleStream.add(title);
        },
        onCloseWindow: (controller) => Get.back(),
        shouldOverrideUrlLoading: (controller, navigationAction) async {
          var url = navigationAction.request.url!.toString();

          if (!url.startsWith('http')) {
            var snackBar = SnackBar(
              content: const Text('当前网页将要打开外部链接，是否打开'),
              showCloseIcon: true,
              action: SnackBarAction(
                label: '打开',
                onPressed: () => Utils.launchURL(url),
              ),
            );
            ScaffoldMessenger.of(context).showSnackBar(snackBar);
            return NavigationActionPolicy.CANCEL;
          }

          if (_isLogin && url == Constants.URL_COOLAPK) {
            // 保留这个精确匹配作为**额外**触发点；
            // 真正的检测靠 _checkLoginCookie（轮询 + onLoadStop + 4 个域）
            await _checkLoginCookie();
            return NavigationActionPolicy.CANCEL;
          }

          return NavigationActionPolicy.ALLOW;
        },
        onDownloadStartRequest: (controller, request) {
          showDialog(
              context: context,
              builder: (context) {
                String suggestedFilename = request.suggestedFilename.toString();
                String fileSize =
                    CacheManage.formatSize(request.contentLength.toDouble());
                try {
                  suggestedFilename = Uri.decodeComponent(suggestedFilename);
                } catch (e) {
                  debugPrint(e.toString());
                }
                return AlertDialog(
                  title: Text(
                    'Download file: $suggestedFilename ?',
                    style: const TextStyle(fontSize: 18),
                  ),
                  content: SelectableText(request.url.toString()),
                  actions: [
                    TextButton(
                        onPressed: () => Get.back(),
                        child: const Text('关闭')),
                    TextButton(
                        onPressed: () async {
                          Get.back();
                          Utils.onDownloadFile(
                            request.url.toString(),
                            suggestedFilename,
                          );
                        },
                        child: Text('确定 ($fileSize)')),
                  ],
                );
              });
          _progressStream.add(1);
        },
      ),
    );
  }
}
