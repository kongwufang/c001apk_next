import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import 'storage_util.dart';

/// HTTP 请求日志（排查用）。
///
/// ## 开关
/// `设置 → 高级 → 记录 HTTP 请求日志`（默认**关闭** ✓）
/// 对应 `GStorage.recordHttpLog` ✓
/// 关闭时 [`record`] 只做一次 bool 判断就返回 ✓ **零开销** ✓
///
/// ## 落盘
/// `<应用支持目录>/http_logs/YYYYMMDD.log` ✓ 一行一条 JSON ✓
/// - 单文件超 [maxFileBytes]（2 MB）⇒ 换下一个序号文件 ✓
/// - 超过 [keepDays]（7 天）的日志自动删掉 ✓
///
/// ## 脱敏
/// `token` / `Cookie` / `authorization` / `password` 一类字段**全打码** ✓
/// 但**保留长度** ✓（排查时能看出「有没有传 / 传了多长」✓ 又拿不到明文 ✓）
///
/// ## 格式
/// ```json
/// {"ts":1792293918,"method":"GET","url":"…","status":200,"ms":312,
///  "req":"…","resp":"…","err":""}
/// ```
/// - `req` / `resp` 只记**前 [maxBodyChars] 字符**（2 KB ✓）
/// - `err` 记录异常类型 + message ✓（正常请求为空串 ✓）
class HttpLog {
  HttpLog._();

  /// 单文件上限：2 MB ⇒ 超了换文件
  static const int maxFileBytes = 2 * 1024 * 1024;

  /// 只保留最近 7 天
  static const int keepDays = 7;

  /// 请求体 / 响应体各记前 2 KB
  static const int maxBodyChars = 2048;

  /// 需要脱敏的键（小写匹配 ✓ 子串匹配更广 ✓）
  static const List<String> _sensitiveKeys = [
    'token',
    'cookie',
    'authorization',
    'password',
    'passwd',
    'secret',
    'session',
    'ssaid',
    'uid',
    'username',
    'sig',
    'signature',
    'x-app-token',
    'x-client-ssaid',
    'x-app-userrandomid',
  ];

  static Directory? _dirCache;

  /// 日志目录（首次访问时创建 ✓）
  static Future<Directory> _dir() async {
    final cached = _dirCache;
    if (cached != null) return cached;
    final base = await getApplicationSupportDirectory();
    final d = Directory('${base.path}${Platform.pathSeparator}http_logs');
    if (!await d.exists()) {
      await d.create(recursive: true);
    }
    _dirCache = d;
    return d;
  }

  /// 记一条（开关关着直接返回 ✓）
  ///
  /// 全部异常都吞掉 ✓ —— 日志功能**绝不能**影响正常请求 ✓
  static Future<void> record({
    required String method,
    required String url,
    int? status,
    required Duration elapsed,
    String? reqBody,
    String? respBody,
    String? error,
  }) async {
    if (!GStorage.recordHttpLog) return;
    try {
      final ts = DateTime.now();
      final row = <String, dynamic>{
        'ts': ts.millisecondsSinceEpoch ~/ 1000,
        'time': ts.toIso8601String(),
        'method': method,
        'url': maskText(url),
        'status': status ?? 0,
        'ms': elapsed.inMilliseconds,
        'req': _clip(maskText(reqBody ?? '')),
        'resp': _clip(maskText(respBody ?? '')),
        'err': _clip(error ?? ''),
      };
      final line = jsonEncode(row);

      final dir = await _dir();
      final file = await _pickFile(dir, ts);
      await file.writeAsString('$line\n', mode: FileMode.append, flush: false);

      // 顺手清理过期文件（每次都做太浪费 ⇒ 1/20 概率 ✓）
      if (DateTime.now().microsecond % 20 == 0) {
        unawaited(_prune(dir));
      }
    } catch (e) {
      debugPrint('HttpLog.record 失败: $e'); // 不影响主流程 ✓
    }
  }

  /// 取当天文件；超过 2 MB 就换 `<YYYYMMDD>-2.log`、`-3` …
  static Future<File> _pickFile(Directory dir, DateTime ts) async {
    final ymd = '${ts.year.toString().padLeft(4, '0')}'
        '${ts.month.toString().padLeft(2, '0')}'
        '${ts.day.toString().padLeft(2, '0')}';
    var i = 1;
    while (true) {
      final name = i == 1 ? '$ymd.log' : '$ymd-$i.log';
      final f = File('${dir.path}${Platform.pathSeparator}$name');
      if (!await f.exists()) return f;
      final len = await f.length();
      if (len < maxFileBytes) return f;
      i++;
      if (i > 99) return f; // 兜底，别无限找 ✓
    }
  }

  /// 删除超过 [keepDays] 天的日志文件
  static Future<void> _prune(Directory dir) async {
    try {
      final cutoff = DateTime.now().subtract(const Duration(days: keepDays));
      await for (final e in dir.list()) {
        if (e is! File) continue;
        final st = await e.stat();
        if (st.modified.isBefore(cutoff)) {
          await e.delete();
        }
      }
    } catch (_) {
      // 清理失败无所谓 ✓
    }
  }

  /// 当前所有日志的总字节数与文件数（设置页显示用 ✓）
  static Future<(int bytes, int files, int lines)> stats() async {
    try {
      final dir = await _dir();
      var bytes = 0;
      var files = 0;
      var lines = 0;
      await for (final e in dir.list()) {
        if (e is! File) continue;
        files++;
        final st = await e.stat();
        bytes += st.size;
        try {
          final content = await e.readAsString();
          lines += '\n'.allMatches(content).length;
        } catch (_) {}
      }
      return (bytes, files, lines);
    } catch (_) {
      return (0, 0, 0);
    }
  }

  /// 导出：把全部日志拼成一份文本（按时间正序 ✓）
  ///
  /// 返回 `(文件名, 内容)` ✓ 由调用方决定存哪（桌面端文件对话框 / 安卓 SAF ✓）
  static Future<(String, String)> exportAll() async {
    final dir = await _dir();
    final files = <File>[];
    await for (final e in dir.list()) {
      if (e is File) files.add(e);
    }
    files.sort((a, b) => a.path.compareTo(b.path)); // 文件名里带日期 ⇒ 天然正序 ✓

    final buf = StringBuffer();
    buf.writeln('# c001apk HTTP 请求日志');
    buf.writeln('# 导出时间：${DateTime.now().toIso8601String()}');
    buf.writeln('# 说明：敏感字段已脱敏（长度保留）；响应体只记前 $maxBodyChars 字符');
    buf.writeln('# 每行一条 JSON');
    buf.writeln();
    for (final f in files) {
      try {
        buf.write(await f.readAsString());
      } catch (_) {}
    }

    final now = DateTime.now();
    final name = 'c001apk-http-log-'
        '${now.year}${now.month.toString().padLeft(2, '0')}'
        '${now.day.toString().padLeft(2, '0')}-'
        '${now.hour.toString().padLeft(2, '0')}'
        '${now.minute.toString().padLeft(2, '0')}.txt';
    return (name, buf.toString());
  }

  /// 清空全部日志
  static Future<int> clear() async {
    var n = 0;
    try {
      final dir = await _dir();
      await for (final e in dir.list()) {
        if (e is File) {
          await e.delete();
          n++;
        }
      }
    } catch (_) {}
    return n;
  }

  // ── 内部工具 ──────────────────────────────────────────

  static String _clip(String s) =>
      s.length <= maxBodyChars ? s : '${s.substring(0, maxBodyChars)}…(截断)';

  /// 把敏感值打码：`"token":"abc123"` → `"token":"*** (len=6)"`
  ///
  /// 用**子串匹配**（不区分大小写）✓ 宁可多打一点 ✓
  @visibleForTesting
  static String maskText(String s) {
    if (s.isEmpty) return s;
    var out = s;
    for (final key in _sensitiveKeys) {
      // JSON 形式： "key":"value"  或  "key": value
      out = out.replaceAllMapped(
        RegExp('("$key"\\s*:\\s*")([^"]*)(")', caseSensitive: false),
        (m) => '${m[1]}*** (len=${m[2]!.length})${m[3]}',
      );
      // Cookie 形式： token=value; / token=value&
      out = out.replaceAllMapped(
        RegExp('($key=)([^;&\\s"]+)', caseSensitive: false),
        (m) => '${m[1]}*** (len=${m[2]!.length})',
      );
    }
    return out;
  }
}
