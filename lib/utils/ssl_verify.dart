import 'dart:io';

import 'package:flutter/services.dart';

import 'storage_util.dart';

/// SSL 证书校验 + 网络传输调试模式。
///
/// 忠实移植 Android `util/SslVerify.kt`（232 行）+ `res/raw/cacert_*.pem`（122 张）。
///
/// ## Android 那边的策略（两道关，都要过）
/// 1. **平台校验**：走系统证书库做标准 PKIX 校验。
///    这一步不能省 —— Coolapk 的 `api.coolapk.com` 证书链锚在
///    **DigiCert Global Root CA（SHA-1 老根）**，而 Conscrypt 只允许**系统库**里的
///    锚点使用 SHA-1，所以必须让平台先验一遍。
/// 2. **锚点白名单**：证书链的信任锚必须落在应用内置的 CA 库里
///    （122 张 = Mozilla CA 全集 + 追加的 DigiCert 历史根）。
///    用户自己装的抓包证书（Charles/Fiddler/mitmproxy）、厂商预置私有根、
///    被劫持后替换的 CA，都不在库里 → 一律拒绝。
///
/// 关闭开关则回落到系统默认校验。
/// 「网络传输调试模式」开启后**彻底跳过校验**，方便抓包调试（与严格校验互斥）。
///
/// ## ★ 本工程的实现与差异（如实说明）
/// Dart 的 `HttpClient` **只能给一个 `SecurityContext`**，
/// 没有 OkHttp `CertificatePinner` / `X509TrustManager` 那种「验完再查锚点」的钩子，
/// 所以拿不到证书链去比对锚点。这里退一步：
///
/// ```
/// SecurityContext(withTrustedRoots: false) + 内置 122 张 CA
/// ```
///
/// 等价于 Android 的第 2 关（**只信任内置库**）：
/// - 抓包证书 / 私有根 / 被替换的 CA 一律被拒 ✓
/// - 内置库里有 DigiCert 历史根，所以 Coolapk 的链能验过 ✓
///
/// 差别只在「不做平台校验那一遍」—— 而那一遍在 Android 上是为 SHA-1 的历史包袱
/// 服务的；Dart 底层的 BoringSSL 对**根的自签名**不做 SHA-1 限制，
/// 所以这里不需要它（已用 Python/OpenSSL 拿这 122 张实测过：
/// `api` / `api2` / `image` / `www` 四个域全部验过）。
///
/// 关闭开关 → 用系统根（`withTrustedRoots: true`，不加内置库）。
class SslVerify {
  const SslVerify._();

  /// 内置 CA bundle 的资源路径
  static const String _assetPath = 'assets/cacert/mozilla_all.pem';

  /// 构建好的严格校验上下文（`init()` 里准备好，`Request` 直接用）
  static SecurityContext? _strictContext;

  /// 是否已经初始化过
  static bool _inited = false;

  /// 严格校验上下文；未初始化或读取失败时为 null（调用方退回系统默认）。
  static SecurityContext? get strictContext => _strictContext;

  /// 在 `main()` 里、建 `Request` 之前调用一次。
  ///
  /// **为什么不懒加载**：Dio 的 `onHttpClientCreate` 回调是**同步**的，
  /// 里面没法 `await rootBundle.load(...)`。所以必须在启动时先备好。
  static Future<void> init() async {
    if (_inited) return;
    _inited = true;
    try {
      final data = await rootBundle.load(_assetPath);
      final bytes = data.buffer.asUint8List();
      // `withTrustedRoots: false` = **不**信任系统根，只信这里塞进去的 122 张
      final ctx = SecurityContext(withTrustedRoots: false)
        ..setTrustedCertificatesBytes(bytes);
      _strictContext = ctx;
    } catch (_) {
      // 资源缺失 / 解析失败：保持 null，请求层退回系统默认校验（不至于用不了）
      _strictContext = null;
    }
  }

  /// 是否开启「校验 SSL 证书」（默认开，对齐 Android `settings.xml` 的 `defaultValue=true`）
  static bool get enabled => GStorage.sslVerify;

  /// 是否开启「网络传输调试模式」（默认关；与严格校验互斥）
  static bool get debugMode => GStorage.sslDebug;

  /// 给 `HttpClient` 用的「是否放行一切证书」判定。
  ///
  /// 只有**调试模式**才放行；默认一律不放行。
  static bool allowBadCertificate(X509Certificate? cert) => debugMode;

  /// 实际要交给 `HttpClient` 的 `SecurityContext`。
  ///
  /// | 状态 | 返回 | 含义 |
  /// |---|---|---|
  /// | 调试模式 | `null` + 放行一切 | 彻底跳过校验（配合 `badCertificateCallback`） |
  /// | 严格校验开 | 内置 CA 上下文 | 只信内置库 |
  /// | 严格校验关 | `null` | 系统默认 |
  static SecurityContext? contextForClient() {
    if (debugMode) return null;
    if (!enabled) return null;
    return _strictContext;
  }
}
