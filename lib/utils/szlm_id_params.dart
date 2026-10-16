import 'dart:convert';

import '../constants/constants.dart';
import 'storage_util.dart';

/// 「获取数字联盟 ID」流程的入参构造。
///
/// 对齐 Android `util/SzlmIdParams.kt` + `ui/others/SzlmIdWebActivity.kt`。
///
/// ## 完整流程
/// ```
/// WebView 打开 WEB_ENTRY
///   UA = 真实浏览器 UA + " C001ApkWebView"
///        （服务端要求 UA 同时带 `wv`（Android WebView 固有标记）和这个标记，
///          于是"在浏览器里直接打开网页"拿不到号）
///   允许第三方 Cookie（Turnstile 的 iframe 在 challenges.cloudflare.com 上）
///   注入 JsBridge "C001Bridge"：
///     getParams()      → 同步返回本文件构造的参数 JSON
///     onSuccess(duid)  → 落盘 DUID
/// ```
/// 网页把参数 POST 给服务端，服务端做 ①签名白名单校验 ②Cloudflare Turnstile
/// 人机验证，都过了才签发 DUID。
///
/// ## deviceinfo 必须逐字节稳定
/// 服务端用 `deviceinfo` 的**原文**做指纹派生。同一台机器每次生成必须完全一致，
/// 不能掺时间戳 / 随机数，否则每次都会换一个新 DUID、白耗取号额度。
class SzlmIdParams {
  /// 官方包的签名证书 SHA-256（小写 hex）。
  ///
  /// ⚠️ **桌面端暂时硬编码**：Android 端是运行时从 `PackageManager` 读本包真实签名
  /// （`SzlmIdParams.signature()`），桌面端没有 Android 签名证书，
  /// 所以先写死用户 APP 里那一份把流程跑通。
  ///
  /// 服务端拿它核验是不是官方包，不是就拒绝签发（对外只说「访问被拒绝」）。
  /// 后续若要正式化，应改成从自身安装包计算或由服务端下发。
  static const OFFICIAL_SIGNATURE =
      '9e7692d0d3f996476c81be80841fc638530eeb1285c8ba325b65ca47af190ec8';

  /// 内置网页地址（人机验证 + 签名校验都过了才签发）
  static const WEB_ENTRY = 'https://service.houlangs.cn/c001apk/szlmid/webgetv1/';

  /// App 内置 WebView 的 UA 私有标记（放在 UA 末尾）
  static const WEB_UA_TAG = 'C001ApkWebView';

  /// 交给内置 WebView 用的 UA。
  ///
  /// Android 端用系统真实 WebView UA（Turnstile 要真实浏览器 UA，
  /// 且**刻意不套**给酷安接口伪装用的 `Dalvik/… +CoolMarket/…`）。
  /// 桌面端的 WebView2 UA 不含服务端要求的 `wv` 标记，所以这里按 Android WebView
  /// 的形态合成一个带 `wv` 的 UA，末尾再挂上 [WEB_UA_TAG]。
  static String webUserAgent() =>
      'Mozilla/5.0 (Linux; Android ${Constants.DEFAULT_ANDROID_VERSION}; '
      '${Constants.DEFAULT_MODEL} Build/${Constants.DEFAULT_BUILDNUMBER}; wv) '
      'AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 '
      'Chrome/117.0.0.0 Mobile Safari/537.36 $WEB_UA_TAG';

  /// 组装给内置网页的全部参数（JsBridge 被回调时同步返回）。
  static String buildForWeb() {
    return jsonEncode({
      'ua': webUserAgent(),
      'deviceinfo': deviceInfoJson(),
      'appinfo': appInfoJson(),
      'signature': OFFICIAL_SIGNATURE,
    });
  }

  /// `deviceinfo`：机型参数 + SSAID。
  ///
  /// 手拼而非 `jsonEncode`：服务端按**原文**比对，键顺序必须固定。
  /// 与 Android 一致：`manufacturer / brand / model / build / android / sdk / ssaid`。
  static String deviceInfoJson() {
    final ssaid = GStorage.ssaid;
    return '{'
        '"manufacturer":${_q(Constants.DEFAULT_MANUFACTURER)},'
        '"brand":${_q(Constants.DEFAULT_BRAND)},'
        '"model":${_q(Constants.DEFAULT_MODEL)},'
        '"build":${_q(Constants.DEFAULT_BUILDNUMBER)},'
        '"android":${_q(Constants.DEFAULT_ANDROID_VERSION)},'
        '"sdk":${_q(Constants.SDK_INT)},'
        '"ssaid":${_q(ssaid)}'
        '}';
  }

  /// `appinfo`：本客户端自己的版本信息（服务端只记日志，不参与业务）。
  ///
  /// 注意用**本应用**的标识，不是伪装给酷安接口的那套版本号。
  static String appInfoJson() {
    return '{'
        '"ver":${_q(Constants.VERSION_NAME)},'
        '"code":${int.tryParse(Constants.VERSION_CODE) ?? 0},'
        '"pkg":${_q(Constants.APP_ID)},'
        '"debug":false'
        '}';
  }

  /// JSON 字符串字面量（等价于 Android 的 `JSONObject.quote`）
  static String _q(String s) => jsonEncode(s);
}
