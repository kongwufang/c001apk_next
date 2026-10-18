import 'package:connectivity_plus/connectivity_plus.dart';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../../constants/constants.dart';
import '../../utils/global_data.dart';
import '../../utils/http_log.dart';
import 'api_error.dart';
import '../../utils/storage_util.dart';
import '../../utils/token_util.dart';

class ApiInterceptor extends Interceptor {
  String? _xAppDevice;
  String? _userAgent;
  String? _sdkInt;
  String? _versionName;
  String? _versionCode;
  String? _apiVersion;

  String get apiVersion {
    return _apiVersion ??= GStorage.apiVersion;
  }

  String get versionCode {
    return _versionCode ??= GStorage.versionCode;
  }

  String get versionName {
    return _versionName ??= GStorage.versionName;
  }

  String get sdkInt {
    return _sdkInt ??= GStorage.sdkInt;
  }

  String get xAppDevice {
    return _xAppDevice ??= GStorage.xAppDevice;
  }

  String get userAgent {
    return _userAgent ??= GStorage.userAgent;
  }

  /// `X-App-Token`。
  ///
  /// ★ 上游是 v2（[TokenUtils.getTokenV2]），用户 Android 端已升级到 **v3**，
  /// 这里对齐（`_rev/win_ref/README.md` §2.3 实测：服务端对 token **严格校验**，
  /// 伪造/缺失都会被拒；token 与 `X-App-Code` 不一致直接 HTTP 403）。
  ///
  /// ⚠️ **不能缓存**：v3 把时间戳算进去了（`off = 4*((t+vc)%100)+128`），
  /// 缓存会让窗口和盐都过期。Android 端也是每个请求现算。
  String get token {
    return TokenUtils.getTokenV3(xAppDevice, versionCode: versionCode);
  }

  /// 只在**第一个**请求打印一次完整头部 —— 排查「请求验证错误 / 403」用。
  ///
  /// 为什么要它：`X-Api-Version` / `X-App-Version` / `X-App-Code` /
  /// `X-App-Device` / `User-Agent` 任一为空或不配套，服务端就回
  /// **403 + 空响应体**（实测确认 ✓），而这种失败**看不出**是哪一项 ✗。
  ///
  /// ⚠️ 这些值在下面几个 getter 里是 `??=` **缓存**的 ✗ ——
  ///   如果第一次请求发生在参数初始化之前，缓存就被空值**永久污染** ✗
  ///   ⇒ 整个进程的所有请求都会 403 ✗（这行的输出就是用来验证这件事的）
  static bool _headersLogged = false;

  void _logHeadersOnce(Map<String, dynamic> h) {
    if (_headersLogged) return;
    _headersLogged = true;
    String v(String k) {
      final s = h[k]?.toString() ?? '';
      if (s.isEmpty) return '(空)✗';
      // 敏感项只留长度
      if (k == 'X-App-Token' || k == 'Cookie') return '(len=${s.length})';
      if (k == 'X-App-Device') {
        return s.length <= 16 ? '(len=${s.length})' : '${s.substring(0, 12)}…(len=${s.length})';
      }
      return s;
    }
    debugPrint('HTTP HEADERS | '
        'ApiVer=${v('X-Api-Version')} | '
        'AppVer=${v('X-App-Version')} | '
        'AppCode=${v('X-App-Code')} | '
        'Supported=${v('X-App-Supported')} | '
        'SdkInt=${v('X-Sdk-Int')} | '
        'AppId=${v('X-App-Id')} | '
        'Mode=${v('X-App-Mode')} | '
        'Dev=${v('X-App-Device')} | '
        'Token=${v('X-App-Token')} | '
        'Cookie=${v('Cookie')} | '
        'UA=${v('User-Agent')}');
  }

  @override
  void onRequest(options, handler) async {
    // 记下起点，供 HttpLog 算耗时 ✓（关着开关也只是一个 Map 写入 ✓ 可忽略）
    options.extra['_httpLogT0'] = DateTime.now().microsecondsSinceEpoch;

    if (TokenUtils.isPreGetLoginParam) {
      options.headers.clear();
      TokenUtils.isPreGetLoginParam = false;

      options.headers['sec-ch-ua'] =
          '"Android WebView";v="117", "Not;A=Brand";v="8", "Chromium";v="117"';
      options.headers['sec-ch-ua-mobile'] = '?1';
      options.headers['sec-ch-ua-platform'] = 'Android';
      options.headers['Upgrade-Insecure-Requests'] = '1';
      options.headers['User-Agent'] = userAgent;
      options.headers['Accept'] =
          'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7';
      options.headers['X-Requested-With'] = Constants.APP_ID;
    } else if (TokenUtils.isGetLoginParam) {
      options.headers.clear();
      TokenUtils.isGetLoginParam = false;

      options.headers['sec-ch-ua'] =
          '"Android WebView";v="117", "Not;A=Brand";v="8", "Chromium";v="117"';
      options.headers['sec-ch-ua-mobile'] = '?1';
      options.headers['sec-ch-ua-platform'] = 'Android';
      options.headers['Upgrade-Insecure-Requests'] = '1';
      options.headers['User-Agent'] = userAgent;
      options.headers['Accept'] =
          'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7';
      options.headers['X-Requested-With'] = Constants.APP_ID;
      options.headers['Cookie'] = GlobalData().SESSID;
    } else if (TokenUtils.isGetCaptcha) {
      options.headers.clear();
      TokenUtils.isGetCaptcha = false;

      options.headers['User-Agent'] = userAgent;
      options.headers['sec-ch-ua'] =
          '"Android WebView";v="117", "Not;A=Brand";v="8", "Chromium";v="117"';
      options.headers['sec-ch-ua-mobile'] = '?1';
      options.headers['sec-ch-ua-platform'] = 'Android';
      options.headers['Accept'] =
          'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8';
      options.headers['X-Requested-With'] = Constants.APP_ID;
      options.headers['Sec-Fetch-Site'] = 'same-origin';
      options.headers['Sec-Fetch-Site'] = 'same-origin';
      options.headers['Sec-Fetch-Mode'] = 'no-cors';
      options.headers['Sec-Fetch-Dest'] = 'image';
      options.headers['Referer'] =
          'https://account.coolapk.com/auth/loginByCoolapk';
      options.headers['Cookie'] =
          '${GlobalData().SESSID}; forward=https://www.coolapk.com';
    } else if (TokenUtils.isOnLogin) {
      options.headers.clear();
      TokenUtils.isOnLogin = false;

      options.headers['User-Agent'] = userAgent;
      options.headers['Cookie'] =
          '${GlobalData().SESSID}; forward=https://www.coolapk.com';
      options.headers['X-Requested-With'] = Constants.REQUEST_WITH;
      options.headers['Content-Type'] = Constants.REQUEST_WITH;
    } else {
      options.headers['User-Agent'] = userAgent;
      options.headers['X-Requested-With'] = Constants.REQUEST_WITH;
      options.headers['X-Sdk-Int'] = sdkInt;
      options.headers['X-Sdk-Locale'] = Constants.LOCALE;
      options.headers['X-App-Id'] = Constants.APP_ID;
      options.headers['X-App-Token'] = token;
      options.headers['X-App-Version'] = versionName;
      options.headers['X-App-Code'] = versionCode;
      options.headers['X-Api-Version'] = apiVersion;
      options.headers['X-App-Device'] = xAppDevice;
      options.headers['X-Dark-Mode'] = '0';
      options.headers['X-App-Channel'] = Constants.CHANNEL;
      options.headers['X-App-Mode'] = Constants.MODE;
      options.headers['X-App-Supported'] = versionCode;
      // 上游缺 Referer（Android `AddCookiesInterceptor` 里有）：
      //   ref=CoolMarket/<版本>-<code>-<mode>，服务端会看
      options.headers['Referer'] =
          'https://api.coolapk.com/?ref=CoolMarket/$versionName-$versionCode-${Constants.MODE}';
      // ⚠️ **不要**在这里全局加 `Content-Type: application/x-www-form-urlencoded`。
      // 踩过的坑：Android 的 OkHttp 拦截器是全局加的，照抄过来之后首页直接变成
      // 服务端返回的「请求方式错误(1)」—— GET 请求带上表单 Content-Type 会被拒。
      // 对照 `_rev/win_ref/coolapk_protocol.py`：它的 `build_headers()` **不含**
      // Content-Type，只在 `post_form()` 里加。本工程同理，按请求由
      // `Options(contentType: Headers.formUrlEncodedContentType)` 指定
      // （见 `network_repo.dart`，上传走 multipartFormDataContentType）。
      // ★ 凭据优先级：`refreshToken`（启动时换到的第二凭据）> `token`（主凭据）
      //
      //   两者在业务接口上表现一致（见 `GlobalData.refreshToken` 的注释），
      //   所以启动换到后就整个进程都用它，直到下次启动再换。
      final authToken = GlobalData().refreshToken.isNotEmpty
          ? GlobalData().refreshToken
          : GlobalData().token;
      options.headers['Cookie'] = GlobalData().isLogin
          ? 'uid=${GlobalData().uid}; username=${GlobalData().username}; token=$authToken'
          : GlobalData().SESSID;
    }

    _logHeadersOnce(options.headers);
    handler.next(options);
  }

  @override
  void onResponse(response, handler) async {
    _logHttp(
      method: response.requestOptions.method,
      url: response.requestOptions.uri.toString(),
      status: response.statusCode,
      t0: response.requestOptions.extra['_httpLogT0'],
      reqBody: _bodyOf(response.requestOptions.data),
      respBody: _bodyOf(response.data),
    );
    handler.next(response);
  }

  void onError(err, handler) async {
    _logHttp(
      method: err.requestOptions.method,
      url: err.requestOptions.uri.toString(),
      status: err.response?.statusCode,
      t0: err.requestOptions.extra['_httpLogT0'],
      reqBody: _bodyOf(err.requestOptions.data),
      respBody: _bodyOf(err.response?.data),
      error: '${err.type.name}: ${err.message ?? ''}',
    );
    String url = err.requestOptions.uri.toString();
    // ★ 诊断：任何请求失败都打一行到 stderr（Windows 上会写进日志文件 ✓）
    //   有它才能定位「哪个接口挂了」✗ —— 否则只有一句 toast，看不出接口 ✗
    debugPrint(
      'HTTP FAIL | ${err.type.name} | ${err.requestOptions.method} $url'
      ' | status=${err.response?.statusCode} | ${err.message ?? ''}',
    );
    if (!url.contains('/v6/apk/download') &&
        !url.contains('/v6/message/showImage')) {
      // ★ toast 也说人话 —— 原来固定「服务器异常，请稍后重试！」✗
      //   现在优先用 ApiError（403+空响应体会带「关代理」提示 ✓）
      final api = ApiError.fromDio(err);
      final detail = api.detail;
      final text = detail == null || detail.isEmpty
          ? api.title
          : '${api.title}（$detail）';
      SmartDialog.showToast(
        text,
        displayType: SmartToastType.onlyRefresh,
      );
      // dioError 仍保留：它有「正在使用移动流量 / wifi」这类网络状态文案 ✓
      // 只在 ApiError 没给出更有用信息时才用 —— 这里只做兜底，不再覆盖 ✓
      if (api.title.isEmpty) {
        SmartDialog.showToast(await dioError(err));
      }
    }
    super.onError(err, handler);
  }

  /// 把请求体/响应体转成可写日志的字符串（超长由 HttpLog 截断 ✓）
  static String _bodyOf(dynamic data) {
    if (data == null) return '';
    try {
      if (data is String) return data;
      if (data is FormData) {
        // multipart 只记字段名 ✓ 文件内容不记 ✗
        final fields = data.fields.map((e) => '${e.key}=${e.value}').join('&');
        final files = data.files.map((e) => '${e.key}=<file:${e.value.filename}>').join('&');
        return [fields, files].where((e) => e.isNotEmpty).join('&');
      }
      return jsonEncode(data);
    } catch (_) {
      return data.toString();
    }
  }

  /// 记一条 HTTP 日志（开关关着 ⇒ HttpLog 内部直接返回 ✓）
  static void _logHttp({
    required String method,
    required String url,
    int? status,
    dynamic t0,
    String? reqBody,
    String? respBody,
    String? error,
  }) {
    if (!GStorage.recordHttpLog) return; // ★ 零开销：一次 bool 判断 ✓
    final start = t0 is int ? t0 : null;
    final elapsed = start == null
        ? Duration.zero
        : Duration(microseconds: DateTime.now().microsecondsSinceEpoch - start);
    // 不 await ✓ 绝不能拖慢请求 ✓
    HttpLog.record(
      method: method,
      url: url,
      status: status,
      elapsed: elapsed,
      reqBody: reqBody,
      respBody: respBody,
      error: error,
    );
  }

  static Future<String> dioError(DioException error) async {
    switch (error.type) {
      case DioExceptionType.badCertificate:
        return '证书有误！';
      case DioExceptionType.badResponse:
        return '服务器异常，请稍后重试！';
      case DioExceptionType.cancel:
        return '请求已被取消，请重新请求';
      case DioExceptionType.connectionError:
        return '连接错误，请检查网络设置';
      case DioExceptionType.connectionTimeout:
        return '网络连接超时，请检查网络设置';
      case DioExceptionType.receiveTimeout:
        return '响应超时，请稍后重试！';
      case DioExceptionType.sendTimeout:
        return '发送请求超时，请检查网络设置';
      case DioExceptionType.unknown:
        final String res = await checkConnect();
        return '$res，网络异常！';
      // 新版 Dart / dio 增加了 transformTimeout（以后还会继续加）。
      // 原写法是穷尽 switch，缺一个值就直接编译失败：
      //   error G42E56F65: not exhaustively matched ... 'transformTimeout'
      // 这里补一个 default，升级 SDK 就不会再因为这个挂掉。
      default:
        return '网络异常，请稍后重试！';
    }
  }

  static Future<String> checkConnect() async {
    final List<ConnectivityResult> connectivityResult =
        await Connectivity().checkConnectivity();
    if (connectivityResult.contains(ConnectivityResult.mobile)) {
      return '正在使用移动流量';
    } else if (connectivityResult.contains(ConnectivityResult.wifi)) {
      return '正在使用wifi';
    } else if (connectivityResult.contains(ConnectivityResult.ethernet)) {
      return '正在使用局域网';
    } else if (connectivityResult.contains(ConnectivityResult.vpn)) {
      return '正在使用代理网络';
    } else if (connectivityResult.contains(ConnectivityResult.bluetooth)) {
      return '正在使用蓝牙网络';
    } else if (connectivityResult.contains(ConnectivityResult.other)) {
      return '正在使用其他网络';
    } else if (connectivityResult.contains(ConnectivityResult.none)) {
      return '未连接到任何网络';
    } else {
      return '';
    }
  }
}
