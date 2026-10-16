import 'dart:convert';

import 'package:dio/dio.dart' show DioException;
import 'package:get/get.dart';

import '../model/feed/datum.dart';
import '../model/spam_config/spam_config.dart';
import '../network/network_repo.dart';
import '../../utils/storage_util.dart';

/// 其他屏蔽项（关键字 / 用户 / 节点）的仓库。
///
/// 对应 Android `logic/repository/SpamConfigRepo.kt`。
///
/// 职责有两块：
/// 1. **读写云端配置**（`/v6/user/spamWordList` 读、`/v6/account/updateConfig` 写）
/// 2. **本地过滤**（[isSpam]）—— 列表加载时逐条判断，命中就不显示
///
/// 配置会**缓存在本地**（[GStorage.spamConfig]），冷启动没网也能先过滤，
/// 拿到网络结果后再覆盖。这一条是 Android 端专门做的，别省。
class SpamConfigRepo {
  SpamConfigRepo._();

  /// 当前配置。UI 用 `Obx` 监听它即可自动刷新。
  static final config = const SpamConfig().obs;

  /// 服务端返回的「最多能加多少条」（`spam_word_config_max_count`）
  static final maxCount = 0.obs;

  /// 同步读取当前值（后台判断用，避免 Rx 读取开销）
  static SpamConfig get current => config.value;

  /// 从本地缓存初始化。在 app 启动时调一次。
  static void initFromCache() {
    final raw = GStorage.spamConfig;
    if (raw.isEmpty) return;
    final parsed = _tryParse(raw);
    if (parsed != null) config.value = parsed;
  }

  static SpamConfig? _tryParse(String raw) {
    try {
      final v = jsonDecode(raw);
      if (v is Map) return SpamConfig.fromJson(Map<String, dynamic>.from(v));
    } catch (_) {
      // 缓存坏了就当没有，别让启动挂掉
    }
    return null;
  }

  /// 拉取云端配置并写入缓存。返回是否成功。
  static Future<bool> refresh() async {
    try {
      final res = await NetworkRepo.getSpamWordList();
      final body = res.data;
      if (body is! Map) return false;
      final resp = SpamConfigResponse.fromJson(Map<String, dynamic>.from(body));
      final raw = resp.data?.spamWordConfig;
      if (raw == null || raw.isEmpty) return false;
      final parsed = _tryParse(raw);
      if (parsed == null) return false;
      config.value = parsed;
      GStorage.saveSpamConfig(raw);
      final mc = resp.data?.maxCount;
      if (mc != null) maxCount.value = mc;
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 写配置的公共路径。
  ///
  /// `value` 是一段 JSON 字符串，写完后**必须 refresh** ——
  /// 服务端不回写内容，只回成功与否。
  static Future<bool> _update(String value) async {
    try {
      final res = await NetworkRepo.postUpdateConfig(
        key: 'spam_word_config',
        value: value,
      );
      final body = res.data;
      final ok = body is Map && body['status'] == 0;
      if (ok) await refresh();
      return ok;
    } on DioException {
      return false;
    } catch (_) {
      return false;
    }
  }

  // ── 关键字 ──
  //
  // ⚠️ **写键是 `word`，读回来的键是 `custom`** —— 服务端读写不对称
  // （Android 端在注释里专门记过），照抄别「顺手统一」。

  static Future<bool> addKeyword(String word) =>
      _update('{"word":{"add":${jsonEncode(word)}}}');

  static Future<bool> cancelKeyword(String word) =>
      _update('{"word":{"cancel":${jsonEncode(word)}}}');

  // ── 用户 ──

  static Future<bool> addUser(String uid) =>
      _update('{"user":{"add":${jsonEncode(uid)}}}');

  static Future<bool> cancelUser(String uid) =>
      _update('{"user":{"cancel":${jsonEncode(uid)}}}');

  // ── 节点 ──
  //
  // 节点的值是 `[{tid, name}]`（数组），与关键字/用户的单值不同。

  static Future<bool> addNode(String tid, String name) => _update(
      '{"node":{"add":[{"tid":${jsonEncode(tid)},"name":${jsonEncode(name)}}]}}');

  static Future<bool> cancelNode(String tid, String name) => _update(
      '{"node":{"cancel":[{"tid":${jsonEncode(tid)},"name":${jsonEncode(name)}}]}}');

  /// 命中**关键字 / 用户 / 节点**任一即算屏蔽。
  ///
  /// 对应 Android `SpamConfigRepo.isSpam`。
  static bool isSpam(Datum? data) {
    if (data == null) return false;
    final cfg = current;
    if (cfg.isEmpty) return false;

    // ── 关键字：正文 + 标题 + 卡片标题 ──
    final text =
        '${data.message ?? ''}\n${data.messageTitle ?? ''}\n${data.title ?? ''}';
    for (final k in cfg.custom) {
      final t = k.title;
      if (t != null && t.isNotEmpty && text.contains(t)) return true;
    }

    // ── 用户 ──
    // ⚠️ 配置里的 `uid` 是 String，而 `Datum.userInfo.uid` 是 int?，要转成字符串比
    final uid = data.userInfo?.uid?.toString();
    if (uid != null && uid.isNotEmpty) {
      for (final u in cfg.user) {
        if (u.uid != null && u.uid == uid) return true;
      }
    }

    // ── 节点 ──
    final nodeText = <String>[
      if (data.tags != null) data.tags!,
      if (data.ttitle != null) data.ttitle!,
      if (data.relationRows != null &&
          data.relationRows!.isNotEmpty &&
          data.relationRows!.first.title != null)
        data.relationRows!.first.title!,
      if (data.targetRow?.title != null) data.targetRow!.title!,
    ].join(' ');

    // ⚠️ Flutter 的 `RelationRow` / `TargetRow` **没有 `id` 字段**
    // （只有 logo/title/url/entityType），而节点屏蔽要靠 `targetFullId` 比对 id。
    // 所以从 `Datum.raw` 的嵌套 JSON 里取 —— 这正是保留 raw 的用处之一。
    final nodeIds = <String>[];
    final raw = data.raw;
    if (raw != null) {
      final tr = raw['targetRow'];
      if (tr is Map && tr['id'] != null) nodeIds.add(tr['id'].toString());
      final rr = raw['relationRows'];
      if (rr is List && rr.isNotEmpty && rr.first is Map) {
        final id = (rr.first as Map)['id'];
        if (id != null) nodeIds.add(id.toString());
      }
    }

    for (final n in cfg.node) {
      final t = n.title;
      if (t != null && t.isNotEmpty && nodeText.contains(t)) return true;
      final fid = n.targetFullId;
      if (fid != null && fid.isNotEmpty && nodeIds.contains(fid)) return true;
    }

    return false;
  }
}
