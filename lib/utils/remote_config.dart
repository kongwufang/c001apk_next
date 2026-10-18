import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../utils/storage_util.dart';
import 'verify_badge.dart';

/// 云端配置（自建服务）—— 对齐 Android `util/RemoteConfig.kt`。
///
/// ## 为什么要有它
/// 「用户认证」的**本地特例**（谁有认证、认证文字、角标色号）不能写死在客户端里 ✗
/// —— 写死就得每次改代码重发版 ✗
/// ⇒ 服务端下一张表，客户端启动时拉一次、落盘缓存 ✓
///
/// ## 现在只有一张表
/// `userverify`：用户认证表 ✓
/// ```
/// https://service.houlangs.cn/c001apk/userverify/
/// ```
/// 后续要加别的表（可信链接白名单之类）照着 `refreshAsync` 加一段即可 ✓
///
/// ## 缓存策略（与 Android 一致）
/// 1. 启动时先 [initFromCache] —— 用上次的缓存**立刻**生效 ✓（离线也能显示 ✓）
/// 2. 再 [refreshAsync] 后台拉一次 ✓ 成功才覆盖缓存 ✓
/// 3. 失败就**什么都不做** ✗ —— 绝不能因为一次响应异常把已有认证清空 ✗
///
/// ⚠️ 这个请求**不能**走 `Request()`（那个 dio 带酷安的全套鉴权头 ✗
/// 把 `X-App-Token` / `Cookie` 发给自己的服务器是没必要的泄露 ✗）
/// ⇒ 这里用**独立的裸 Dio** ✓ 不带任何凭证 ✓
class RemoteConfig {
  RemoteConfig._();

  /// 用户认证表
  static const String verifyUrl =
      'https://service.houlangs.cn/c001apk/userverify/';

  /// 独立 Dio：只给自己服务用，不带酷安凭证 ✓
  static final Dio _dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 8),
      receiveTimeout: const Duration(seconds: 8),
      responseType: ResponseType.plain,
    ),
  );

  /// 用本地缓存先把表建起来（启动时**同步**调 ✓）
  static void initFromCache() {
    VerifyBadge.load(GStorage.userVerifyConfig);
  }

  /// 后台刷新一次（不 await，不阻塞启动 ✓）
  static void refreshAsync() {
    _refresh();
  }

  static Future<void> _refresh() async {
    try {
      final response = await _dio.get(verifyUrl);
      final text = response.data?.toString() ?? '';
      if (response.statusCode != 200 || text.trim().isEmpty) return;
      // 先落盘再建表：失败了下次启动还能用 ✓
      GStorage.setUserVerifyConfig(text);
      VerifyBadge.load(text);
      debugPrint('RemoteConfig: 认证表已刷新（${text.length} 字节）');
    } catch (e) {
      // 静默失败 ✓ 继续用缓存 ✓
      debugPrint('RemoteConfig: 认证表刷新失败（沿用缓存）$e');
    }
  }
}
