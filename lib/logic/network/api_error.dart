import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

/// 统一的错误描述。
///
/// ## 为什么要在**请求层**做这件事
/// 服务端返回异常时，UI 只拿到一句 `statusCode: 500` ✗ 完全没法看 ✗
/// 而服务端其实**经常给了 `message`** ✓（如「该内容已被删除」「参数错误」✓）
///
/// ⇒ 在这里一次性把「响应体 / 异常类型」翻译成人话 ✓
/// ⇒ UI 层只管渲染 [`title`] / [`detail`] ✓ 不再自己猜 ✓
///
/// ## 文案优先级
/// ```
/// ① JSON 里的 message     ← 用户最想看的 ✓
/// ② JSON 里的 error / msg / error_description / errorMessage
/// ③ 纯文本响应（HTML 错误页等）→ 截前 200 字 ✓
/// ④ 都没有 → 按 HTTP 状态码给固定文案 ✓
/// ```
class ApiError {
  const ApiError({
    required this.title,
    this.detail,
    this.statusCode,
    this.path,
    this.rawBody,
  });

  /// 主要文案（一定是人话 ✓）
  final String title;

  /// 次要信息（异常类型 / 原始文案差异等 ✓ 可能为空）
  final String? detail;

  /// HTTP 状态码（连不上时为 null ✓）
  final int? statusCode;

  /// 请求路径（不带头部的完整 URL ✓）
  final String? path;

  /// 原始响应体片段（脱敏后 ✓ 用于复制给开发者 ✓）
  final String? rawBody;

  /// 给 UI 的详细信息行（状态码 + 路径）
  String get meta {
    final parts = <String>[];
    if (statusCode != null && statusCode! > 0) parts.add('HTTP $statusCode');
    if (path != null && path!.isNotEmpty) {
      // 只显示路径部分 ✓ 域名不重要且长 ✗
      try {
        final u = Uri.parse(path!);
        parts.add(u.path + (u.query.isEmpty ? '' : '?${u.query}'));
      } catch (_) {
        parts.add(path!);
      }
    }
    return parts.join(' · ');
  }

  /// 「复制详情」用的完整文本 ✓
  String get copyText {
    final b = StringBuffer();
    b.writeln('c001apk 错误详情');
    b.writeln('时间：${DateTime.now().toIso8601String()}');
    b.writeln('标题：$title');
    if (detail != null && detail!.isNotEmpty) b.writeln('说明：$detail');
    if (meta.isNotEmpty) b.writeln('位置：$meta');
    if (rawBody != null && rawBody!.isNotEmpty) {
      b.writeln('响应片段：');
      b.writeln(rawBody);
    }
    return b.toString();
  }

  // ── 构造：从响应 ────────────────────────────────────────

  /// 从 Dio 的响应体 / 状态码提取
  static ApiError fromResponse(Response<dynamic> r) {
    final title = titleFromBody(r.data) ?? _titleForStatus(r.statusCode);
    final path = r.requestOptions.uri.toString();
    return ApiError(
      title: title,
      detail: _detailFromBody(r.data),
      statusCode: r.statusCode,
      path: path,
      rawBody: _snippet(r.data),
    );
  }

  /// 从 Dio 异常提取（按 `DioExceptionType` 分类 ✓）
  static ApiError fromDio(DioException e) {
    final resp = e.response;
    // 有响应 ⇒ 优先用响应里的 message ✓（比「服务器异常」有用得多 ✓）
    if (resp != null) {
      final fromBody = titleFromBody(resp.data);
      final title = fromBody ?? _titleForStatus(resp.statusCode);
      return ApiError(
        title: title,
        // ★ 403 且响应体是**空的** ⇒ 几乎都是「请求被中转节点拦掉」：
        //   实测直连 api.coolapk.com 返回 200，走本地代理（Clash 7890）
        //   则**所有接口**都 403 且 body 为空 ✗
        //   ⇒ 这条提示比「登录已过期」准得多 ✓
        detail: fromBody == null && resp.statusCode == 403
            ? '如果你开了代理 / VPN / 加速器（包括 Clash 的 TUN 模式），请关掉后重试'
            : _detailFromBody(resp.data),
        statusCode: resp.statusCode,
        path: e.requestOptions.uri.toString(),
        rawBody: _snippet(resp.data),
      );
    }

    return ApiError(
      title: _titleForType(e.type),
      detail: e.message,
      path: e.requestOptions.uri.toString(),
    );
  }

  // ── 内部 ────────────────────────────────────────────────

  /// 从响应体里找文案 ✓ 找不到返回 null
  @visibleForTesting
  static String? titleFromBody(dynamic data) {
    if (data == null) return null;

    Map<String, dynamic>? map;
    if (data is Map) {
      map = Map<String, dynamic>.from(data);
    } else if (data is String) {
      final t = data.trim();
      if (t.isEmpty) return null;
      // 尝试当 JSON 解析 ✓（有些接口返回 String 但内容是 JSON ✓）
      if (t.startsWith('{') || t.startsWith('[')) {
        try {
          final j = jsonDecode(t);
          if (j is Map) map = Map<String, dynamic>.from(j);
        } catch (_) {}
      }
      if (map == null) {
        // 纯文本：像 HTML 就说是错误页 ✓ 否则截一段 ✓
        final low = t.toLowerCase();
        if (low.contains('<html') || low.contains('<!doctype')) {
          return '服务器返回了错误页';
        }
        return _clipLine(t);
      }
    }

    if (map == null) return null;

    // ★ 优先级：message → error → msg → error_description → errorMessage
    for (final k in const [
      'message',
      'error',
      'msg',
      'error_description',
      'errorMessage',
      'messageStatus',
    ]) {
      final v = map[k];
      if (v == null) continue;
      // messageStatus 是数字，不当文案 ✗
      if (k == 'messageStatus') continue;
      final s = v is String ? v : v.toString();
      if (s.trim().isNotEmpty && s.toLowerCase() != 'null') {
        return _clipLine(s);
      }
    }

    // 有些接口把内容包在 data 里又给了一个 message ✓（嵌套一层）
    final inner = map['data'];
    if (inner is Map) {
      return titleFromBody(inner);
    }
    return null;
  }

  /// 次要说明：只在「能从响应里看出更多」时才给 ✓
  static String? _detailFromBody(dynamic data) {
    if (data is! Map) return null;
    final code = data['status'] ?? data['error'] ?? data['code'];
    if (code == null) return null;
    return '服务端状态码 $code';
  }

  static String _titleForStatus(int? code) {
    if (code == null || code <= 0) return '网络请求失败';
    if (code == 400) return '请求参数有误';
    if (code == 401 || code == 403) return '没有权限（可能登录已过期）';
    if (code == 404) return '内容不存在（可能已被删除）';
    if (code == 409) return '请求冲突';
    if (code == 429) return '请求太频繁，请稍后再试';
    if (code >= 500 && code < 600) return '服务器出错了（$code）';
    return '请求失败（HTTP $code）';
  }

  static String _titleForType(DioExceptionType t) {
    switch (t) {
      case DioExceptionType.badCertificate:
        return '证书校验失败';
      case DioExceptionType.badResponse:
        return '服务器返回异常';
      case DioExceptionType.cancel:
        return '请求已取消';
      case DioExceptionType.connectionError:
        return '连接失败，请检查网络';
      case DioExceptionType.connectionTimeout:
        return '连接超时，请检查网络';
      case DioExceptionType.receiveTimeout:
        return '响应超时，请稍后重试';
      case DioExceptionType.sendTimeout:
        return '发送超时，请稍后重试';
      case DioExceptionType.unknown:
        return '未知错误';
      // ⚠️ dio 5.11 新增了 `transformTimeout`（还有可能继续加 ✗）
      //    用 `default` 兜底 ✓ 否则升级 dio 就 Analyze 红 ✗
      //    （enum 的 switch 在 Dart 里允许 default ✓）
      default:
        return '网络请求失败';
    }
  }

  /// 响应体片段（给复制用 ✓ 截 500 字 ✓）
  static String? _snippet(dynamic data) {
    if (data == null) return null;
    try {
      final s = data is String ? data : jsonEncode(data);
      final t = s.trim();
      if (t.isEmpty) return null;
      // HTML 不复制进去 ✗ 太长没用 ✗
      if (t.toLowerCase().contains('<html')) return _clipLine(t, 200);
      return t.length <= 500 ? t : '${t.substring(0, 500)}…';
    } catch (_) {
      return null;
    }
  }

  static String _clipLine(String s, [int max = 200]) {
    final one = s.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (one.isEmpty) return '未知错误';
    return one.length <= max ? one : '${one.substring(0, max)}…';
  }
}
