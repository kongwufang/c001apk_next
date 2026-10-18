import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import 'storage_util.dart';

/// 自更新检查。
///
/// 对应 Android `util/UpdateChecker.kt`，**接口完全一样** ✓
///
/// ```
/// GET https://service.houlangs.cn/c001apk/update/
/// 请求头：
///   X-App-userrandomid : 本机随机用户 ID（见 GStorage.userRandomId）✓
///   X-Client-Version   : 版本名
///   X-Client-Code      : 版本号
///   X-Client-Platform  : android / windows   ← ★ 桌面版标记（用户要求 ✓）
/// 响应：
///   {"code":0,"message":"ok","time":…,
///    "stable":{"versionName":"1.0.1","versionCode":468,"changelog":"…",
///              "download":[{"name":"线路一","url":"…"}]},
///    "org":{"buttons":[{"name":"官方群组","url":"…"}]}}
/// ```
///
/// ## 桌面版标记
/// 服务端据此**区分两个版本渠道** ✓ —— Windows 与 Android 的 `versionCode`
/// 完全独立（桌面端从 1 开始，Android 已经到 2609291 ✗ 混在一起比必然误判 ✗）
/// ⇒ `X-Client-Platform: windows` 是**必须**的 ✓
///
/// ## 随机用户 ID
/// **不**用 DUID / SZLM ID（那能反查到具体设备 ✗）
/// 用 `GStorage.userRandomId`：本机随机生成一次、落盘、之后每次同一个 ✓
/// 与 Android `PrefManager.userRandomId` 同一套逻辑 ✓
class UpdateChecker {
  UpdateChecker._();

  static const String _baseUrl =
      'https://service.houlangs.cn/c001apk/update/';

  /// 桌面端标记（服务端据此区分渠道）
  static const String platform = 'windows';

  static final Dio _dio = Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
  ));

  /// 拉取更新信息。失败返回 null（不抛异常，调用方弹 Toast 即可 ✓）
  static Future<UpdateInfo?> fetchUpdate() async {
    try {
      final res = await _dio.get<Map<String, dynamic>>(
        _baseUrl,
        options: Options(headers: _headers()),
      );
      final body = res.data;
      // ⚠️ `is! Map` **不能**把 `Map<String, dynamic>?` 提升为非空 ✗
      //    （负向分支已 return，但类型系统只把它当 `Map<dynamic, dynamic>?`）
      //    ⇒ 必须**显式**判 null ✓
      if (body == null) return null;
      if ((body['code'] as num?)?.toInt() != 0) return null;

      final stable = body['stable'];
      if (stable is! Map) return null;
      final s = Map<String, dynamic>.from(stable);

      final lines = <DownloadLine>[];
      final rawLines = s['download'];
      if (rawLines is List) {
        for (final it in rawLines) {
          if (it is Map) {
            final name = '${it['name'] ?? ''}';
            final url = '${it['url'] ?? ''}';
            if (url.isNotEmpty) lines.add(DownloadLine(name, url));
          }
        }
      }

      return UpdateInfo(
        versionName: '${s['versionName'] ?? ''}',
        versionCode: (s['versionCode'] as num?)?.toInt() ?? -1,
        changelog: '${s['changelog'] ?? ''}',
        lines: lines,
      );
    } catch (e) {
      debugPrint('UpdateChecker.fetchUpdate 失败: $e');
      return null;
    }
  }

  /// 当前版本号（**int**）
  ///
  /// `GStorage.versionCode` 存的是 **String** ✗ 这里统一 parse 一次，
  /// 解析失败按 1 处理（= 全新安装 ✓）
  static int _currentCode() {
    final raw = GStorage.versionCode;
    final v = int.tryParse(raw);
    return (v == null || v <= 0) ? 1 : v;
  }

  /// 与 Android 完全一致的四个头 ✓
  static Map<String, String> _headers() {
    final versionName =
        GStorage.versionName.isEmpty ? '1.0.0' : GStorage.versionName;
    // ⚠️ `GStorage.versionCode` 是 **String** ✗（不是 int）
    //    必须 parse，否则 `== 0` / `? 1 :` 都是类型错误 ⇒ Analyze 红 ✗
    final versionCode = _currentCode();
    return {
      'X-App-userrandomid': GStorage.userRandomId,
      'X-Client-Version': versionName,
      'X-Client-Code': versionCode.toString(),
      'X-Client-Platform': platform,
      'User-Agent': 'c001apk-flutter/$versionName ($platform)',
    };
  }
}

class DownloadLine {
  const DownloadLine(this.name, this.url);

  final String name;
  final String url;
}

class UpdateInfo {
  const UpdateInfo({
    required this.versionName,
    required this.versionCode,
    required this.changelog,
    required this.lines,
  });

  final String versionName;
  final int versionCode;
  final String changelog;
  final List<DownloadLine> lines;

  /// 比当前版本新吗？
  ///
  /// ⚠️ 只跟**本平台**的 versionCode 比 ✓
  /// 桌面端的 versionCode 与 Android 的完全独立（Android 已到 2609291 ✗）
  bool get isNewer {
    // 同上：`GStorage.versionCode` 是 String ✗
    return versionCode > UpdateChecker._currentCode();
  }
}
