import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../constants/m3_tokens.dart';
import 'storage_util.dart';

/// 转场类型（对齐 Android `TransitionAnim` 的 `TYPE_*`）。
///
/// Android 还定义了 `parallax`（新页滑入 + 旧页左移半屏、缩到 0.7 并淡出），
/// 但**设置数组里没暴露它**（`anim_type_texts` 只有 3 项），所以这里也不给。
enum M3TransitionType { slide, fade, none }

/// 转场配置：把「设置 - 外观 - 转场动画」的三项翻译成曲线 / 时长。
///
/// 对应 Android `util/TransitionAnim.kt`。
///
/// ## 曲线来源（`TransitionAnim.kt` 的常量注释）
/// | 选项 | 进入 | 退出 |
/// |---|---|---|
/// | `linear` 线性（匀速）· 改造前 | `Cubic(0,0,1,1)` | 同 |
/// | `m2` M2 标准 `(0.4,0,0.2,1)` | `M3Easing.legacy` | **同曲线**（进出一样） |
/// | `std` M3 标准 | standard-decelerate `(0,0,0,1)` | standard-accelerate `(0.3,0,1,1)` |
/// | `emph` M3 强调 | emphasized-decelerate `(0.1,0.7,0.1,1)` | emphasized-accelerate `(0.3,0,0.8,0.2)` |
/// | `spring` 弹簧近似 | ease-out-cubic `(0.33,1,0.68,1)` | 反向 ease-in-cubic `(0.68,0,0.32,0)` |
///
/// ## ★ 默认值为什么是 std / slide / 400
/// 台账里记的「用户拍板 std + slide + 400ms，**权威**」（2026-10-06 真机实测满意）。
/// Android `settings.xml` 里声明的是 `emph / slide / 300`，但那是「选项默认值」，
/// 用户实测后拍板的是 std / 400 —— 这里取后者，保持现有观感不变，
/// 下拉框里仍可切到 emph / 300。
class M3MotionConfig {
  const M3MotionConfig._();

  /// 弹簧曲线的近似（Android 注释：rikkahub 那边其实没有曲线，
  /// 是 Compose 的 `spring(dampingRatio=1, stiffness=400)`，没法用一条贝塞尔等价表达；
  /// 取形状最接近的 ease-out-cubic，退出取反向 ease-in-cubic）
  static const Curve _springIn = Cubic(0.33, 1.0, 0.68, 1.0);
  static const Curve _springOut = Cubic(0.68, 0.0, 0.32, 0.0);

  static M3TransitionType get type => switch (GStorage.animType) {
        1 => M3TransitionType.fade,
        2 => M3TransitionType.none,
        _ => M3TransitionType.slide,
      };

  static Curve get enterCurve => switch (GStorage.animCurve) {
        0 => M3Easing.linear,
        1 => M3Easing.legacy,
        3 => M3Easing.emphasizedDecelerate,
        4 => _springIn,
        _ => M3Easing.standardDecelerate,
      };

  static Curve get exitCurve => switch (GStorage.animCurve) {
        0 => M3Easing.linear,
        1 => M3Easing.legacy, // M2 进出同曲线
        3 => M3Easing.emphasizedAccelerate,
        4 => _springOut,
        _ => M3Easing.standardAccelerate,
      };

  /// 进入时长（用户可在设置里选 100~500ms）
  static Duration get enterDuration =>
      Duration(milliseconds: GStorage.animDuration);

  /// 退出时长（既有规则：进入 - 50ms）
  ///
  /// ⚠️ **目前没有生效** —— GetX 的 `GetPage` 只有 `transitionDuration`，
  /// **没有** `reverseTransitionDuration`（已核对 get 4.7.2 的 `get_route.dart`），
  /// 所以退出实际与进入同长。要精确到「进入 - 50ms」得绕开 GetX 自己写
  /// `PageRouteBuilder`（它支持两段不同时长）。这个值先留着，等真要抠这一帧时再用。
  static Duration get exitDuration => Duration(
      milliseconds: (GStorage.animDuration - 50).clamp(50, 500));
}

/// M3 转场 —— 曲线 / 类型 / 时长都由 [M3MotionConfig] 决定。
///
/// ## 四个槽位怎么被覆盖
/// Android 端是四个独立动画资源；Flutter 侧用**同一个** [CustomTransition] 覆盖全部四个，
/// 因为一条 route 同时持有两个动画：
/// ```
/// animation          : 本页自身的进出（push 0→1 / pop 1→0）
/// secondaryAnimation : 本页被**新页盖住**的程度（0→1 被盖 / 1→0 回场）
/// ```
/// 于是：
/// | Android 槽位 | 含义 | 这里由谁产生 |
/// |---|---|---|
/// | `rin`  | 新页进入 100% → 0      | `animation` 0→1 上的正向位移 |
/// | `rout` | 新页退出 0 → 100%      | `animation` 1→0（pop 时反向播） |
/// | `lout` | 旧页退场 0 → -100%     | `secondaryAnimation` 0→1 |
/// | `lin`  | 旧页回场 -100% → 0     | `secondaryAnimation` 1→0 |
///
/// ## ★ 为什么忽略传进来的 `curve`
/// GetX 会把 `GetPage.curve` 传进来，但那条曲线是**所有转场共用一条**，
/// 而 M3 的进出本来就该用**两条不同的曲线**（decelerate / accelerate）。
/// 所以这里**不看** `curve` 参数，统一取 [M3MotionConfig] 里的进出两条。
class M3SlideTransition extends CustomTransition {
  /// 注意**不能是 const**：[CustomTransition] 的构造函数不是 const，
  /// 子类用 `const` 会报
  /// `A constant constructor can't call a non-constant super constructor`。
  M3SlideTransition();

  @override
  Widget buildTransition(
    BuildContext context,
    Curve? curve,
    Alignment? alignment,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    // 「无动画」：直接给原样
    if (M3MotionConfig.type == M3TransitionType.none) return child;

    final enterCurve = M3MotionConfig.enterCurve;
    final exitCurve = M3MotionConfig.exitCurve;

    // 「淡入淡出」：只动本页透明度，旧页不动
    if (M3MotionConfig.type == M3TransitionType.fade) {
      final enterOpacity = Tween<double>(begin: 0, end: 1)
          .chain(CurveTween(curve: enterCurve))
          .animate(animation);
      return FadeTransition(opacity: enterOpacity, child: child);
    }

    // 「水平滑动」
    // rin / rout：本页自身
    final enter = Tween<Offset>(
      begin: const Offset(1, 0),
      end: Offset.zero,
    ).chain(CurveTween(curve: enterCurve)).animate(animation);

    // lout / lin：本页被新页盖住（或新页退出时回场）
    final exit = Tween<Offset>(
      begin: Offset.zero,
      end: const Offset(-1, 0),
    ).chain(CurveTween(curve: exitCurve)).animate(secondaryAnimation);

    return SlideTransition(
      position: exit,
      child: SlideTransition(position: enter, child: child),
    );
  }
}
