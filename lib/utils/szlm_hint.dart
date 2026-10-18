import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

/// 「可能是数字联盟 ID（szlmId）失效」的识别与提示。
///
/// ## 为什么需要它
/// 2026-10-18 实测（30 个业务接口 × 旧号 / 随机号）：
/// 设备标识失效时服务器**仍回 HTTP 200** ✗，错误藏在 JSON 的 `data` 字符串里 ✓
///
/// | 坏 szlmId | 受影响接口 | 服务器文案 |
/// |---|---|---|
/// | 换号前的旧值（过期）| 动态详情 / 动态回复 / 楼中楼 | `评论接口异常` |
/// | 同上 | 动态详情 | `检测到当前版本存在异常，可能对您的账号信息安全带来风险。暂时无法访问该内容，请使用官方APP访问(-419)` |
/// | 随机值 | 动态详情 / 动态回复 / 楼中楼 | `当前访问需要验证码` |
///
/// 其余 27 个接口（首页 / 用户 / 通知 / 私信 / 收藏 / 搜索 / 应用 …）**不受影响** ✓
///
/// ⇒ 用户看到「评论接口异常」这种文案根本不知道怎么处理 ✗
/// ⇒ 这里识别出来，直接告诉用户「去重新取一次号」✓ 并给按钮跳转 ✓
class SzlmHint {
  SzlmHint._();

  /// 命中这些子串 ⇒ 判定为设备标识失效 ✓
  ///
  /// 加新特征时**只加子串** ✓（服务器文案可能带前后缀 / 括号说明）
  static const List<String> markers = [
    '评论接口异常',
    '当前访问需要验证码',
    '检测到当前版本存在异常',
    '(-419)',
  ];

  /// 上一次弹窗时间 —— 一次失效会连续触发很多请求 ✗ 不能每个都弹 ✗
  static DateTime? _lastShownAt;
  static const Duration _cooldown = Duration(seconds: 60);

  /// 这条错误文案是不是「szlmId 失效」类 ✓
  static bool matches(String? message) {
    if (message == null || message.isEmpty) return false;
    for (final m in markers) {
      if (message.contains(m)) return true;
    }
    return false;
  }

  /// 如果命中就弹一次提示（60 秒内只弹一次 ✓）
  ///
  /// ⚠️ 调用方在**请求层**（`network_repo.dart`）—— 那里拿不到 BuildContext ✗
  /// 所以用 `Get.dialog`（GetX 的全局导航器 ✓ 不需要 context ✓）
  static void maybeNotify(String? message) {
    if (!matches(message)) return;
    final now = DateTime.now();
    if (_lastShownAt != null && now.difference(_lastShownAt!) < _cooldown) {
      return;
    }
    _lastShownAt = now;

    // 用 Get.dialog 而不是 SmartDialog.showToast：
    // toast 挂不了按钮 ✗ 而这里必须给「去取号」的入口 ✓
    Get.dialog(
      AlertDialog(
        title: const Text('可能数字联盟 ID 已失效'),
        content: const SingleChildScrollView(
          child: Text(
            '服务器拒绝了这次请求：\n\n'
            '「评论接口异常」/「当前访问需要验证码」/「版本存在异常」\n\n'
            '这些都是**设备标识（数字联盟 ID / DUID）失效**时的固定提示 —— '
            '它不一定是账号问题，重新取一次号通常就能恢复。\n\n'
            '去「设置 → 高级 → 获取数字联盟 ID」点一次，按提示完成人机验证即可（App 端同样如此）。',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Get.back(),
            child: const Text('知道了'),
          ),
          FilledButton(
            onPressed: () {
              Get.back(); // 先关弹窗，再跳页面 ✓
              Get.toNamed('/szlmId');
            },
            child: const Text('去重新取号'),
          ),
        ],
      ),
      barrierDismissible: true,
    );
    SmartDialog.showToast('检测到设备标识可能失效，建议重新取号');
  }

  /// 测试 / 单元用：清掉节流
  @visibleForTesting
  static void resetCooldown() => _lastShownAt = null;
}
