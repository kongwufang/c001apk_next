import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../constants/m3_tokens.dart';

/// M3 水平滑动转场 —— 用户 2026-10-06 真机实测拍板的配置。
///
/// 规格（见 `_rev`…/`.codebuddy/handover-memory/project_transition_anim_experiment.md`）：
/// ```
/// 曲线 = M3 standard（= Cubic(0.2, 0, 0, 1)）
/// 类型 = 水平滑动（slide）
/// 进入 = 400ms      退出 = 350ms（既有规则：进入 - 50ms）
/// ```
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
/// ## 已知与 Android 的差异
/// GetX 的 `GetPage` **只有** `transitionDuration`，**没有** `reverseTransitionDuration`
/// （已核对 get 4.7.2 的 `get_route.dart`）。所以退出实际也是 400ms，
/// 比规格的 350ms 多 50ms。要精确到 350ms 得绕开 GetX 用自己的 `PageRouteBuilder`
/// （它的 `reverseTransitionDuration` 支持两段不同时长）—— 待用户反馈是否在意。
class M3SlideTransition extends CustomTransition {
  const M3SlideTransition();

  @override
  Widget buildTransition(
    BuildContext context,
    Curve? curve,
    Alignment? alignment,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    final enterCurve = curve ?? M3Transition.enterCurve;
    final exitCurve = curve ?? M3Transition.exitCurve;

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
