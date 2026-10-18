import 'package:flutter/material.dart';

import '../utils/verify_badge.dart';

/// 认证角标（头像右下角那枚小圆标）—— 对齐 Android `VerifyBadge.applyTo`。
///
/// 只有 `verify_status == 1` 或在云端认证表里才显示 ✓ 否则**整个不渲染** ✓
class VerifyDot extends StatelessWidget {
  const VerifyDot({
    super.key,
    required this.uid,
    required this.status,
    required this.icon,
    this.size = 14,
  });

  final dynamic uid;
  final int? status;
  final String? icon;
  final double size;

  @override
  Widget build(BuildContext context) {
    final uidStr = uid?.toString();
    if (!VerifyBadge.isVerified(uidStr, status)) {
      return const SizedBox.shrink();
    }
    final color = VerifyBadge.colorFor(uidStr, icon);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        // 描边跟表面同色，避免角标和头像糊在一起 ✓
        border: Border.all(
          width: 2,
          color: Theme.of(context).colorScheme.surface,
        ),
      ),
      child: Icon(Icons.check, size: size * 0.6, color: Colors.white),
    );
  }
}

/// 「头像 + 右下角认证角标」。
///
/// 用法：把头像 widget 当 `child` 传进来即可 ✓
/// （不上 Avatar 自己画图，是为了不侵入 `clipNetworkImage` 那些现有调用点 ✗）
class VerifyAvatar extends StatelessWidget {
  const VerifyAvatar({
    super.key,
    required this.child,
    required this.uid,
    required this.status,
    required this.icon,
    this.dotSize = 14,
    this.offset = 0,
  });

  final Widget child;
  final dynamic uid;
  final int? status;
  final String? icon;
  final double dotSize;

  /// 角标往外挪多少（头像有外描边时用 ✓）
  final double offset;

  @override
  Widget build(BuildContext context) {
    final uidStr = uid?.toString();
    if (!VerifyBadge.isVerified(uidStr, status)) return child;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        child,
        Positioned(
          right: -offset,
          bottom: -offset,
          child: VerifyDot(
            uid: uid,
            status: status,
            icon: icon,
            size: dotSize,
          ),
        ),
      ],
    );
  }
}

/// 个人主页的认证行 —— 角标 + 文字（对齐 Android `setVerifyTitle`）。
///
/// ★ 认证**文字**只在个人主页出现 ✓ 列表 / 详情页只挂头像角标 ✓（照 Android）
/// 云端把 title 配成空串时，这里同样**整行不显示** ✓（等于只留头像角标 ✓）
class VerifyTitleRow extends StatelessWidget {
  const VerifyTitleRow({
    super.key,
    required this.uid,
    required this.rawTitle,
    required this.icon,
    required this.status,
  });

  final dynamic uid;
  final String? rawTitle;
  final String? icon;
  final int? status;

  @override
  Widget build(BuildContext context) {
    final uidStr = uid?.toString();
    final title = VerifyBadge.title(uidStr, rawTitle);
    if (title == null || title.isEmpty) return const SizedBox.shrink();
    final color = VerifyBadge.colorFor(uidStr, icon);
    return Padding(
      padding: const EdgeInsets.only(left: 20, top: 4),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 15,
            height: 15,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            child: const Icon(Icons.check, size: 10, color: Colors.white),
          ),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 12, color: color),
            ),
          ),
        ],
      ),
    );
  }
}
