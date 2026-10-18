import 'dart:convert';

import 'package:flutter/material.dart';

/// 用户认证标 —— 对齐 Android `util/VerifyBadge.kt`（逻辑一一对应 ✓）。
///
/// ## 服务端下发的三个字段（列表和详情都带）
/// ```
/// verify_status   1 = 有认证
/// verify_icon     客户端内置图标名，样本里只有 v_green / v_yellow
/// verify_title    完整认证名，如「酷安认证: 酷安员工」「酷安认证: 优质内容创作者」
///                 —— 只在个人主页展示，列表 / 详情页只挂头像角标
/// ```
/// 认证类别（企业 / 官方 / 个人）**没有**独立的结构化字段 ✗：
/// `user_type` 恒为 0、`verify_label` 恒为空、`admintype` 只覆盖「酷安员工 / 官方账号」✓
/// ⇒ 颜色只能跟着 `verify_icon` 走，文字直接用 `verify_title` 原文 ✓
///
/// ## 在此之上叠一层「云端认证表」
/// 本应用自己的认证：`service.houlangs.cn/c001apk/userverify/` 一份 JSON ✓
/// ```json
/// {"code":0,"list":[{"uid":"32864090","title":"…","color":"#FF9CA8","icon":"v_green"}]}
/// ```
/// 命中的人以云端配置为准 ✓（文字用云端的、颜色用云端的 ✓
/// 没配 `color` 才回落到 `verify_icon` 的绿 / 黄 ✓）
///
/// 表由 [RemoteConfig] 在启动时刷新一次并落盘缓存 ✓
/// ⇒ 离线启动、接口挂了都不影响已有认证的展示 ✓
class VerifyBadge {
  VerifyBadge._();

  /// 与 Android `colors.xml` 的 `verify_badge_green` / `verify_badge_yellow` 一致 ✓
  static const Color green = Color(0xFF4CAF50);
  static const Color yellow = Color(0xFFFFB300);

  /// 云端下发的单条认证配置
  static Map<String, _Entry> _remote = const {};

  /// 云端表是否已加载（UI 想按需刷新时用 ✓）
  static bool get hasRemote => _remote.isNotEmpty;

  /// 用服务端下发的 JSON 重建整张表。
  ///
  /// 解析不出来（空串 / 坏 JSON / 一条都没有）就**保持原样** ✓
  /// —— 宁可继续用旧缓存，也别因为一次响应异常让所有人的认证标集体消失 ✗
  static void load(String? json) {
    if (json == null || json.trim().isEmpty) return;
    dynamic decoded;
    try {
      decoded = jsonDecode(json);
    } catch (_) {
      return;
    }
    if (decoded is! Map) return;
    final list = decoded['list'];
    if (list is! List) return;

    final next = <String, _Entry>{};
    for (final item in list) {
      if (item is! Map) continue;
      final uid = (item['uid']?.toString() ?? '').trim();
      if (uid.isEmpty) continue;
      next[uid] = _Entry(
        title: (item['title']?.toString() ?? '').trim(),
        color: _parseColor(item['color']?.toString()),
        icon: (item['icon']?.toString() ?? '').trim(),
      );
    }
    if (next.isEmpty) return;
    _remote = next;
  }

  /// `#RRGGBB` / `#AARRGGBB`（服务端已校验过格式，这里只是兜一道 ✓）
  static Color? _parseColor(String? value) {
    final text = (value ?? '').trim();
    if (text.isEmpty) return null;
    var hex = text.startsWith('#') ? text.substring(1) : text;
    if (hex.length == 6) hex = 'FF$hex';
    if (hex.length != 8) return null;
    final v = int.tryParse(hex, radix: 16);
    return v == null ? null : Color(v);
  }

  /// 有没有认证：在云端表里，或服务端自己标了 `verify_status == 1` ✓
  static bool isVerified(String? uid, int? status) =>
      (uid != null && _remote.containsKey(uid)) || status == 1;

  /// 角标底色：云端 `color` 优先 ✓ 其次按 icon 分绿 / 黄 ✓
  static Color colorFor(String? uid, String? icon) {
    final entry = uid == null ? null : _remote[uid];
    if (entry?.color != null) return entry!.color!;
    final key = (entry?.icon?.isNotEmpty ?? false) ? entry!.icon : icon;
    return key == 'v_yellow' ? yellow : green;
  }

  /// 认证文字（个人主页那一行）。
  ///
  /// 云端配了就用云端的 ✓（配成空串等于**只出头像角标**、不显示这一行 ✓）
  /// 否则原样展示服务端下发的 ✓
  static String? title(String? uid, String? raw) {
    final entry = uid == null ? null : _remote[uid];
    if (entry != null) {
      return entry.title.isNotEmpty ? entry.title : null;
    }
    return (raw != null && raw.trim().isNotEmpty) ? raw : null;
  }
}

class _Entry {
  const _Entry({required this.title, this.color, required this.icon});

  final String title;
  final Color? color;
  final String icon;
}
